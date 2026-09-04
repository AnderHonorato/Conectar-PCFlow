using System.Buffers.Binary;
using System.Net;
using System.Text;

namespace PCFlow.Windows.Core;

/// <summary>
/// O código que o usuário copia do PC e cola no celular ou no outro PC.
///
/// Ele carrega tudo o que é preciso para conectar de qualquer lugar sem
/// depender de descoberta na rede: destino, porta e a identidade do PC.
/// Como a identidade viaja dentro do código, a conexão continua com pinagem
/// de certificado mesmo pela internet — ninguém no caminho consegue se passar
/// pelo seu computador.
///
/// Formato binário (22 bytes) para destino direto por IP:
///   [0]      versão/tipo (1 = IPv4 direto)
///   [1..4]   IPv4
///   [5..6]   porta de controle (big endian)
///   [7..22]  16 primeiros bytes do SHA-256 do certificado
///
/// Formato binário (20 bytes) para destino via servidor de retransmissão:
///   [0]      versão/tipo (2 = servidor)
///   [1..4]   identificador do PC no servidor
///   [5..20]  16 primeiros bytes do SHA-256 do certificado
///
/// O texto usa Base32 de Crockford (sem I, L, O e U) para não confundir letra
/// com número na hora de ditar ou digitar o código.
/// </summary>
public static class CodigoAcesso
{
    private const string Alfabeto = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public const byte TipoDireto = 1;
    public const byte TipoServidor = 2;

    /// <summary>Código de acesso direto: o celular fala com este IP e porta.</summary>
    public static string GerarDireto(IPAddress ip, int porta, string impressaoTls)
    {
        var bytes = new byte[23];
        bytes[0] = TipoDireto;
        ip.MapToIPv4().GetAddressBytes().CopyTo(bytes, 1);
        BinaryPrimitives.WriteUInt16BigEndian(bytes.AsSpan(5, 2), (ushort)porta);
        ImpressaoEmBytes(impressaoTls).CopyTo(bytes, 7);
        return Agrupar(ParaBase32(bytes));
    }

    /// <summary>Código de acesso por servidor: o celular pede este PC ao servidor.</summary>
    public static string GerarPorServidor(uint identificador, string impressaoTls)
    {
        var bytes = new byte[21];
        bytes[0] = TipoServidor;
        BinaryPrimitives.WriteUInt32BigEndian(bytes.AsSpan(1, 4), identificador);
        ImpressaoEmBytes(impressaoTls).CopyTo(bytes, 5);
        return Agrupar(ParaBase32(bytes));
    }

    /// <summary>Lê um código digitado. Devolve null quando o texto não é um código válido.</summary>
    public static DestinoAcesso? Ler(string? codigo)
    {
        if (string.IsNullOrWhiteSpace(codigo)) return null;
        byte[] bytes;
        try { bytes = DeBase32(codigo); }
        catch (FormatException) { return null; }

        if (bytes.Length == 23 && bytes[0] == TipoDireto)
        {
            var ip = new IPAddress(bytes.AsSpan(1, 4).ToArray());
            var porta = BinaryPrimitives.ReadUInt16BigEndian(bytes.AsSpan(5, 2));
            return new DestinoAcesso(TipoDireto, ip.ToString(), porta, 0, Convert.ToHexString(bytes, 7, 16).ToLowerInvariant());
        }
        if (bytes.Length == 21 && bytes[0] == TipoServidor)
        {
            var id = BinaryPrimitives.ReadUInt32BigEndian(bytes.AsSpan(1, 4));
            return new DestinoAcesso(TipoServidor, "", 0, id, Convert.ToHexString(bytes, 5, 16).ToLowerInvariant());
        }
        return null;
    }

    /// <summary>
    /// A impressão digital do código é truncada em 16 bytes para o texto não
    /// ficar impossível de copiar. Esta comparação aceita tanto a impressão
    /// completa quanto a truncada, sempre exigindo que o prefixo bata.
    /// </summary>
    public static bool ImpressaoConfere(string doCertificado, string doCodigo)
    {
        var a = Normalizar(doCertificado);
        var b = Normalizar(doCodigo);
        if (a.Length == 0 || b.Length < 32) return false;
        return a.Length >= b.Length && string.Equals(a[..b.Length], b, StringComparison.Ordinal);
    }

    private static string Normalizar(string impressao) =>
        impressao.Replace(":", "").Replace(" ", "").ToLowerInvariant();

    private static byte[] ImpressaoEmBytes(string impressao)
    {
        var texto = Normalizar(impressao);
        if (texto.Length < 32) throw new ArgumentException("Impressão digital TLS inválida", nameof(impressao));
        return Convert.FromHexString(texto[..32]);
    }

    private static string ParaBase32(ReadOnlySpan<byte> dados)
    {
        var saida = new StringBuilder((dados.Length * 8 + 4) / 5);
        int acumulador = 0, bits = 0;
        foreach (var b in dados)
        {
            acumulador = (acumulador << 8) | b;
            bits += 8;
            while (bits >= 5)
            {
                saida.Append(Alfabeto[(acumulador >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) saida.Append(Alfabeto[(acumulador << (5 - bits)) & 31]);
        return saida.ToString();
    }

    private static byte[] DeBase32(string texto)
    {
        var bytes = new List<byte>(texto.Length * 5 / 8 + 1);
        int acumulador = 0, bits = 0;
        foreach (var caractere in texto)
        {
            if (caractere is '-' or ' ' or '\t' or '\r' or '\n') continue;
            var valor = ValorDe(caractere);
            if (valor < 0) throw new FormatException($"Caractere inválido no código: {caractere}");
            acumulador = (acumulador << 5) | valor;
            bits += 5;
            if (bits >= 8)
            {
                bytes.Add((byte)((acumulador >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return bytes.ToArray();
    }

    /// <summary>Aceita as trocas que as pessoas fazem sem perceber: O por 0, I e L por 1.</summary>
    private static int ValorDe(char caractere)
    {
        var c = char.ToUpperInvariant(caractere);
        c = c switch { 'O' => '0', 'I' or 'L' => '1', 'U' => 'V', _ => c };
        return Alfabeto.IndexOf(c);
    }

    private static string Agrupar(string texto)
    {
        var saida = new StringBuilder(texto.Length + texto.Length / 5);
        for (var i = 0; i < texto.Length; i++)
        {
            if (i > 0 && i % 5 == 0) saida.Append('-');
            saida.Append(texto[i]);
        }
        return saida.ToString();
    }
}

/// <summary>Para onde um código de acesso aponta, já decodificado.</summary>
public sealed record DestinoAcesso(byte Tipo, string Host, int Porta, uint IdentificadorServidor, string ImpressaoTls)
{
    public bool ViaServidor => Tipo == CodigoAcesso.TipoServidor;
}
