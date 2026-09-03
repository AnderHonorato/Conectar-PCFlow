using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace PCFlow.Windows.Core;

public sealed class ServidorPcFlow : IAsyncDisposable
{
    public const int PortaDescoberta = 45455;
    public const int PortaControle = 45456;
    public const int PortaTela = 45457;

    private readonly ArmazenamentoConfiguracao _armazenamento = new();
    private readonly ConfiguracaoPcFlow _configuracao;
    private readonly TlsIdentidade _tls = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly ConcurrentDictionary<string, SessaoAtiva> _sessoes = new();
    private readonly JsonSerializerOptions _json = new(JsonSerializerDefaults.Web);
    private TcpListener? _tcpControle;
    private TcpListener? _tcpTela;
    private UdpClient? _udp;
    private Task? _tarefaControle;
    private Task? _tarefaTela;
    private Task? _tarefaUdp;

    public string CodigoPareamento { get; private set; } = GerarPin();
    public string EnderecoLocal => ObterEnderecoLocal();
    public string MaquinaId => _configuracao.MaquinaId;
    public string ImpressaoTls => _tls.ImpressaoDigital;
    public IReadOnlyList<DispositivoAutorizado> Dispositivos => _configuracao.Dispositivos;
    public ConfiguracaoPcFlow Configuracao => _configuracao;
    public bool Ativo { get; private set; }
    public bool Pausado { get; private set; }

    public Func<SolicitacaoConexao, Task<bool>>? SolicitarAceiteAsync { get; set; }
    public Func<bool>? JanelaVisivel { get; set; }
    public event Action<string>? StatusAlterado;
    public event Action? DispositivosAlterados;
    public event Action<int>? SessoesAlteradas;

    public ServidorPcFlow() => _configuracao = _armazenamento.Carregar();

    public Task IniciarAsync()
    {
        if (Ativo) return Task.CompletedTask;
        Ativo = true;
        _tcpControle = new TcpListener(IPAddress.Any, PortaControle);
        _tcpTela = new TcpListener(IPAddress.Any, PortaTela);
        _tcpControle.Start();
        _tcpTela.Start();
        _udp = new UdpClient(PortaDescoberta) { EnableBroadcast = true };
        _tarefaControle = AceitarControleAsync(_cts.Token);
        _tarefaTela = AceitarTelaAsync(_cts.Token);
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
                if (!_configuracao.DescobertaRede) continue;
                var texto = Encoding.UTF8.GetString(pacote.Buffer);
                if (!texto.StartsWith("PCFLOW_DISCOVER_V2", StringComparison.Ordinal) &&
                    !texto.StartsWith("PCFLOW_DISCOVER_V1", StringComparison.Ordinal)) continue;
                var resposta = JsonSerializer.Serialize(new
                {
                    tipo = "pcflow",
                    nome = Environment.MachineName,
                    maquinaId = MaquinaId,
                    porta = PortaControle,
                    portaTela = PortaTela,
                    protocolo = 2,
                    tls = ImpressaoTls,
                    monitores = CapturaTela.QuantidadeMonitores
                }, _json);
                var bytes = Encoding.UTF8.GetBytes(resposta);
                await _udp.SendAsync(bytes, pacote.RemoteEndPoint, ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Descoberta: {ex.Message}"); }
        }
    }

    private async Task AceitarControleAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcpControle is not null)
        {
            try
            {
                var cliente = await _tcpControle.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => AtenderControleAsync(cliente, ct), ct);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { StatusAlterado?.Invoke($"Conexão: {ex.Message}"); }
        }
    }

    private async Task AtenderControleAsync(TcpClient cliente, CancellationToken ct)
    {
        using (cliente)
        {
            var remoto = (cliente.Client.RemoteEndPoint as IPEndPoint)?.Address;
            if (remoto is null || !EhRedeLocal(remoto)) return;
            cliente.NoDelay = true;

            using var ssl = new SslStream(cliente.GetStream(), false);
            try
            {
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _tls.Certificado,
                    ClientCertificateRequired = false,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
                }, ct);
            }
            catch { return; }

            MensagemRede? ola;
            try
            {
                var linha = await LerLinhaAsync(ssl, ct);
                ola = linha is null ? null : JsonSerializer.Deserialize<MensagemRede>(linha, _json);
            }
            catch { return; }

            if (ola is null || ola.Tipo != "ola" || string.IsNullOrWhiteSpace(ola.DispositivoId)) return;
            if (!string.IsNullOrWhiteSpace(ola.MaquinaId) && ola.MaquinaId != MaquinaId)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "ID da máquina não corresponde" }, ct);
                return;
            }
            if (!string.IsNullOrWhiteSpace(ola.Pin) && ola.Pin.Replace(" ", "") != CodigoPareamento)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Código de pareamento inválido" }, ct);
                return;
            }

            var conhecido = _configuracao.Dispositivos.FirstOrDefault(d => d.Id == ola.DispositivoId);
            if (conhecido?.Bloqueado == true)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Dispositivo bloqueado" }, ct);
                return;
            }

            var tokenValido = conhecido is not null && TokenIgual(conhecido.Token, ola.Token);
            var acessoNaoSupervisionado = SegurancaSenha.Verificar(ola.Senha ?? "", _configuracao.SenhaSalt, _configuracao.SenhaHash);
            var aceito = acessoNaoSupervisionado || await SolicitarPermissaoInterativaAsync(
                new SolicitacaoConexao(ola.DispositivoId, ola.Nome ?? "Android", remoto.ToString(), tokenValido));

            if (!aceito)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Conexão recusada no computador" }, ct);
                return;
            }

            if (conhecido is null || !tokenValido)
            {
                conhecido ??= new DispositivoAutorizado { Id = ola.DispositivoId, Nome = ola.Nome ?? "Android" };
                conhecido.Nome = ola.Nome ?? conhecido.Nome;
                conhecido.Token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
                conhecido.UltimaConexao = DateTime.UtcNow;
                if (!_configuracao.Dispositivos.Contains(conhecido)) _configuracao.Dispositivos.Add(conhecido);
            }
            else conhecido.UltimaConexao = DateTime.UtcNow;

            _armazenamento.Salvar(_configuracao);
            CodigoPareamento = GerarPin();
            DispositivosAlterados?.Invoke();

            var sessaoId = Convert.ToHexString(RandomNumberGenerator.GetBytes(24)).ToLowerInvariant();
            var sessao = new SessaoAtiva(sessaoId, conhecido.Id, conhecido.Nome, remoto.ToString(),
                _configuracao.PermitirTela, _configuracao.PermitirEntrada, _configuracao.PermitirClipboard,
                _configuracao.PermitirEnergia, _configuracao.PermitirArquivos);
            _sessoes[sessaoId] = sessao;
            SessoesAlteradas?.Invoke(_sessoes.Count);
            StatusAlterado?.Invoke($"{conhecido.Nome} conectado");

            await EscreverJsonAsync(ssl, new
            {
                tipo = "conectado",
                token = conhecido.Token,
                sessaoId,
                nome = Environment.MachineName,
                maquinaId = MaquinaId,
                portaTela = PortaTela,
                monitores = CapturaTela.DescreverMonitores(),
                permissoes = new { tela = sessao.PermitirTela, entrada = sessao.PermitirEntrada, clipboard = sessao.PermitirClipboard, energia = sessao.PermitirEnergia, arquivos = sessao.PermitirArquivos }
            }, ct);

            try
            {
                while (!ct.IsCancellationRequested && cliente.Connected)
                {
                    var linha = await LerLinhaAsync(ssl, ct);
                    if (linha is null) break;
                    var mensagem = JsonSerializer.Deserialize<MensagemRede>(linha, _json);
                    if (mensagem is null || Pausado) continue;
                    await ProcessarMensagemAsync(sessao, mensagem, ssl, ct);
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (Exception ex) { StatusAlterado?.Invoke($"Sessão: {ex.Message}"); }
            finally
            {
                _sessoes.TryRemove(sessaoId, out _);
                SessoesAlteradas?.Invoke(_sessoes.Count);
                StatusAlterado?.Invoke("Servidor ativo");
            }
        }
    }

    private async Task ProcessarMensagemAsync(SessaoAtiva sessao, MensagemRede m, SslStream ssl, CancellationToken ct)
    {
        if (m.Tipo == "clipboard_get" && sessao.PermitirClipboard)
        {
            await EscreverJsonAsync(ssl, new { tipo = "clipboard", texto = ClipboardPcFlow.LerTexto() }, ct);
            return;
        }
        if (m.Tipo == "clipboard_set" && sessao.PermitirClipboard)
        {
            ClipboardPcFlow.DefinirTexto(m.Texto ?? "");
            return;
        }

        var entrada = m.Tipo is "mouse_move" or "mouse_abs" or "mouse_click" or "mouse_down" or "mouse_up" or "scroll" or "texto" or "tecla" or "media";
        if (entrada && !sessao.PermitirEntrada) return;
        if (m.Tipo == "power" && !sessao.PermitirEnergia) return;
        ExecutorComandos.Executar(m);
    }

    private async Task<bool> SolicitarPermissaoInterativaAsync(SolicitacaoConexao solicitacao)
    {
        if (_configuracao.AcessoInterativo == "nunca") return false;
        if (_configuracao.AcessoInterativo == "janela" && !(JanelaVisivel?.Invoke() ?? false)) return false;
        if (SolicitarAceiteAsync is null) return false;
        try { return await SolicitarAceiteAsync(solicitacao); }
        catch { return false; }
    }

    private async Task AceitarTelaAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _tcpTela is not null)
        {
            try
            {
                var cliente = await _tcpTela.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => TransmitirTelaAsync(cliente, ct), ct);
            }
            catch (OperationCanceledException) { break; }
            catch { }
        }
    }

    private async Task TransmitirTelaAsync(TcpClient cliente, CancellationToken ct)
    {
        using (cliente)
        {
            var remoto = (cliente.Client.RemoteEndPoint as IPEndPoint)?.Address;
            if (remoto is null || !EhRedeLocal(remoto)) return;
            cliente.NoDelay = true;
            using var ssl = new SslStream(cliente.GetStream(), false);
            try
            {
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _tls.Certificado,
                    ClientCertificateRequired = false,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
                }, ct);
                var linha = await LerLinhaAsync(ssl, ct);
                var pedido = linha is null ? null : JsonSerializer.Deserialize<MensagemRede>(linha, _json);
                if (pedido is null || pedido.Tipo != "stream" || string.IsNullOrWhiteSpace(pedido.SessaoId) ||
                    !_sessoes.TryGetValue(pedido.SessaoId, out var sessao) || !sessao.PermitirTela) return;

                var fps = Math.Clamp(pedido.Fps, 2, 20);
                var qualidade = Math.Clamp(pedido.Qualidade, 35, 85);
                var monitor = Math.Clamp(pedido.Monitor, 0, Math.Max(0, CapturaTela.QuantidadeMonitores - 1));
                var cabecalho = new byte[4];

                while (!ct.IsCancellationRequested && cliente.Connected && _sessoes.ContainsKey(pedido.SessaoId))
                {
                    var quadro = CapturaTela.CapturarJpeg(monitor, qualidade);
                    if (quadro.Length == 0 || quadro.Length > 16_000_000) break;
                    BinaryPrimitives.WriteInt32BigEndian(cabecalho, quadro.Length);
                    await ssl.WriteAsync(cabecalho, ct);
                    await ssl.WriteAsync(quadro, ct);
                    await ssl.FlushAsync(ct);
                    await Task.Delay(1000 / fps, ct);
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch { }
        }
    }

    public void AlternarPausa()
    {
        Pausado = !Pausado;
        StatusAlterado?.Invoke(Pausado ? "Servidor pausado" : "Servidor ativo");
    }

    public void SalvarConfiguracao() => _armazenamento.Salvar(_configuracao);

    public bool DefinirSenhaNaoSupervisionada(string senha)
    {
        if (senha.Length < 8) return false;
        var (salt, hash) = SegurancaSenha.Criar(senha);
        _configuracao.SenhaSalt = salt;
        _configuracao.SenhaHash = hash;
        _armazenamento.Salvar(_configuracao);
        return true;
    }

    public void RemoverSenhaNaoSupervisionada()
    {
        _configuracao.SenhaSalt = null;
        _configuracao.SenhaHash = null;
        _armazenamento.Salvar(_configuracao);
    }

    public void GerarNovoCodigo() => CodigoPareamento = GerarPin();

    public async ValueTask DisposeAsync()
    {
        _cts.Cancel();
        _tcpControle?.Stop();
        _tcpTela?.Stop();
        _udp?.Dispose();
        foreach (var t in new[] { _tarefaControle, _tarefaTela, _tarefaUdp }) if (t is not null) try { await t; } catch { }
        _tls.Certificado.Dispose();
        _cts.Dispose();
    }

    private async Task EscreverJsonAsync(Stream stream, object valor, CancellationToken ct)
    {
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(valor, _json) + "\n");
        await stream.WriteAsync(bytes, ct);
        await stream.FlushAsync(ct);
    }

    private static async Task<string?> LerLinhaAsync(Stream stream, CancellationToken ct)
    {
        using var ms = new MemoryStream();
        var buffer = new byte[1];
        while (ms.Length < 131_072)
        {
            var lidos = await stream.ReadAsync(buffer, ct);
            if (lidos == 0) return ms.Length == 0 ? null : Encoding.UTF8.GetString(ms.ToArray());
            if (buffer[0] == (byte)'\n') return Encoding.UTF8.GetString(ms.ToArray());
            if (buffer[0] != (byte)'\r') ms.WriteByte(buffer[0]);
        }
        throw new InvalidDataException("Mensagem de controle muito grande");
    }

    private static bool TokenIgual(string esperado, string? recebido)
    {
        if (string.IsNullOrEmpty(recebido)) return false;
        var a = Encoding.UTF8.GetBytes(esperado);
        var b = Encoding.UTF8.GetBytes(recebido);
        return a.Length == b.Length && CryptographicOperations.FixedTimeEquals(a, b);
    }

    private static bool EhRedeLocal(IPAddress endereco)
    {
        if (IPAddress.IsLoopback(endereco) || endereco.IsIPv6LinkLocal) return true;
        if (endereco.IsIPv4MappedToIPv6) endereco = endereco.MapToIPv4();
        if (endereco.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = endereco.GetAddressBytes();
        return b[0] == 10 || b[0] == 127 || (b[0] == 192 && b[1] == 168) ||
               (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 169 && b[1] == 254);
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
