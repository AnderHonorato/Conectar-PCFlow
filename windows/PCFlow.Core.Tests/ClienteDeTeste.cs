using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace PCFlow.Core.Tests;

/// <summary>
/// Cliente que fala o mesmo protocolo do app Android. É com ele que os testes de
/// integração exercitam pareamento, comandos, heartbeat e reconexão de verdade,
/// contra o servidor real rodando em localhost.
/// </summary>
public sealed class ClienteDeTeste : IDisposable
{
    private readonly TcpClient _tcp = new();
    private StreamReader? _leitor;
    private StreamWriter? _escritor;

    public async Task ConectarAsync(int porta)
    {
        await _tcp.ConnectAsync("127.0.0.1", porta);
        _tcp.NoDelay = true;
        var fluxo = _tcp.GetStream();
        _leitor = new StreamReader(fluxo, Encoding.UTF8, false, 8192, leaveOpen: true);
        _escritor = new StreamWriter(fluxo, new UTF8Encoding(false), 8192, leaveOpen: true)
        { AutoFlush = true };
    }

    public async Task EnviarAsync(object payload)
    {
        if (_escritor is null) throw new InvalidOperationException("Cliente não conectado.");
        await _escritor.WriteLineAsync(JsonSerializer.Serialize(payload, Protocolo.Json));
    }

    /// <summary>Envia texto cru — usado nos testes de mensagem malformada e gigante.</summary>
    public async Task EnviarCruAsync(string linha)
    {
        if (_escritor is null) throw new InvalidOperationException("Cliente não conectado.");
        await _escritor.WriteLineAsync(linha);
    }

    public async Task<JsonElement?> ReceberAsync(TimeSpan? limite = null)
    {
        if (_leitor is null) return null;
        var tarefa = _leitor.ReadLineAsync();
        var vencedor = await Task.WhenAny(tarefa, Task.Delay(limite ?? TimeSpan.FromSeconds(5)));
        if (vencedor != tarefa) return null;
        var linha = await tarefa;
        if (linha is null) return null;
        return JsonDocument.Parse(linha).RootElement.Clone();
    }

    public Task<JsonElement?> ApresentarAsync(string id, string? pin, string? token, string nome = "Celular de Teste")
    {
        var ola = new Dictionary<string, object?>
        {
            ["tipo"] = "ola",
            ["protocolo"] = Protocolo.Versao,
            ["dispositivoId"] = id,
            ["nome"] = nome,
            ["modelo"] = "Teste"
        };
        if (pin is not null) ola["pin"] = pin;
        if (token is not null) ola["token"] = token;
        return EnviarAsync(ola).ContinueWith(_ => ReceberAsync()).Unwrap();
    }

    public bool Conectado => _tcp.Connected;

    public void Dispose()
    {
        try { _escritor?.Dispose(); } catch { }
        try { _leitor?.Dispose(); } catch { }
        _tcp.Dispose();
    }
}

/// <summary>Sobe um servidor real numa porta livre e garante o encerramento.</summary>
public sealed class ServidorDeTeste : IAsyncDisposable
{
    public ServidorPcFlow Servidor { get; }
    public EntradaGravada Entrada { get; }
    public MidiaGravada Midia { get; }
    public EnergiaGravada Energia { get; }
    public AreaTransferenciaFake Clipboard { get; }
    public int Porta => Servidor.PortaEmUso;

    private readonly string _pasta;

    public ServidorDeTeste(Action<ConfiguracaoPcFlow>? ajustar = null)
    {
        var (plataforma, entrada, midia, energia, clip) = Fabrica.Criar();
        Entrada = entrada; Midia = midia; Energia = energia; Clipboard = clip;

        var armazenamento = Fabrica.ArmazenamentoTemporario(out _pasta);
        Servidor = new ServidorPcFlow(plataforma, armazenamento);
        Servidor.Configuracao.PortaControle = PortaLivre();
        ajustar?.Invoke(Servidor.Configuracao);

        if (!Servidor.Iniciar())
            throw new InvalidOperationException(Servidor.UltimoErro ?? "Servidor não subiu.");
    }

    private static int PortaLivre()
    {
        var ouvinte = new TcpListener(System.Net.IPAddress.Loopback, 0);
        ouvinte.Start();
        var porta = ((System.Net.IPEndPoint)ouvinte.LocalEndpoint).Port;
        ouvinte.Stop();
        return porta;
    }

    public async ValueTask DisposeAsync()
    {
        await Servidor.DisposeAsync();
        try { Directory.Delete(_pasta, true); } catch { }
    }
}
