using System.Buffers;
using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

// Servidor de retransmissão do PCFlow.
//
// É um encanador cego: ele nunca vê o conteúdo da sessão. O PC e o celular
// continuam falando TLS de ponta a ponta com pinagem de certificado, e este
// servidor só costura dois sockets pelo mesmo código. Por isso ele pode rodar
// em qualquer VPS barata sem ser um ponto de confiança.
//
// Uso:
//   pcflow-relay [porta]                 (padrão 45460)
//   PCFLOW_RELAY_PORTA=45460 pcflow-relay
//
// Fluxo:
//   PC     -> {"tipo":"registrar","codigo":"123456789","segredo":"...","nome":"PC-DA-SALA"}
//   PC     <- {"tipo":"registrado"}                       (conexão fica aberta como canal de controle)
//   Cel    -> {"tipo":"conectar","codigo":"123456789","alvo":"controle"}
//   PC     <- {"tipo":"chamada","canal":"ab12…","alvo":"controle"}
//   PC     -> (nova conexão) {"tipo":"canal","canal":"ab12…","segredo":"..."}
//   ambos  <- {"tipo":"pronto"}   e a partir daqui é byte puro nos dois sentidos.

var porta = ResolverPorta(args);
var registros = new ConcurrentDictionary<string, RegistroPc>(StringComparer.OrdinalIgnoreCase);
var pendentes = new ConcurrentDictionary<string, CanalPendente>(StringComparer.OrdinalIgnoreCase);
var json = new JsonSerializerOptions(JsonSerializerDefaults.Web);

using var encerrar = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; encerrar.Cancel(); };

var ouvinte = AbrirEscuta(porta);
ouvinte.Start();
Registrar($"PCFlow Relay ouvindo na porta {porta} ({ouvinte.LocalEndpoint})");

_ = Task.Run(async () =>
{
    // Faxina: tira do ar canal que ninguém veio buscar.
    while (!encerrar.IsCancellationRequested)
    {
        await Task.Delay(TimeSpan.FromSeconds(15), encerrar.Token);
        foreach (var (id, canal) in pendentes)
            if (DateTime.UtcNow - canal.CriadoEm > TimeSpan.FromSeconds(30) && pendentes.TryRemove(id, out _))
                canal.Desistir();
    }
}, encerrar.Token);

while (!encerrar.IsCancellationRequested)
{
    TcpClient cliente;
    try { cliente = await ouvinte.AcceptTcpClientAsync(encerrar.Token); }
    catch (OperationCanceledException) { break; }
    _ = Task.Run(() => AtenderAsync(cliente, encerrar.Token));
}

async Task AtenderAsync(TcpClient cliente, CancellationToken ct)
{
    var remoto = cliente.Client.RemoteEndPoint?.ToString() ?? "?";
    cliente.NoDelay = true;
    var fluxo = cliente.GetStream();
    string? codigoRegistrado = null;
    try
    {
        var linha = await LerLinhaAsync(fluxo, ct);
        if (linha is null) { cliente.Dispose(); return; }
        using var doc = JsonDocument.Parse(linha);
        var raiz = doc.RootElement;
        var tipo = Texto(raiz, "tipo");

        switch (tipo)
        {
            case "registrar":
                codigoRegistrado = await RegistrarPcAsync(cliente, fluxo, raiz, remoto, ct);
                return;
            case "canal":
                await EntregarCanalDoPcAsync(cliente, fluxo, raiz, ct);
                return;
            case "conectar":
                await PedirCanalAsync(cliente, fluxo, raiz, remoto, ct);
                return;
            default:
                await ResponderAsync(fluxo, new { tipo = "erro", mensagem = "Pedido desconhecido" }, ct);
                cliente.Dispose();
                return;
        }
    }
    catch (Exception ex)
    {
        Registrar($"{remoto}: {ex.Message}");
        cliente.Dispose();
    }
    finally
    {
        if (codigoRegistrado is not null &&
            registros.TryGetValue(codigoRegistrado, out var reg) && reg.Controle == cliente)
        {
            registros.TryRemove(codigoRegistrado, out _);
            Registrar($"PC {codigoRegistrado} saiu do ar");
        }
    }
}

async Task<string?> RegistrarPcAsync(TcpClient cliente, NetworkStream fluxo, JsonElement raiz, string remoto, CancellationToken ct)
{
    var codigo = Texto(raiz, "codigo");
    var segredo = Texto(raiz, "segredo");
    var nome = Texto(raiz, "nome") ?? "PC";
    if (string.IsNullOrWhiteSpace(codigo) || string.IsNullOrWhiteSpace(segredo) || segredo.Length < 32)
    {
        await ResponderAsync(fluxo, new { tipo = "erro", mensagem = "Código ou segredo inválido" }, ct);
        cliente.Dispose();
        return null;
    }

    var novo = new RegistroPc(codigo, Resumo(segredo), nome, cliente, fluxo);
    while (true)
    {
        if (registros.TryAdd(codigo, novo)) break;
        if (!registros.TryGetValue(codigo, out var atual)) continue;
        // Só o dono do código retoma o registro: sem isso qualquer um derrubaria o PC alheio.
        if (!CryptographicOperations.FixedTimeEquals(atual.ResumoSegredo, novo.ResumoSegredo))
        {
            await ResponderAsync(fluxo, new { tipo = "erro", mensagem = "Este código já está em uso por outro computador" }, ct);
            cliente.Dispose();
            return null;
        }
        if (registros.TryUpdate(codigo, novo, atual)) { atual.Controle.Dispose(); break; }
    }

    await ResponderAsync(fluxo, new { tipo = "registrado", codigo }, ct);
    Registrar($"PC {codigo} ({nome}) registrado de {remoto}");

    // O canal de controle fica aberto: é por ele que o PC recebe as chamadas.
    while (!ct.IsCancellationRequested)
    {
        var linha = await LerLinhaAsync(fluxo, ct);
        if (linha is null) break;
        using var doc = JsonDocument.Parse(linha);
        if (Texto(doc.RootElement, "tipo") == "ping")
            await ResponderAsync(fluxo, new { tipo = "pong" }, ct);
    }
    cliente.Dispose();
    return codigo;
}

async Task PedirCanalAsync(TcpClient cliente, NetworkStream fluxo, JsonElement raiz, string remoto, CancellationToken ct)
{
    var codigo = Texto(raiz, "codigo") ?? "";
    var alvo = Texto(raiz, "alvo") ?? "controle";
    if (!registros.TryGetValue(codigo, out var pc))
    {
        await ResponderAsync(fluxo, new { tipo = "erro", mensagem = "Computador não está conectado ao servidor" }, ct);
        cliente.Dispose();
        return;
    }

    var canal = Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant();
    var pendente = new CanalPendente(cliente, fluxo);
    pendentes[canal] = pendente;

    try { await ResponderAsync(pc.Fluxo, new { tipo = "chamada", canal, alvo }, ct); }
    catch (Exception)
    {
        pendentes.TryRemove(canal, out _);
        await ResponderAsync(fluxo, new { tipo = "erro", mensagem = "Computador não respondeu" }, ct);
        cliente.Dispose();
        return;
    }

    Registrar($"{remoto} pediu canal {alvo} do PC {codigo}");
    // Daqui em diante quem cuida deste socket é EntregarCanalDoPcAsync.
    await pendente.Concluido.Task.WaitAsync(TimeSpan.FromSeconds(30), ct)
        .ContinueWith(_ => { }, TaskScheduler.Default);
}

async Task EntregarCanalDoPcAsync(TcpClient doPc, NetworkStream fluxoPc, JsonElement raiz, CancellationToken ct)
{
    var canal = Texto(raiz, "canal") ?? "";
    if (!pendentes.TryRemove(canal, out var pendente))
    {
        await ResponderAsync(fluxoPc, new { tipo = "erro", mensagem = "Canal expirado" }, ct);
        doPc.Dispose();
        return;
    }

    await ResponderAsync(fluxoPc, new { tipo = "pronto" }, ct);
    await ResponderAsync(pendente.Fluxo, new { tipo = "pronto" }, ct);
    pendente.Concluido.TrySetResult(true);

    using (doPc)
    using (pendente.Cliente)
    {
        var ida = CopiarAsync(pendente.Fluxo, fluxoPc, ct);
        var volta = CopiarAsync(fluxoPc, pendente.Fluxo, ct);
        await Task.WhenAny(ida, volta);
    }
}

static async Task CopiarAsync(Stream origem, Stream destino, CancellationToken ct)
{
    var buffer = ArrayPool<byte>.Shared.Rent(64 * 1024);
    try
    {
        while (true)
        {
            var lidos = await origem.ReadAsync(buffer, ct);
            if (lidos <= 0) return;
            await destino.WriteAsync(buffer.AsMemory(0, lidos), ct);
            await destino.FlushAsync(ct);
        }
    }
    catch (Exception) { /* uma ponta caiu: encerra o par */ }
    finally { ArrayPool<byte>.Shared.Return(buffer); }
}

async Task ResponderAsync(Stream destino, object valor, CancellationToken ct)
{
    var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(valor, json) + "\n");
    await destino.WriteAsync(bytes, ct);
    await destino.FlushAsync(ct);
}

static string? Texto(JsonElement raiz, string nome) =>
    raiz.TryGetProperty(nome, out var valor) && valor.ValueKind == JsonValueKind.String ? valor.GetString() : null;

static byte[] Resumo(string segredo) => SHA256.HashData(Encoding.UTF8.GetBytes(segredo));

static void Registrar(string mensagem) =>
    Console.WriteLine($"{DateTime.UtcNow:yyyy-MM-dd HH:mm:ss}Z  {mensagem}");

/// <summary>
/// Escuta em IPv6 com pilha dupla quando a máquina tem IPv6, e volta para IPv4
/// puro quando não tem — contêineres e VPS pequenas costumam vir sem IPv6, e o
/// servidor não pode simplesmente morrer nesses lugares.
/// </summary>
static TcpListener AbrirEscuta(int porta)
{
    if (Socket.OSSupportsIPv6)
    {
        try
        {
            var duplo = new TcpListener(IPAddress.IPv6Any, porta);
            duplo.Server.SetSocketOption(SocketOptionLevel.IPv6, SocketOptionName.IPv6Only, false);
            return duplo;
        }
        catch (SocketException ex)
        {
            Registrar($"IPv6 indisponível ({ex.SocketErrorCode}); escutando somente em IPv4.");
        }
    }
    return new TcpListener(IPAddress.Any, porta);
}

static int ResolverPorta(string[] args)
{
    if (args.Length > 0 && int.TryParse(args[0], out var doArgumento)) return doArgumento;
    var doAmbiente = Environment.GetEnvironmentVariable("PCFLOW_RELAY_PORTA");
    return int.TryParse(doAmbiente, out var valor) ? valor : 45460;
}

static async Task<string?> LerLinhaAsync(Stream origem, CancellationToken ct)
{
    using var memoria = new MemoryStream();
    var buffer = new byte[1];
    while (memoria.Length < 8192)
    {
        int lidos;
        try { lidos = await origem.ReadAsync(buffer, ct); }
        catch (Exception) { return null; }
        if (lidos == 0) return memoria.Length == 0 ? null : Encoding.UTF8.GetString(memoria.ToArray());
        if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(memoria.ToArray());
        if (buffer[0] != (byte)'\r') memoria.WriteByte(buffer[0]);
    }
    return null;
}

sealed record RegistroPc(string Codigo, byte[] ResumoSegredo, string Nome, TcpClient Controle, NetworkStream Fluxo);

sealed class CanalPendente(TcpClient cliente, NetworkStream fluxo)
{
    public TcpClient Cliente { get; } = cliente;
    public NetworkStream Fluxo { get; } = fluxo;
    public DateTime CriadoEm { get; } = DateTime.UtcNow;
    public TaskCompletionSource<bool> Concluido { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
    public void Desistir() { Concluido.TrySetResult(false); Cliente.Dispose(); }
}
