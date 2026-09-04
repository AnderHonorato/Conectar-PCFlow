using System.IO;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace PCFlow.Windows.Core;

/// <summary>
/// Mantém este PC registrado num servidor de retransmissão do PCFlow.
///
/// Serve para o caso em que abrir porta no roteador não é possível — CGNAT,
/// rede corporativa, Wi-Fi de operadora. O PC abre uma conexão de saída para o
/// servidor e fica esperando chamadas; quando o celular pede este código, o
/// servidor avisa e o PC traz um canal novo. O conteúdo continua TLS de ponta
/// a ponta com pinagem, então o servidor não enxerga nem a tela nem as teclas.
/// </summary>
public sealed class AgenteRelay : IAsyncDisposable
{
    public const int PortaPadrao = 45460;

    private readonly ServidorPcFlow _servidor;
    private readonly ConfiguracaoPcFlow _configuracao;
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    private CancellationTokenSource? _cts;
    private Task? _laco;

    public bool Conectado { get; private set; }
    public string UltimoDetalhe { get; private set; } = "Servidor de retransmissão desligado";
    public event Action<string>? Status;

    public AgenteRelay(ServidorPcFlow servidor)
    {
        _servidor = servidor;
        _configuracao = servidor.Configuracao;
    }

    /// <summary>Código que o usuário digita no celular para chegar por servidor.</summary>
    public string CodigoServidor => _configuracao.MaquinaId;

    public void Iniciar()
    {
        if (_laco is not null) return;
        if (string.IsNullOrWhiteSpace(_configuracao.ServidorRelay))
        {
            Anunciar("Nenhum servidor de retransmissão configurado.");
            return;
        }
        GarantirSegredo();
        _cts = new CancellationTokenSource();
        _laco = ManterRegistroAsync(_cts.Token);
    }

    public async Task PararAsync()
    {
        if (_cts is null) return;
        await _cts.CancelAsync();
        if (_laco is not null) { try { await _laco; } catch { } }
        _laco = null;
        _cts.Dispose();
        _cts = null;
        Conectado = false;
        Anunciar("Servidor de retransmissão desligado");
    }

    private void GarantirSegredo()
    {
        if (!string.IsNullOrWhiteSpace(_configuracao.SegredoRelay)) return;
        _configuracao.SegredoRelay = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();
        _servidor.SalvarConfiguracao();
    }

    private async Task ManterRegistroAsync(CancellationToken ct)
    {
        var espera = TimeSpan.FromSeconds(3);
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await RegistrarAsync(ct);
                espera = TimeSpan.FromSeconds(3); // deu certo uma vez: volta a tentar rápido
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex)
            {
                Conectado = false;
                Anunciar($"Servidor de retransmissão: {Explicar(ex)}");
            }
            if (ct.IsCancellationRequested) break;
            try { await Task.Delay(espera, ct); } catch (OperationCanceledException) { break; }
            espera = TimeSpan.FromSeconds(Math.Min(60, espera.TotalSeconds * 2));
        }
    }

    private async Task RegistrarAsync(CancellationToken ct)
    {
        var (host, porta) = SepararEndereco(_configuracao.ServidorRelay);
        using var cliente = new TcpClient { NoDelay = true };
        await cliente.ConnectAsync(host, porta, ct);
        await using var fluxo = cliente.GetStream();

        await EscreverAsync(fluxo, new
        {
            tipo = "registrar",
            codigo = CodigoServidor,
            segredo = _configuracao.SegredoRelay,
            nome = Environment.MachineName
        }, ct);

        var resposta = await LerLinhaAsync(fluxo, ct) ?? throw new IOException("O servidor não respondeu ao registro");
        using (var doc = JsonDocument.Parse(resposta))
        {
            var tipo = doc.RootElement.TryGetProperty("tipo", out var t) ? t.GetString() : null;
            if (tipo != "registrado")
            {
                var mensagem = doc.RootElement.TryGetProperty("mensagem", out var m) ? m.GetString() : "recusado";
                throw new InvalidOperationException(mensagem ?? "registro recusado");
            }
        }

        Conectado = true;
        Anunciar($"Pronto para receber conexões pelo servidor com o código {CodigoServidor}");

        using var pulsos = new CancellationTokenSource();
        using var juntos = CancellationTokenSource.CreateLinkedTokenSource(ct, pulsos.Token);
        var batida = PulsarAsync(fluxo, juntos.Token);

        try
        {
            while (!juntos.IsCancellationRequested)
            {
                var linha = await LerLinhaAsync(fluxo, juntos.Token);
                if (linha is null) break;
                using var doc = JsonDocument.Parse(linha);
                if (doc.RootElement.TryGetProperty("tipo", out var t) && t.GetString() == "chamada")
                {
                    var canal = doc.RootElement.GetProperty("canal").GetString()!;
                    var alvo = doc.RootElement.TryGetProperty("alvo", out var a) ? a.GetString() ?? "controle" : "controle";
                    _ = Task.Run(() => AtenderChamadaAsync(host, porta, canal, alvo, ct), ct);
                }
            }
        }
        finally
        {
            pulsos.Cancel();
            try { await batida; } catch { }
            Conectado = false;
        }
        throw new IOException("O servidor de retransmissão encerrou a conexão");
    }

    private async Task PulsarAsync(Stream fluxo, CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested)
            {
                await Task.Delay(TimeSpan.FromSeconds(25), ct);
                await EscreverAsync(fluxo, new { tipo = "ping" }, ct);
            }
        }
        catch (Exception) { /* a queda é tratada pelo laço de leitura */ }
    }

    private async Task AtenderChamadaAsync(string host, int porta, string canal, string alvo, CancellationToken ct)
    {
        try
        {
            var cliente = new TcpClient { NoDelay = true };
            await cliente.ConnectAsync(host, porta, ct);
            var fluxo = cliente.GetStream();

            await EscreverAsync(fluxo, new { tipo = "canal", canal, segredo = _configuracao.SegredoRelay }, ct);
            var resposta = await LerLinhaAsync(fluxo, ct);
            if (resposta is null) { cliente.Dispose(); return; }
            using (var doc = JsonDocument.Parse(resposta))
            {
                if (!doc.RootElement.TryGetProperty("tipo", out var t) || t.GetString() != "pronto")
                {
                    cliente.Dispose();
                    return;
                }
            }

            Anunciar($"Sessão {alvo} chegando pelo servidor de retransmissão");
            using (cliente)
                await _servidor.AtenderCanalDoRelayAsync(alvo, fluxo, () => cliente.Connected, ct);
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { Anunciar($"Canal pelo servidor falhou: {Explicar(ex)}"); }
    }

    public static (string Host, int Porta) SepararEndereco(string endereco)
    {
        var texto = endereco.Trim();
        var separador = texto.LastIndexOf(':');
        if (separador > 0 && int.TryParse(texto[(separador + 1)..], out var porta))
            return (texto[..separador], porta);
        return (texto, PortaPadrao);
    }

    private static string Explicar(Exception ex) => ex switch
    {
        SocketException s => $"não consegui falar com o servidor ({s.SocketErrorCode})",
        IOException => "a conexão com o servidor caiu",
        InvalidOperationException => ex.Message,
        _ => ex.Message
    };

    private void Anunciar(string mensagem)
    {
        UltimoDetalhe = mensagem;
        Status?.Invoke(mensagem);
    }

    private async Task EscreverAsync(Stream destino, object valor, CancellationToken ct)
    {
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(valor, _json) + "\n");
        await destino.WriteAsync(bytes, ct);
        await destino.FlushAsync(ct);
    }

    private static async Task<string?> LerLinhaAsync(Stream origem, CancellationToken ct)
    {
        using var memoria = new MemoryStream();
        var buffer = new byte[1];
        while (memoria.Length < 8192)
        {
            var lidos = await origem.ReadAsync(buffer, ct);
            if (lidos == 0) return memoria.Length == 0 ? null : Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] != (byte)'\r') memoria.WriteByte(buffer[0]);
        }
        return null;
    }

    public async ValueTask DisposeAsync() => await PararAsync();
}
