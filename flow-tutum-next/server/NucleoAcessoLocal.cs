using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;

namespace FlowTutum.Next.Security;

/// <summary>
/// Regra da rede local. Nenhum IP externo ou outra interface recebe acesso direto.
/// A rede é configurada pelo executável da conta AS, sem tarefa agendada.
/// </summary>
public sealed class PoliticaRedeLocal
{
    private readonly IPAddress _servidor;
    private readonly IPAddress _mascara;

    public PoliticaRedeLocal(IPAddress servidor, IPAddress mascara)
    {
        _servidor = IpV4(servidor) ?? throw new ArgumentException("Servidor precisa ser IPv4.");
        _mascara = IpV4(mascara) ?? throw new ArgumentException("Máscara precisa ser IPv4.");
        if (!Privado(_servidor) || !MascaraContigua(_mascara))
            throw new ArgumentException("Rede local privada e máscara válidas são obrigatórias.");
    }

    public bool EhPermitido(IPAddress? ipOrigem, IPAddress? ipDestino)
    {
        var remoto = IpV4(ipOrigem);
        var destino = IpV4(ipDestino);
        if (remoto is null || destino is null || !destino.Equals(_servidor) || !Privado(remoto))
            return false;

        var ipBytes = remoto.GetAddressBytes();
        var localBytes = _servidor.GetAddressBytes();
        var mascaraBytes = _mascara.GetAddressBytes();
        for (var i = 0; i < 4; i++)
            if ((ipBytes[i] & mascaraBytes[i]) != (localBytes[i] & mascaraBytes[i]))
                return false;

        var hostBits = 0;
        var hostBroadcast = 0;
        for (var i = 0; i < 4; i++)
        {
            hostBits |= (ipBytes[i] & ~mascaraBytes[i]);
            hostBroadcast |= (~ipBytes[i] & ~mascaraBytes[i] & 0xFF);
        }
        return hostBits != 0 && hostBroadcast != 0;
    }

    public static bool OrigemPermitida(string? origem, string esquema, string host)
    {
        // Browsers enviam Origin. Requisições nativas sem Origin não recebem token
        // sem a verificação de IP e interface feita acima.
        if (string.IsNullOrEmpty(origem)) return true;
        if (!Uri.TryCreate(origem, UriKind.Absolute, out var uri)) return false;
        if (uri.UserInfo.Length > 0 || uri.AbsolutePath != "/" || uri.Query.Length > 0 ||
            uri.Fragment.Length > 0) return false;
        if (!string.Equals(uri.Scheme, esquema, StringComparison.OrdinalIgnoreCase))
            return false;
        return string.Equals(uri.Authority, host, StringComparison.OrdinalIgnoreCase);
    }

    private static IPAddress? IpV4(IPAddress? endereco) =>
        endereco?.AddressFamily == AddressFamily.InterNetwork ? endereco :
        endereco?.IsIPv4MappedToIPv6 == true ? endereco.MapToIPv4() : null;

    private static bool Privado(IPAddress ip)
    {
        var b = ip.GetAddressBytes();
        return b[0] == 10 ||
               (b[0] == 172 && b[1] is >= 16 and <= 31) ||
               (b[0] == 192 && b[1] == 168);
    }

    private static bool MascaraContigua(IPAddress mascara)
    {
        var bytes = mascara.GetAddressBytes();
        var bits = bytes.Aggregate(0u, (acc, b) => (acc << 8) | b);
        if (bits == 0 || bits == uint.MaxValue) return false;
        var invertida = ~bits;
        return (invertida & (invertida + 1)) == 0;
    }
}

/// <summary>
/// Tokens aleatórios, expiração e vínculo ao endereço IP. Armazena apenas hash.
/// Acesso desativado revoga imediatamente todos os tokens da LAN.
/// </summary>
public sealed class SessoesRedeLocal
{
    private sealed record Credencial(string EnderecoIp, DateTimeOffset ExpiraEm);
    private readonly object _sincronizacao = new();
    private readonly Dictionary<string, Credencial> _sessoes = new(StringComparer.Ordinal);
    private readonly TimeSpan _duracao;
    private bool _ativo;

    public SessoesRedeLocal(TimeSpan? duracao = null, bool ativo = false)
    {
        _duracao = duracao ?? TimeSpan.FromMinutes(30);
        if (_duracao <= TimeSpan.Zero || _duracao > TimeSpan.FromHours(1))
            throw new ArgumentOutOfRangeException(nameof(duracao));
        _ativo = ativo;
    }

    public bool Ativo { get { lock (_sincronizacao) return _ativo; } }
    public int QuantidadeDeSessoes { get { lock (_sincronizacao) return _sessoes.Count; } }

    public void DefinirAtivo(bool ativo)
    {
        lock (_sincronizacao)
        {
            _ativo = ativo;
            if (!ativo) _sessoes.Clear();
        }
    }

    public string? Emitir(IPAddress endereco, DateTimeOffset agora)
    {
        // A camada de sessão também precisa recusar endereços não locais:
        // um chamador futuro pode ignorar a política HTTP por engano.
        if (!EnderecoPrivadoValido(endereco)) return null;
        lock (_sincronizacao)
        {
            if (!_ativo) return null;
            LimparExpiradas(agora);
            if (_sessoes.Count >= 32) return null;
            var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
            _sessoes[Hash(token)] = new Credencial(Normalizar(endereco), agora + _duracao);
            return token;
        }
    }

    public bool Verificar(string? token, IPAddress? endereco, DateTimeOffset agora)
    {
        if (token is null || endereco is null || !EnderecoPrivadoValido(endereco) ||
            token.Length != 64 || !token.All(Uri.IsHexDigit)) return false;
        lock (_sincronizacao)
        {
            if (!_ativo) return false;
            var hash = Hash(token);
            if (!_sessoes.TryGetValue(hash, out var credencial)) return false;
            if (credencial.ExpiraEm <= agora)
            {
                _sessoes.Remove(hash);
                return false;
            }
            return credencial.EnderecoIp == Normalizar(endereco);
        }
    }

    /// <summary>Revoga somente o token do dispositivo desconectado, sem derrubar outros clientes.</summary>
    public bool Revogar(string? token)
    {
        if (token is null || token.Length != 64 || !token.All(Uri.IsHexDigit)) return false;
        lock (_sincronizacao)
        {
            return _sessoes.Remove(Hash(token));
        }
    }

    public void RevogarTudo() { lock (_sincronizacao) _sessoes.Clear(); }

    private void LimparExpiradas(DateTimeOffset agora)
    {
        foreach (var chave in _sessoes.Where(p => p.Value.ExpiraEm <= agora)
                     .Select(p => p.Key).ToArray()) _sessoes.Remove(chave);
    }

    private static bool EnderecoPrivadoValido(IPAddress endereco)
    {
        var ipv4 = endereco.IsIPv4MappedToIPv6 ? endereco.MapToIPv4() : endereco;
        if (ipv4.AddressFamily != AddressFamily.InterNetwork) return false;
        var bytes = ipv4.GetAddressBytes();
        return bytes[0] == 10 ||
               (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) ||
               (bytes[0] == 192 && bytes[1] == 168);
    }

    private static string Normalizar(IPAddress endereco) =>
        (endereco.IsIPv4MappedToIPv6 ? endereco.MapToIPv4() : endereco).ToString();

    private static string Hash(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.ASCII.GetBytes(token)));
}

/// <summary>Limita o número de solicitações de acesso sem depender de relógios ou serviços externos.</summary>
public sealed class LimitadorDeConexoes
{
    private readonly object _sincronizacao = new();
    private readonly Dictionary<string, (int Tentativas, DateTimeOffset Inicio)> _dados = new();
    private readonly int _maximo;
    private readonly TimeSpan _janela;

    public LimitadorDeConexoes(int maximo = 10, TimeSpan? janela = null)
    {
        if (maximo is < 1 or > 100) throw new ArgumentOutOfRangeException(nameof(maximo));
        _maximo = maximo;
        _janela = janela ?? TimeSpan.FromMinutes(1);
        if (_janela <= TimeSpan.Zero) throw new ArgumentOutOfRangeException(nameof(janela));
    }

    public bool Permitir(IPAddress ip, DateTimeOffset agora)
    {
        var chave = (ip.IsIPv4MappedToIPv6 ? ip.MapToIPv4() : ip).ToString();
        lock (_sincronizacao)
        {
            if (_dados.Count >= 256)
                foreach (var antigo in _dados.Where(p => agora < p.Value.Inicio || agora - p.Value.Inicio >= _janela)
                         .Select(p => p.Key).ToArray()) _dados.Remove(antigo);
            // Limite rígido de memória: endereço desconhecido não aloca entradas ilimitadas.
            if (_dados.Count >= 256 && !_dados.ContainsKey(chave)) return false;
            if (!_dados.TryGetValue(chave, out var estado) || agora < estado.Inicio || agora - estado.Inicio >= _janela)
                estado = (0, agora);
            if (estado.Tentativas >= _maximo) return false;
            _dados[chave] = (estado.Tentativas + 1, estado.Inicio);
            return true;
        }
    }
}
