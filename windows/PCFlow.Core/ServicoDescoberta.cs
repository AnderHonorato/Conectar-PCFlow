using System.Net;
using System.Net.Sockets;
using System.Text;

namespace PCFlow.Core;

/// <summary>
/// Responde às sondas de descoberta do Android via UDP.
///
/// Correções em relação à v1:
///  - responde tanto à sonda V2 quanto à V1 (compatibilidade);
///  - continua rodando se a porta estiver ocupada por outra instância (não derruba o app);
///  - responde ao endereço de origem exato, o que funciona com broadcast dirigido à sub-rede.
/// </summary>
public sealed class ServicoDescoberta : IAsyncDisposable
{
    private readonly Func<string> _nome;
    private readonly Func<int> _porta;
    private readonly Action<string>? _log;
    private UdpClient? _udp;
    private CancellationTokenSource? _cts;
    private Task? _laco;

    public bool Ativo { get; private set; }
    public string? UltimoErro { get; private set; }

    public ServicoDescoberta(Func<string> nome, Func<int> porta, Action<string>? log = null)
    {
        _nome = nome;
        _porta = porta;
        _log = log;
    }

    public bool Iniciar()
    {
        if (Ativo) return true;
        try
        {
            _udp = new UdpClient(AddressFamily.InterNetwork);
            _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _udp.Client.Bind(new IPEndPoint(IPAddress.Any, Protocolo.PortaDescoberta));
            _udp.EnableBroadcast = true;
            _cts = new CancellationTokenSource();
            _laco = LacoAsync(_cts.Token);
            Ativo = true;
            UltimoErro = null;
            return true;
        }
        catch (SocketException ex)
        {
            UltimoErro = $"Porta {Protocolo.PortaDescoberta}/UDP indisponível: {ex.SocketErrorCode}";
            _log?.Invoke(UltimoErro);
            _udp?.Dispose();
            _udp = null;
            return false;
        }
    }

    private async Task LacoAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _udp is not null)
        {
            UdpReceiveResult pacote;
            try
            {
                pacote = await _udp.ReceiveAsync(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (SocketException) { continue; }

            try
            {
                var texto = Encoding.UTF8.GetString(pacote.Buffer);
                if (!texto.StartsWith("PCFLOW_DISCOVER", StringComparison.Ordinal)) continue;

                var resposta = Protocolo.Serializar(new
                {
                    tipo = "anuncio",
                    nome = _nome(),
                    porta = _porta(),
                    protocolo = Protocolo.Versao,
                    versao = Protocolo.VersaoApp
                });
                var bytes = Encoding.UTF8.GetBytes(resposta);
                await _udp.SendAsync(bytes, pacote.RemoteEndPoint, ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { _log?.Invoke($"Descoberta: {ex.Message}"); }
        }
    }

    public async ValueTask DisposeAsync()
    {
        Ativo = false;
        if (_cts is not null) await _cts.CancelAsync().ConfigureAwait(false);
        _udp?.Dispose();
        _udp = null;
        if (_laco is not null)
        {
            try { await _laco.ConfigureAwait(false); } catch { }
            _laco = null;
        }
        _cts?.Dispose();
        _cts = null;
    }
}
