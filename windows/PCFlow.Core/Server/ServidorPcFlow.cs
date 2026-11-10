using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using PCFlow.Core.Discovery;
using PCFlow.Core.Session;
using PCFlow.Core.Trust;

namespace PCFlow.Core;

public sealed class ServidorPcFlow : IAsyncDisposable
{
    public const int PortaDescobertaPadrao = 45455;
    public const int PortaControlePadrao = 45456;

    private readonly ICommandSink _sink;
    private readonly ArmazenamentoConfiguracao _armazenamento;
    private readonly ConfiguracaoPcFlow _configuracao;
    private readonly TrustStore _trust;
    private readonly SessionGate _gate = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly List<SessionEngine> _sessoes = [];
    private readonly object _sessaoLock = new();
    private TcpListener? _tcp;
    private UdpClient? _udp;

    public string CodigoPareamento { get; private set; } = GerarPin();
    public string EnderecoLocal => ObterEnderecoLocal();
    public int PortaControle { get; private set; }
    public IReadOnlyList<DispositivoAutorizado> Dispositivos => _trust.Devices;
    public ConfiguracaoPcFlow Configuracao => _configuracao;
    public bool Ativo { get; private set; }
    public bool Pausado { get; private set; }

    public event Action<string>? StatusAlterado;
    public event Action? DispositivosAlterados;

    public ServidorPcFlow(ICommandSink sink, int portaControle = PortaControlePadrao, int portaDescoberta = PortaDescobertaPadrao, ArmazenamentoConfiguracao? armazenamento = null, IPAddress? bind = null)
    {
        _sink = sink;
        PortaControle = portaControle;
        _armazenamento = armazenamento ?? new ArmazenamentoConfiguracao();
        _configuracao = _armazenamento.Carregar();
        if (string.IsNullOrWhiteSpace(_configuracao.HostDeviceId))
        {
            _configuracao.HostDeviceId = Guid.NewGuid().ToString("D");
            _armazenamento.Salvar(_configuracao);
        }
        _trust = new TrustStore(_configuracao.Dispositivos);
        var endereco = bind ?? IPAddress.Any;
        _tcp = new TcpListener(endereco, portaControle);
        _udp = new UdpClient(new IPEndPoint(endereco, portaDescoberta)) { EnableBroadcast = true };
    }

    public Task IniciarAsync()
    {
        if (Ativo)
            return Task.CompletedTask;
        Ativo = true;
        _tcp!.Start();
        PortaControle = ((IPEndPoint)_tcp.LocalEndpoint).Port;
        _ = AceitarAsync(_cts.Token);
        _ = DescobrirAsync(_cts.Token);
        StatusAlterado?.Invoke("Servidor ativo");
        return Task.CompletedTask;
    }

    public void AlternarPausa()
    {
        Pausado = !Pausado;
        StatusAlterado?.Invoke(Pausado ? "Servidor pausado" : "Servidor ativo");
    }

    public void Revogar(string deviceId)
    {
        List<SessionEngine> copia;
        lock (_sessaoLock)
            copia = _sessoes.ToList();
        var mudou = false;
        foreach (var sessao in copia)
        {
            var efeitos = sessao.Revoke(deviceId);
            mudou |= sessao.TrustChanged;
            if (efeitos.Count > 0)
                mudou = true;
        }
        if (!mudou)
            mudou = _trust.Revoke(deviceId);
        if (!mudou)
            return;
        _armazenamento.Salvar(_configuracao);
        DispositivosAlterados?.Invoke();
        StatusAlterado?.Invoke("Dispositivo revogado");
    }

    private async Task DescobrirAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _udp is not null)
        {
            try
            {
                var pacote = await _udp.ReceiveAsync(ct);
                var texto = Encoding.UTF8.GetString(pacote.Buffer);
                if (!DiscoveryProtocol.IsRequest(texto))
                    continue;
                var resposta = DiscoveryProtocol.Response(Environment.MachineName, PortaControle, _configuracao.HostDeviceId);
                var bytes = Encoding.UTF8.GetBytes(resposta);
                await _udp.SendAsync(bytes, pacote.RemoteEndPoint, ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Descoberta: {ex.Message}"); }
        }
    }

    private async Task AceitarAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcp is not null)
        {
            try
            {
                var cliente = await _tcp.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => AtenderAsync(cliente, ct), ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Conexão: {ex.Message}"); }
        }
    }

    private async Task AtenderAsync(TcpClient cliente, CancellationToken ct)
    {
        var engine = new SessionEngine(_trust, _configuracao.HostDeviceId, () => CodigoPareamento, RotacionarPin, _gate);
        lock (_sessaoLock)
            _sessoes.Add(engine);
        engine.OnSocketAccepted();
        using (cliente)
        using (var stream = cliente.GetStream())
        using (var reader = new StreamReader(stream, Encoding.UTF8, false, leaveOpen: true))
        using (var writer = new StreamWriter(stream, new UTF8Encoding(false), leaveOpen: true) { AutoFlush = true })
        {
            engine.MarkSocketUp();
            try
            {
                while (!ct.IsCancellationRequested && cliente.Connected)
                {
                    var linha = await reader.ReadLineAsync(ct);
                    if (linha is null)
                        break;
                    if (Aplicar(engine, engine.Receive(linha, Agora()), writer))
                        break;
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            finally
            {
                Aplicar(engine, engine.OnTransportClosed(), writer);
                if (engine.TrustChanged)
                {
                    _armazenamento.Salvar(_configuracao);
                    DispositivosAlterados?.Invoke();
                }
                lock (_sessaoLock)
                    _sessoes.Remove(engine);
                StatusAlterado?.Invoke("Servidor ativo");
            }
        }
    }

    private bool Aplicar(SessionEngine engine, IReadOnlyList<SessionEffect> efeitos, StreamWriter writer)
    {
        var fechar = false;
        foreach (var efeito in efeitos)
        {
            switch (efeito)
            {
                case SessionEffect.Reply reply:
                    writer.WriteLine(reply.Json);
                    break;
                case SessionEffect.Dispatch dispatch when !Pausado:
                    _sink.Execute(dispatch.Command);
                    break;
                case SessionEffect.ReleaseInput release:
                    _sink.ReleaseHeld(release.Kind, release.Code);
                    break;
                case SessionEffect.CloseSocket:
                    fechar = true;
                    break;
            }
        }
        if (engine.TrustChanged)
        {
            _armazenamento.Salvar(_configuracao);
            DispositivosAlterados?.Invoke();
        }
        return fechar;
    }

    private void RotacionarPin() => CodigoPareamento = GerarPin();

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
        _cts.Dispose();
        await Task.CompletedTask;
    }

    private static long Agora() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    private static string GerarPin() => RandomNumberGenerator.GetInt32(100000, 999999).ToString();

    private static string ObterEnderecoLocal()
    {
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces().Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback))
        {
            var ip = ni.GetIPProperties().UnicastAddresses.FirstOrDefault(a => a.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address));
            if (ip is not null)
                return ip.Address.ToString();
        }
        return "127.0.0.1";
    }
}
