using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using Xunit;

namespace PCFlow.Tests;

/// <summary>
/// O servidor de retransmissão é a peça que faz o PCFlow funcionar quando a
/// operadora usa CGNAT e não existe porta para abrir. Estes testes iniciam o
/// binário de verdade e passam bytes pelos dois lados, porque um encanamento
/// que "compila" não prova nada: ou os bytes chegam, ou o acesso remoto não
/// existe.
/// </summary>
public sealed class TestesRelay : IAsyncLifetime
{
    private Process? _relay;
    private int _porta;

    public async Task InitializeAsync()
    {
        _porta = PortaLivre();
        var dll = LocalizarRelay();
        _relay = Process.Start(new ProcessStartInfo(ExecutavelDotnet(), $"\"{dll}\" {_porta}")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false
        }) ?? throw new InvalidOperationException("Não consegui iniciar o servidor de retransmissão");

        var erros = _relay.StandardError.ReadToEndAsync();
        // Espera o "ouvindo na porta" para não competir com a subida do processo.
        var pronto = _relay.StandardOutput.ReadLineAsync();
        var terminou = await Task.WhenAny(pronto, Task.Delay(TimeSpan.FromSeconds(30)));
        if (terminou != pronto || pronto.Result is null)
            throw new InvalidOperationException(
                $"O servidor de retransmissão não subiu. Saída de erro: {(erros.IsCompleted ? erros.Result : "(sem saída)")}");
        _ = Task.Run(() => _relay.StandardOutput.ReadToEnd());
    }

    public Task DisposeAsync()
    {
        try { if (_relay is { HasExited: false }) _relay.Kill(entireProcessTree: true); } catch { }
        _relay?.Dispose();
        return Task.CompletedTask;
    }

    [Fact]
    public async Task OsBytesAtravessamODosLadosDoServidor()
    {
        // O PC se registra e fica esperando chamadas.
        using var pc = await ConectarAsync();
        await EscreverAsync(pc, new { tipo = "registrar", codigo = "123456789", segredo = new string('a', 64), nome = "PC-DE-TESTE" });
        Assert.Equal("registrado", await TipoDaRespostaAsync(pc));

        // O celular pede este código.
        using var celular = await ConectarAsync();
        await EscreverAsync(celular, new { tipo = "conectar", codigo = "123456789", alvo = "controle" });

        // O PC recebe a chamada e traz a outra ponta do canal.
        var chamada = JsonDocument.Parse(await LerLinhaAsync(pc) ?? "{}").RootElement;
        Assert.Equal("chamada", chamada.GetProperty("tipo").GetString());
        Assert.Equal("controle", chamada.GetProperty("alvo").GetString());

        using var canal = await ConectarAsync();
        await EscreverAsync(canal, new
        {
            tipo = "canal",
            canal = chamada.GetProperty("canal").GetString(),
            segredo = new string('a', 64)
        });
        Assert.Equal("pronto", await TipoDaRespostaAsync(canal));
        Assert.Equal("pronto", await TipoDaRespostaAsync(celular));

        // Daqui em diante é byte cru: é isso que carrega o TLS de ponta a ponta.
        await EnviarCruAsync(celular, "tls-do-celular-para-o-pc\n");
        Assert.Equal("tls-do-celular-para-o-pc", await LerLinhaAsync(canal));

        await EnviarCruAsync(canal, "tela-do-pc-para-o-celular\n");
        Assert.Equal("tela-do-pc-para-o-celular", await LerLinhaAsync(celular));
    }

    [Fact]
    public async Task OutroComputadorNaoRoubaUmCodigoJaRegistrado()
    {
        using var dono = await ConectarAsync();
        await EscreverAsync(dono, new { tipo = "registrar", codigo = "987654321", segredo = new string('b', 64), nome = "Dono" });
        Assert.Equal("registrado", await TipoDaRespostaAsync(dono));

        using var invasor = await ConectarAsync();
        await EscreverAsync(invasor, new { tipo = "registrar", codigo = "987654321", segredo = new string('c', 64), nome = "Invasor" });

        var resposta = JsonDocument.Parse(await LerLinhaAsync(invasor) ?? "{}").RootElement;
        Assert.Equal("erro", resposta.GetProperty("tipo").GetString());
        Assert.Contains("em uso", resposta.GetProperty("mensagem").GetString()!);
    }

    [Fact]
    public async Task PedirUmComputadorQueNaoEstaOnlineDaErroClaro()
    {
        using var celular = await ConectarAsync();
        await EscreverAsync(celular, new { tipo = "conectar", codigo = "000000000", alvo = "controle" });

        var resposta = JsonDocument.Parse(await LerLinhaAsync(celular) ?? "{}").RootElement;
        Assert.Equal("erro", resposta.GetProperty("tipo").GetString());
        Assert.Contains("não está conectado", resposta.GetProperty("mensagem").GetString()!);
    }

    [Fact]
    public async Task CanalInventadoEhRecusado()
    {
        using var falso = await ConectarAsync();
        await EscreverAsync(falso, new { tipo = "canal", canal = "canal-que-nunca-existiu", segredo = new string('d', 64) });

        var resposta = JsonDocument.Parse(await LerLinhaAsync(falso) ?? "{}").RootElement;
        Assert.Equal("erro", resposta.GetProperty("tipo").GetString());
    }

    // ---------- apoio ----------

    private async Task<TcpClient> ConectarAsync()
    {
        var cliente = new TcpClient { NoDelay = true };
        await cliente.ConnectAsync(IPAddress.Loopback, _porta);
        return cliente;
    }

    private static async Task EscreverAsync(TcpClient cliente, object valor) =>
        await EnviarCruAsync(cliente, JsonSerializer.Serialize(valor, new JsonSerializerOptions(JsonSerializerDefaults.Web)) + "\n");

    private static async Task EnviarCruAsync(TcpClient cliente, string texto)
    {
        var bytes = Encoding.UTF8.GetBytes(texto);
        await cliente.GetStream().WriteAsync(bytes);
        await cliente.GetStream().FlushAsync();
    }

    private static async Task<string?> TipoDaRespostaAsync(TcpClient cliente)
    {
        var linha = await LerLinhaAsync(cliente);
        return linha is null ? null : JsonDocument.Parse(linha).RootElement.GetProperty("tipo").GetString();
    }

    private static async Task<string?> LerLinhaAsync(TcpClient cliente)
    {
        using var espera = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        var fluxo = cliente.GetStream();
        using var memoria = new MemoryStream();
        var buffer = new byte[1];
        while (memoria.Length < 8192)
        {
            var lidos = await fluxo.ReadAsync(buffer, espera.Token);
            if (lidos == 0) return memoria.Length == 0 ? null : Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] != (byte)'\r') memoria.WriteByte(buffer[0]);
        }
        return null;
    }

    private static int PortaLivre()
    {
        using var sonda = new TcpListener(IPAddress.Loopback, 0);
        sonda.Start();
        var porta = ((IPEndPoint)sonda.LocalEndpoint).Port;
        sonda.Stop();
        return porta;
    }

    /// <summary>
    /// O testhost já roda dentro do dotnet: usar o mesmo executável evita
    /// depender do PATH da máquina que estiver rodando os testes.
    /// </summary>
    private static string ExecutavelDotnet()
    {
        var atual = Environment.ProcessPath;
        if (atual is not null && Path.GetFileNameWithoutExtension(atual).Equals("dotnet", StringComparison.OrdinalIgnoreCase))
            return atual;
        var raiz = Environment.GetEnvironmentVariable("DOTNET_ROOT");
        if (raiz is not null)
        {
            var candidato = Path.Combine(raiz, OperatingSystem.IsWindows() ? "dotnet.exe" : "dotnet");
            if (File.Exists(candidato)) return candidato;
        }
        return "dotnet";
    }

    private static string LocalizarRelay()
    {
        var pasta = AppContext.BaseDirectory;
        for (var i = 0; i < 8 && pasta is not null; i++)
        {
            var candidato = Path.Combine(pasta, "server", "PCFlow.Relay", "bin");
            if (Directory.Exists(candidato))
            {
                var dll = Directory.GetFiles(candidato, "pcflow-relay.dll", SearchOption.AllDirectories).FirstOrDefault();
                if (dll is not null) return dll;
            }
            pasta = Path.GetDirectoryName(pasta.TrimEnd(Path.DirectorySeparatorChar));
        }
        throw new FileNotFoundException(
            "pcflow-relay.dll não encontrado. Compile a solução antes de rodar os testes.");
    }
}
