using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace PCFlow.Windows.Core;

public sealed class ServidorPcFlow : IAsyncDisposable
{
    public const int PortaDescoberta = 45455;
    public const int PortaControle = 45456;

    private readonly ArmazenamentoConfiguracao _armazenamento = new();
    private readonly ConfiguracaoPcFlow _configuracao;
    private readonly CancellationTokenSource _cts = new();
    private TcpListener? _tcp;
    private UdpClient? _udp;
    private Task? _tarefaTcp;
    private Task? _tarefaUdp;
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);

    public string CodigoPareamento { get; private set; } = GerarPin();
    public string EnderecoLocal => ObterEnderecoLocal();
    public IReadOnlyList<DispositivoAutorizado> Dispositivos => _configuracao.Dispositivos;
    public bool Ativo { get; private set; }
    public bool Pausado { get; private set; }
    public ConfiguracaoPcFlow Configuracao => _configuracao;

    public event Action<string>? StatusAlterado;
    public event Action? DispositivosAlterados;

    public ServidorPcFlow() => _configuracao = _armazenamento.Carregar();

    public Task IniciarAsync()
    {
        if (Ativo) return Task.CompletedTask;
        Ativo = true;
        _tcp = new TcpListener(IPAddress.Any, PortaControle);
        _tcp.Start();
        _udp = new UdpClient(PortaDescoberta) { EnableBroadcast = true };
        _tarefaTcp = AceitarClientesAsync(_cts.Token);
        _tarefaUdp = ResponderDescobertaAsync(_cts.Token);
        StatusAlterado?.Invoke("Servidor ativo");
        return Task.CompletedTask;
    }

    private async Task ResponderDescobertaAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _udp is not null)
        {
            try
            {
                var pacote = await _udp.ReceiveAsync(ct);
                var texto = Encoding.UTF8.GetString(pacote.Buffer);
                if (!texto.StartsWith("PCFLOW_DISCOVER_V1", StringComparison.Ordinal)) continue;
                var resposta = JsonSerializer.Serialize(new { tipo = "pcflow", nome = Environment.MachineName, porta = PortaControle, protocolo = 1 }, _json);
                var bytes = Encoding.UTF8.GetBytes(resposta);
                await _udp.SendAsync(bytes, pacote.RemoteEndPoint, ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Descoberta: {ex.Message}"); }
        }
    }

    private async Task AceitarClientesAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcp is not null)
        {
            try
            {
                var cliente = await _tcp.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => AtenderClienteAsync(cliente, ct), ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Conexão: {ex.Message}"); }
        }
    }

    private async Task AtenderClienteAsync(TcpClient cliente, CancellationToken ct)
    {
        using (cliente)
        using (var stream = cliente.GetStream())
        using (var reader = new StreamReader(stream, Encoding.UTF8, false, leaveOpen: true))
        using (var writer = new StreamWriter(stream, new UTF8Encoding(false), leaveOpen: true) { AutoFlush = true })
        {
            MensagemRede? ola;
            try
            {
                var linha = await reader.ReadLineAsync(ct);
                ola = linha is null ? null : JsonSerializer.Deserialize<MensagemRede>(linha, _json);
            }
            catch { return; }

            if (ola is null || ola.Tipo != "ola" || string.IsNullOrWhiteSpace(ola.DispositivoId)) return;
            var autorizado = _configuracao.Dispositivos.FirstOrDefault(d => d.Id == ola.DispositivoId);
            if (autorizado is null)
            {
                if (ola.Pin != CodigoPareamento)
                {
                    await writer.WriteLineAsync(JsonSerializer.Serialize(new { tipo = "erro", mensagem = "PIN inválido" }, _json));
                    return;
                }
                autorizado = new DispositivoAutorizado
                {
                    Id = ola.DispositivoId,
                    Nome = ola.Nome ?? "Android",
                    Token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)),
                    UltimaConexao = DateTime.UtcNow
                };
                _configuracao.Dispositivos.Add(autorizado);
                _armazenamento.Salvar(_configuracao);
                CodigoPareamento = GerarPin();
                DispositivosAlterados?.Invoke();
                await writer.WriteLineAsync(JsonSerializer.Serialize(new { tipo = "pareado", token = autorizado.Token, nome = Environment.MachineName }, _json));
            }
            else
            {
                if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(autorizado.Token), Encoding.UTF8.GetBytes(ola.Token ?? "")))
                {
                    await writer.WriteLineAsync(JsonSerializer.Serialize(new { tipo = "erro", mensagem = "Dispositivo não autorizado" }, _json));
                    return;
                }
                autorizado.UltimaConexao = DateTime.UtcNow;
                _armazenamento.Salvar(_configuracao);
                await writer.WriteLineAsync(JsonSerializer.Serialize(new { tipo = "conectado", nome = Environment.MachineName }, _json));
            }

            StatusAlterado?.Invoke($"{autorizado.Nome} conectado");
            try
            {
                while (!ct.IsCancellationRequested && cliente.Connected)
                {
                    var linha = await reader.ReadLineAsync(ct);
                    if (linha is null) break;
                    var mensagem = JsonSerializer.Deserialize<MensagemRede>(linha, _json);
                    if (mensagem is not null && !Pausado) ExecutorComandos.Executar(mensagem);
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            finally { StatusAlterado?.Invoke("Servidor ativo"); }
        }
    }

    public void AlternarPausa()
    {
        Pausado = !Pausado;
        StatusAlterado?.Invoke(Pausado ? "Servidor pausado" : "Servidor ativo");
    }

    public void DefinirMinimizarParaBandeja(bool valor)
    {
        _configuracao.MinimizarParaBandeja = valor;
        _armazenamento.Salvar(_configuracao);
    }

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        _tcp?.Stop();
        _udp?.Dispose();
        if (_tarefaTcp is not null) try { await _tarefaTcp; } catch { }
        if (_tarefaUdp is not null) try { await _tarefaUdp; } catch { }
        _cts.Dispose();
    }

    private static string GerarPin() => RandomNumberGenerator.GetInt32(100000, 999999).ToString();

    private static string ObterEnderecoLocal()
    {
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces().Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback))
        {
            var ip = ni.GetIPProperties().UnicastAddresses.FirstOrDefault(a => a.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address));
            if (ip is not null) return ip.Address.ToString();
        }
        return "127.0.0.1";
    }
}
