using System.Buffers.Binary;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;

namespace PCFlow.Windows.Core;

/// <summary>
/// O lado cliente: usa este PC para ver e controlar outro computador com PCFlow.
///
/// É o mesmo protocolo que o aplicativo Android fala, inclusive a pinagem do
/// certificado por SHA-256, então o PC entra na sessão do mesmo jeito e com as
/// mesmas garantias que o celular.
/// </summary>
public sealed class ClientePcFlow : IAsyncDisposable
{
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    private CancellationTokenSource? _cts;
    private SslStream? _controle;
    private TcpClient? _tcpControle;
    private Task? _laco;
    private Task? _tela;

    public bool Conectado { get; private set; }
    public string? SessaoId { get; private set; }
    public string NomeRemoto { get; private set; } = "";
    public int QuantidadeMonitores { get; private set; } = 1;
    public PermissoesEfetivas Permissoes { get; private set; } = new(true, true, true, true, true);

    /// <summary>Um quadro JPEG da tela remota, pronto para desenhar.</summary>
    public event Action<byte[]>? QuadroRecebido;
    public event Action<string>? Status;
    public event Action<string>? Encerrado;

    // ---------- descoberta ----------

    /// <summary>Procura na rede local outros PCs rodando o PCFlow.</summary>
    public static async Task<IReadOnlyList<PcRemoto>> DescobrirAsync(CancellationToken ct = default)
    {
        var achados = new Dictionary<string, PcRemoto>();
        using var udp = new UdpClient { EnableBroadcast = true };
        var pedido = Encoding.UTF8.GetBytes("PCFLOW_DISCOVER_V2");

        foreach (var destino in EnderecosDeBroadcast())
        {
            try { await udp.SendAsync(pedido, new IPEndPoint(destino, ServidorPcFlow.PortaDescoberta), ct); }
            catch (SocketException) { }
        }

        using var prazo = CancellationTokenSource.CreateLinkedTokenSource(ct);
        prazo.CancelAfter(TimeSpan.FromSeconds(2));
        try
        {
            while (!prazo.IsCancellationRequested)
            {
                var resposta = await udp.ReceiveAsync(prazo.Token);
                var texto = Encoding.UTF8.GetString(resposta.Buffer);
                using var doc = JsonDocument.Parse(texto);
                var raiz = doc.RootElement;
                if (!raiz.TryGetProperty("tipo", out var tipo) || tipo.GetString() != "pcflow") continue;

                var pc = new PcRemoto(
                    Nome: Ler(raiz, "nome") ?? resposta.RemoteEndPoint.Address.ToString(),
                    Host: resposta.RemoteEndPoint.Address.ToString(),
                    Porta: LerInt(raiz, "porta", ServidorPcFlow.PortaControle),
                    PortaTela: LerInt(raiz, "portaTela", ServidorPcFlow.PortaTela),
                    MaquinaId: Ler(raiz, "maquinaId") ?? "",
                    ImpressaoTls: Ler(raiz, "tls") ?? "",
                    Monitores: Math.Max(1, LerInt(raiz, "monitores", 1)));

                // Não faz sentido listar o próprio PC como destino.
                if (pc.MaquinaId == new ArmazenamentoConfiguracao().Carregar().MaquinaId) continue;
                achados[pc.MaquinaId.Length > 0 ? pc.MaquinaId : pc.Host] = pc;
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception) { }
        return achados.Values.OrderBy(p => p.Nome, StringComparer.CurrentCultureIgnoreCase).ToList();
    }

    /// <summary>Monta o destino a partir de um código de acesso copiado do outro PC.</summary>
    public static PcRemoto? DoCodigo(string codigo)
    {
        var destino = CodigoAcesso.Ler(codigo);
        if (destino is null || destino.ViaServidor) return null;
        return new PcRemoto($"PC em {destino.Host}", destino.Host, destino.Porta,
            ServidorPcFlow.PortaTela, "", destino.ImpressaoTls, 1);
    }

    // ---------- sessão ----------

    public async Task<bool> ConectarAsync(PcRemoto pc, string? pin, string? senha, CancellationToken ct = default)
    {
        await DesconectarAsync();
        _cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        var token = _cts.Token;

        try
        {
            _tcpControle = new TcpClient { NoDelay = true };
            await _tcpControle.ConnectAsync(pc.Host, pc.Porta, token);
            _controle = await AbrirTlsAsync(_tcpControle, pc.ImpressaoTls, token);

            var identidade = IdentidadeDesteComputador();
            var ola = new Dictionary<string, object?>
            {
                ["tipo"] = "ola",
                ["dispositivoId"] = identidade,
                ["maquinaId"] = pc.MaquinaId,
                ["nome"] = $"{Environment.MachineName} (PC)",
                ["appVersao"] = VersaoPcFlow.App
            };
            if (!string.IsNullOrWhiteSpace(pin)) ola["pin"] = pin.Replace(" ", "");
            if (!string.IsNullOrWhiteSpace(senha)) ola["senha"] = senha;
            await EscreverAsync(_controle, ola, token);

            var resposta = await LerLinhaAsync(_controle, token)
                ?? throw new IOException("O outro computador fechou a conexão antes de responder");
            using var doc = JsonDocument.Parse(resposta);
            var raiz = doc.RootElement;
            if (Ler(raiz, "tipo") != "conectado")
                throw new InvalidOperationException(Ler(raiz, "mensagem") ?? "Conexão recusada");

            SessaoId = Ler(raiz, "sessaoId");
            NomeRemoto = Ler(raiz, "nome") ?? pc.Nome;
            QuantidadeMonitores = raiz.TryGetProperty("monitores", out var monitores) && monitores.ValueKind == JsonValueKind.Array
                ? Math.Max(1, monitores.GetArrayLength())
                : pc.Monitores;
            Permissoes = LerPermissoes(raiz);
            Conectado = true;
            Status?.Invoke($"Conectado a {NomeRemoto}");

            _laco = OuvirAsync(token);
            if (Permissoes.Tela) _tela = ReceberTelaAsync(pc, SessaoId!, 0, token);
            return true;
        }
        catch (Exception ex)
        {
            await DesconectarAsync();
            Status?.Invoke(Explicar(ex));
            Encerrado?.Invoke(Explicar(ex));
            return false;
        }
    }

    private async Task OuvirAsync(CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested && _controle is not null)
            {
                var linha = await LerLinhaAsync(_controle, ct);
                if (linha is null) break;
                using var doc = JsonDocument.Parse(linha);
                if (Ler(doc.RootElement, "tipo") == "erro")
                    Status?.Invoke(Ler(doc.RootElement, "mensagem") ?? "erro");
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception) { }
        finally
        {
            if (Conectado)
            {
                Conectado = false;
                Encerrado?.Invoke("Sessão encerrada pelo outro computador");
            }
        }
    }

    /// <summary>Troca o monitor exibido, reabrindo o canal de tela.</summary>
    public async Task TrocarMonitorAsync(PcRemoto pc, int indice)
    {
        if (!Conectado || SessaoId is null || _cts is null) return;
        var novo = Math.Clamp(indice, 0, QuantidadeMonitores - 1);
        _tela = ReceberTelaAsync(pc, SessaoId, novo, _cts.Token);
        await Task.CompletedTask;
    }

    private async Task ReceberTelaAsync(PcRemoto pc, string sessaoId, int monitor, CancellationToken ct)
    {
        try
        {
            using var tcp = new TcpClient { NoDelay = true };
            await tcp.ConnectAsync(pc.Host, pc.PortaTela, ct);
            await using var ssl = await AbrirTlsAsync(tcp, pc.ImpressaoTls, ct);

            await EscreverAsync(ssl, new Dictionary<string, object?>
            {
                ["tipo"] = "stream",
                ["sessaoId"] = sessaoId,
                ["monitor"] = monitor,
                ["fps"] = 15,
                ["qualidade"] = 72
            }, ct);

            var cabecalho = new byte[4];
            while (!ct.IsCancellationRequested)
            {
                await LerExatoAsync(ssl, cabecalho, ct);
                var tamanho = BinaryPrimitives.ReadInt32BigEndian(cabecalho);
                if (tamanho <= 0 || tamanho > 16_000_000) break;
                var quadro = new byte[tamanho];
                await LerExatoAsync(ssl, quadro, ct);
                QuadroRecebido?.Invoke(quadro);
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { Status?.Invoke($"Tela remota: {Explicar(ex)}"); }
    }

    // ---------- comandos ----------

    public void Posicionar(double x, double y, int monitor) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "mouse_abs", ["x"] = x, ["y"] = y, ["monitor"] = monitor });

    public void Clicar(string botao) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "mouse_click", ["botao"] = botao });

    public void BotaoPressionado(string botao) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "mouse_down", ["botao"] = botao });

    public void BotaoSolto(string botao) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "mouse_up", ["botao"] = botao });

    public void Rolar(int delta) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "scroll", ["delta"] = delta });

    public void EnviarTexto(string texto) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "texto", ["texto"] = texto });

    public void EnviarTecla(string tecla) =>
        Enviar(new Dictionary<string, object?> { ["tipo"] = "tecla", ["tecla"] = tecla });

    private void Enviar(Dictionary<string, object?> mensagem)
    {
        if (!Conectado || _controle is null || _cts is null) return;
        var fluxo = _controle;
        var token = _cts.Token;
        _ = Task.Run(async () =>
        {
            try { await EscreverAsync(fluxo, mensagem, token); }
            catch (Exception) { /* a queda aparece no laço de leitura */ }
        }, token);
    }

    public async Task DesconectarAsync()
    {
        Conectado = false;
        if (_cts is not null) { await _cts.CancelAsync(); }
        foreach (var tarefa in new[] { _laco, _tela }) if (tarefa is not null) { try { await tarefa; } catch { } }
        _laco = null; _tela = null;
        if (_controle is not null) { await _controle.DisposeAsync(); _controle = null; }
        _tcpControle?.Dispose(); _tcpControle = null;
        _cts?.Dispose(); _cts = null;
        SessaoId = null;
    }

    // ---------- transporte ----------

    /// <summary>
    /// Abre o TLS exigindo que o certificado do outro PC bata com a impressão
    /// digital que veio da descoberta ou do código de acesso. Sem isso, conectar
    /// pela internet seria um convite a quem estivesse no caminho.
    /// </summary>
    private static async Task<SslStream> AbrirTlsAsync(TcpClient tcp, string impressaoEsperada, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(impressaoEsperada))
            throw new InvalidOperationException(
                "Identidade do outro computador desconhecida. Atualize a busca na rede ou use o código de acesso.");

        var ssl = new SslStream(tcp.GetStream(), false, (_, certificado, _, _) =>
        {
            if (certificado is null) return false;
            var atual = Convert.ToHexString(SHA256.HashData(certificado.GetRawCertData())).ToLowerInvariant();
            return CodigoAcesso.ImpressaoConfere(atual, impressaoEsperada);
        });

        try
        {
            await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
            {
                TargetHost = "PCFlow",
                // None = usa o melhor protocolo que este Windows tem, sem fixar
                // uma versão que a máquina possa não suportar.
                EnabledSslProtocols = System.Security.Authentication.SslProtocols.None
            }, ct);
        }
        catch (Exception)
        {
            await ssl.DisposeAsync();
            throw;
        }
        return ssl;
    }

    private static PermissoesEfetivas LerPermissoes(JsonElement raiz)
    {
        if (!raiz.TryGetProperty("permissoes", out var p) || p.ValueKind != JsonValueKind.Object)
            return new PermissoesEfetivas(true, true, true, true, false);
        bool Flag(string nome, bool padrao) =>
            p.TryGetProperty(nome, out var valor) && valor.ValueKind is JsonValueKind.True or JsonValueKind.False
                ? valor.GetBoolean() : padrao;
        return new PermissoesEfetivas(Flag("tela", true), Flag("entrada", true), Flag("clipboard", true),
            Flag("energia", true), Flag("arquivos", false));
    }

    /// <summary>Identificador estável deste PC como cliente do outro.</summary>
    private static string IdentidadeDesteComputador()
    {
        var configuracao = new ArmazenamentoConfiguracao().Carregar();
        return $"pc-{configuracao.MaquinaId}";
    }

    private static IEnumerable<IPAddress> EnderecosDeBroadcast()
    {
        yield return IPAddress.Broadcast;
        foreach (var interfaceRede in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
        {
            if (interfaceRede.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up) continue;
            foreach (var endereco in interfaceRede.GetIPProperties().UnicastAddresses)
            {
                if (endereco.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                if (IPAddress.IsLoopback(endereco.Address)) continue;
                var ip = endereco.Address.GetAddressBytes();
                var mascara = endereco.IPv4Mask?.GetAddressBytes();
                if (mascara is null || mascara.Length != 4) continue;
                var difusao = new byte[4];
                for (var i = 0; i < 4; i++) difusao[i] = (byte)(ip[i] | ~mascara[i]);
                yield return new IPAddress(difusao);
            }
        }
    }

    private static string Explicar(Exception ex) => ex switch
    {
        SocketException s when s.SocketErrorCode == SocketError.TimedOut =>
            "O outro computador não respondeu. Confira se o PCFlow está aberto lá e se o firewall liberou as portas.",
        SocketException s when s.SocketErrorCode == SocketError.ConnectionRefused =>
            "O outro computador recusou a conexão. O servidor do PCFlow provavelmente está parado lá.",
        SocketException =>
            "Não consegui alcançar o outro computador nesta rede.",
        System.Security.Authentication.AuthenticationException =>
            "A identidade do outro computador não confere com o código usado. Peça um código novo.",
        IOException => "A conexão caiu no meio da sessão.",
        _ => ex.Message
    };

    private static string? Ler(JsonElement raiz, string nome) =>
        raiz.TryGetProperty(nome, out var valor) && valor.ValueKind == JsonValueKind.String ? valor.GetString() : null;

    private static int LerInt(JsonElement raiz, string nome, int padrao) =>
        raiz.TryGetProperty(nome, out var valor) && valor.TryGetInt32(out var numero) ? numero : padrao;

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
        while (memoria.Length < 131_072)
        {
            var lidos = await origem.ReadAsync(buffer, ct);
            if (lidos == 0) return memoria.Length == 0 ? null : Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(memoria.ToArray());
            if (buffer[0] != (byte)'\r') memoria.WriteByte(buffer[0]);
        }
        throw new InvalidDataException("Mensagem muito grande");
    }

    private static async Task LerExatoAsync(Stream origem, byte[] destino, CancellationToken ct)
    {
        var lidos = 0;
        while (lidos < destino.Length)
        {
            var agora = await origem.ReadAsync(destino.AsMemory(lidos), ct);
            if (agora == 0) throw new IOException("A transmissão da tela terminou");
            lidos += agora;
        }
    }

    public async ValueTask DisposeAsync() => await DesconectarAsync();
}

/// <summary>Um PC alcançável: veio da busca na rede ou de um código de acesso.</summary>
public sealed record PcRemoto(
    string Nome,
    string Host,
    int Porta,
    int PortaTela,
    string MaquinaId,
    string ImpressaoTls,
    int Monitores)
{
    public string Resumo => $"{Nome} — {Host}";
}
