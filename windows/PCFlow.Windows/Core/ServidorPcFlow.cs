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
    public const int PortaArquivos = ServidorArquivosPcFlow.Porta;

    /// <summary>Todas as portas TCP que precisam chegar até este PC.</summary>
    public static readonly int[] PortasTcp = [PortaControle, PortaTela, PortaArquivos];

    private readonly ArmazenamentoConfiguracao _armazenamento = new();
    private readonly ConfiguracaoPcFlow _configuracao;
    private readonly TlsIdentidade _tls = new();
    private CancellationTokenSource _cts = new();
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

    /// <summary>IP público apurado na última tentativa de abrir acesso externo.</summary>
    public string? IpPublico { get; private set; }

    /// <summary>
    /// Código para conectar de qualquer lugar: leva destino, porta e a identidade
    /// TLS deste PC, então a pinagem continua valendo fora da rede local.
    /// </summary>
    public string CodigoAcessoExterno =>
        IPAddress.TryParse(IpPublico, out var ip)
            ? CodigoAcesso.GerarDireto(ip, PortaControle, ImpressaoTls)
            : "";

    /// <summary>O mesmo código, mas para a rede local — serve de atalho sem QR.</summary>
    public string CodigoAcessoLocal =>
        IPAddress.TryParse(EnderecoLocal, out var ip)
            ? CodigoAcesso.GerarDireto(ip, PortaControle, ImpressaoTls)
            : "";

    public Func<SolicitacaoConexao, Task<bool>>? SolicitarAceiteAsync { get; set; }
    public Func<bool>? JanelaVisivel { get; set; }
    public event Action<string>? StatusAlterado;
    public event Action? DispositivosAlterados;
    public event Action<int>? SessoesAlteradas;

    public ServidorPcFlow() => _configuracao = _armazenamento.Carregar();

    public Task IniciarAsync()
    {
        if (Ativo) return Task.CompletedTask;
        // O token é recriado a cada início: sem isto, parar o servidor uma vez
        // deixava o cancelamento preso para sempre e "Reiniciar" não voltava.
        if (_cts.IsCancellationRequested)
        {
            _cts.Dispose();
            _cts = new CancellationTokenSource();
        }
        try
        {
            _tcpControle = new TcpListener(IPAddress.Any, PortaControle);
            _tcpTela = new TcpListener(IPAddress.Any, PortaTela);
            _tcpControle.Start();
            _tcpTela.Start();
            _udp = new UdpClient(PortaDescoberta) { EnableBroadcast = true };
            Ativo = true;
            _tarefaControle = AceitarControleAsync(_cts.Token);
            _tarefaTela = AceitarTelaAsync(_cts.Token);
            _tarefaUdp = ResponderDescobertaAsync(_cts.Token);
            StatusAlterado?.Invoke("Servidor ativo");
        }
        catch (SocketException ex) when (ex.SocketErrorCode == SocketError.AddressAlreadyInUse)
        {
            Ativo = false;
            _tcpControle?.Stop();
            _tcpTela?.Stop();
            _udp?.Dispose();
            StatusAlterado?.Invoke("Outra instância do PCFlow já está ativa. Saia pelo ícone da bandeja e abra esta versão novamente.");
        }
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
                    portaArquivos = PortaArquivos,
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
            var origem = ClassificarOrigem(remoto);
            if (origem is null)
            {
                StatusAlterado?.Invoke(
                    "Conexão recusada: origem fora da rede local. Ligue \"Aceitar conexões de fora da rede\" em Acesso remoto para permitir.");
                return;
            }
            cliente.NoDelay = true;
            cliente.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.KeepAlive, true);
            await AtenderControleFluxoAsync(cliente.GetStream(), origem, () => cliente.Connected, ct);
        }
    }

    /// <summary>
    /// Atende uma sessão de controle sobre qualquer transporte já estabelecido.
    /// É o que permite a mesma sessão chegar por TCP direto ou por um canal
    /// vindo do servidor de retransmissão, sem duplicar a lógica.
    /// </summary>
    public async Task AtenderControleFluxoAsync(Stream transporte, OrigemConexao origem,
        Func<bool> conectado, CancellationToken ct)
    {
        {
            var remoto = origem.Descricao;
            using var ssl = new SslStream(transporte, false);
            try
            {
                var negociado = await TlsPcFlow.AutenticarServidorAsync(ssl, _tls.Certificado, ct);
                StatusAlterado?.Invoke($"Canal seguro com {remoto} ({negociado})");
            }
            catch (Exception ex)
            {
                StatusAlterado?.Invoke($"Falha TLS com {remoto}: {TlsPcFlow.ExplicarFalha(ex)}");
                return;
            }

            MensagemRede? ola;
            try
            {
                var linha = await LerLinhaAsync(ssl, ct);
                ola = linha is null ? null : JsonSerializer.Deserialize<MensagemRede>(linha, _json);
            }
            catch (Exception ex)
            {
                StatusAlterado?.Invoke($"Falha no início da sessão: {ex.Message}");
                return;
            }

            if (ola is null || ola.Tipo != "ola" || string.IsNullOrWhiteSpace(ola.DispositivoId))
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Solicitação de conexão inválida" }, ct);
                return;
            }
            if (!string.IsNullOrWhiteSpace(ola.MaquinaId) && ola.MaquinaId != MaquinaId)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "ID da máquina não corresponde" }, ct);
                return;
            }

            // Misturar APK de uma versão com EXE de outra causava "connection closed"
            // sem explicação. Agora a incompatibilidade é dita com todas as letras.
            if (!string.IsNullOrWhiteSpace(ola.AppVersao) && ola.AppVersao != VersaoPcFlow.App)
            {
                await EscreverJsonAsync(ssl, new
                {
                    tipo = "erro",
                    mensagem = $"Versões diferentes: o PC é {VersaoPcFlow.App} e o celular é {ola.AppVersao}. " +
                               "Instale o APK que acompanha esta versão do PCFlow."
                }, ct);
                StatusAlterado?.Invoke($"Recusado: celular na versão {ola.AppVersao}, PC na {VersaoPcFlow.App}");
                return;
            }

            // Fora da rede local a exigência sobe: só entra quem sabe a senha de
            // acesso não supervisionado. Sem senha definida, nem tenta — é o que
            // impede alguém de varrer a internet e cair na tela do PC.
            if (!origem.RedeLocal)
            {
                if (!_configuracao.PermitirAcessoExterno)
                {
                    await EscreverJsonAsync(ssl, new
                    {
                        tipo = "erro",
                        mensagem = "Este PC só aceita conexões da rede local. Ligue \"Aceitar conexões de fora da rede\" no PCFlow do computador."
                    }, ct);
                    return;
                }
                if (string.IsNullOrEmpty(_configuracao.SenhaHash))
                {
                    await EscreverJsonAsync(ssl, new
                    {
                        tipo = "erro",
                        mensagem = "Para entrar de fora da rede é preciso definir uma senha de acesso em Segurança, no PCFlow do computador."
                    }, ct);
                    StatusAlterado?.Invoke($"Recusado {origem.Descricao}: acesso externo sem senha definida");
                    return;
                }
                if (!SegurancaSenha.Verificar(ola.Senha ?? "", _configuracao.SenhaSalt, _configuracao.SenhaHash))
                {
                    await EscreverJsonAsync(ssl, new
                    {
                        tipo = "erro",
                        mensagem = "Senha de acesso incorreta. Conexões de fora da rede local sempre exigem a senha."
                    }, ct);
                    StatusAlterado?.Invoke($"Recusado {origem.Descricao}: senha de acesso externo incorreta");
                    await Task.Delay(1500, ct); // atrasa quem fica tentando na sorte
                    return;
                }
            }

            var informouPin = !string.IsNullOrWhiteSpace(ola.Pin);
            var pinValido = informouPin && ola.Pin!.Replace(" ", "") == CodigoPareamento;
            if (informouPin && !pinValido)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Código de pareamento inválido ou expirado" }, ct);
                return;
            }

            var conhecido = _configuracao.Dispositivos.FirstOrDefault(d => d.Id == ola.DispositivoId);
            if (conhecido?.Bloqueado == true)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Dispositivo bloqueado neste computador" }, ct);
                return;
            }
            if (conhecido is not null && !origem.RedeLocal && !conhecido.PermitirForaDaRede)
            {
                await EscreverJsonAsync(ssl, new
                {
                    tipo = "erro",
                    mensagem = $"{conhecido.Nome} só pode conectar pela rede local. Libere o acesso externo deste dispositivo em Dispositivos, no PC."
                }, ct);
                return;
            }

            var tokenValido = conhecido is not null && TokenIgual(conhecido.Token, ola.Token);
            var acessoNaoSupervisionado = SegurancaSenha.Verificar(ola.Senha ?? "", _configuracao.SenhaSalt, _configuracao.SenhaHash);

            // QR Code ou PIN válido já são uma confirmação local de posse do código exibido no PC.
            // Solicitação por ID/descoberta continua exigindo aceite, a menos que a senha não supervisionada seja válida.
            var aceito = pinValido || acessoNaoSupervisionado;
            if (!aceito)
            {
                StatusAlterado?.Invoke($"Solicitação de {ola.Nome ?? "Android"} aguardando aceite");
                aceito = await SolicitarPermissaoInterativaAsync(
                    new SolicitacaoConexao(ola.DispositivoId, ola.Nome ?? "Android", origem.Descricao, tokenValido));
            }

            if (!aceito)
            {
                await EscreverJsonAsync(ssl, new { tipo = "erro", mensagem = "Conexão recusada ou solicitação não aceita no computador" }, ct);
                return;
            }

            if (conhecido is null || !tokenValido)
            {
                conhecido ??= new DispositivoAutorizado { Id = ola.DispositivoId, Nome = ola.Nome ?? "Android" };
                conhecido.Nome = ola.Nome ?? conhecido.Nome;
                conhecido.Token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
                conhecido.Bloqueado = false;
                conhecido.UltimaConexao = DateTime.UtcNow;
                if (!_configuracao.Dispositivos.Contains(conhecido)) _configuracao.Dispositivos.Add(conhecido);
            }
            else conhecido.UltimaConexao = DateTime.UtcNow;
            conhecido.UltimaOrigem = origem.Rotulo;

            _armazenamento.Salvar(_configuracao);
            if (pinValido) CodigoPareamento = GerarPin();
            DispositivosAlterados?.Invoke();

            // As permissões do dispositivo mandam quando ele tem regra própria;
            // fora isso valem as gerais do PC.
            var permitido = conhecido.Resolver(_configuracao);
            var sessaoId = Convert.ToHexString(RandomNumberGenerator.GetBytes(24)).ToLowerInvariant();
            var sessao = new SessaoAtiva(sessaoId, conhecido.Id, conhecido.Nome, origem.Descricao,
                permitido.Tela, permitido.Entrada, permitido.Clipboard,
                permitido.Energia, permitido.Arquivos);
            _sessoes[sessaoId] = sessao;
            SessoesAlteradas?.Invoke(_sessoes.Count);
            StatusAlterado?.Invoke($"{conhecido.Nome} conectado ({origem.Rotulo})");

            await EscreverJsonAsync(ssl, new
            {
                tipo = "conectado",
                token = conhecido.Token,
                sessaoId,
                nome = Environment.MachineName,
                maquinaId = MaquinaId,
                portaTela = PortaTela,
                portaArquivos = PortaArquivos,
                monitores = CapturaTela.DescreverMonitores(),
                permissoes = new
                {
                    tela = sessao.PermitirTela,
                    entrada = sessao.PermitirEntrada,
                    clipboard = sessao.PermitirClipboard,
                    energia = sessao.PermitirEnergia,
                    arquivos = sessao.PermitirArquivos
                }
            }, ct);

            try
            {
                while (!ct.IsCancellationRequested && conectado())
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
        if (m.Tipo == "ping")
        {
            await EscreverJsonAsync(ssl, new { tipo = "pong", t = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() }, ct);
            return;
        }
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
            var origem = ClassificarOrigem((cliente.Client.RemoteEndPoint as IPEndPoint)?.Address);
            if (origem is null) return;
            cliente.NoDelay = true;
            await TransmitirTelaFluxoAsync(cliente.GetStream(), () => cliente.Connected, ct);
        }
    }

    /// <summary>Transmite a tela sobre qualquer transporte já estabelecido.</summary>
    public async Task TransmitirTelaFluxoAsync(Stream transporte, Func<bool> conectado, CancellationToken ct)
    {
        {
            using var ssl = new SslStream(transporte, false);
            try
            {
                await TlsPcFlow.AutenticarServidorAsync(ssl, _tls.Certificado, ct);
                var linha = await LerLinhaAsync(ssl, ct);
                var pedido = linha is null ? null : JsonSerializer.Deserialize<MensagemRede>(linha, _json);
                if (pedido is null || pedido.Tipo != "stream" || string.IsNullOrWhiteSpace(pedido.SessaoId) ||
                    !_sessoes.TryGetValue(pedido.SessaoId, out var sessao) || !sessao.PermitirTela) return;

                var fps = Math.Clamp(pedido.Fps, 2, 30);
                var qualidade = Math.Clamp(pedido.Qualidade, 35, 85);
                var monitor = Math.Clamp(pedido.Monitor, 0, Math.Max(0, CapturaTela.QuantidadeMonitores - 1));
                var larguraMaxima = Math.Max(0, pedido.LarguraMaxima);
                var cabecalho = new byte[4];

                // O ritmo sai do relógio, não da soma das esperas: capturar e
                // comprimir já gastam parte do orçamento do quadro, então dormir
                // 1000/fps depois disso deixava o intervalo real sempre maior que
                // o pedido — 24 fps viravam 17 na prática.
                var orcamento = TimeSpan.FromSeconds(1.0 / fps);
                var intervaloDeVida = TimeSpan.FromSeconds(1);
                var relogio = System.Diagnostics.Stopwatch.StartNew();
                var prazo = relogio.Elapsed;
                var ultimoEnvio = relogio.Elapsed;
                byte[]? anterior = null;

                while (!ct.IsCancellationRequested && conectado() && _sessoes.ContainsKey(pedido.SessaoId))
                {
                    var quadro = CapturaTela.CapturarJpeg(monitor, qualidade, larguraMaxima);
                    if (quadro.Length == 0 || quadro.Length > 16_000_000) break;

                    // Tela parada é o caso comum (ler, escrever, apresentar) e
                    // reenviar o mesmo JPEG só gasta a rede, que é o gargalo no
                    // Wi-Fi ruim. Comparar os bytes custa muito menos que enviá-los.
                    // Ainda assim vai um quadro por segundo: sem ele o celular não
                    // distingue tela parada de conexão morta.
                    var repetido = anterior is not null && quadro.AsSpan().SequenceEqual(anterior);
                    if (!repetido || relogio.Elapsed - ultimoEnvio >= intervaloDeVida)
                    {
                        BinaryPrimitives.WriteInt32BigEndian(cabecalho, quadro.Length);
                        await ssl.WriteAsync(cabecalho, ct);
                        await ssl.WriteAsync(quadro, ct);
                        await ssl.FlushAsync(ct);
                        ultimoEnvio = relogio.Elapsed;
                    }
                    anterior = quadro;

                    prazo += orcamento;
                    var espera = prazo - relogio.Elapsed;
                    if (espera > TimeSpan.Zero) await Task.Delay(espera, ct);
                    else prazo = relogio.Elapsed; // estourou o orçamento: recomeça a contagem em vez de acumular dívida
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (Exception ex) { StatusAlterado?.Invoke($"Tela remota: {ex.Message}"); }
        }
    }

    /// <summary>Encerra as escutas mantendo a configuração; pode ser religado depois.</summary>
    public async Task PararAsync()
    {
        if (!Ativo) return;
        Ativo = false;
        await _cts.CancelAsync();
        try { _tcpControle?.Stop(); } catch (SocketException) { }
        try { _tcpTela?.Stop(); } catch (SocketException) { }
        _udp?.Dispose();
        _tcpControle = null; _tcpTela = null; _udp = null;
        foreach (var t in new[] { _tarefaControle, _tarefaTela, _tarefaUdp })
            if (t is not null) { try { await t; } catch { } }
        _tarefaControle = null; _tarefaTela = null; _tarefaUdp = null;
        _sessoes.Clear();
        SessoesAlteradas?.Invoke(0);
        StatusAlterado?.Invoke("Servidor parado");
    }

    public async Task ReiniciarAsync()
    {
        await PararAsync();
        await Task.Delay(250);
        await IniciarAsync();
    }

    public void AlternarPausa()
    {
        Pausado = !Pausado;
        StatusAlterado?.Invoke(Pausado ? "Servidor pausado" : "Servidor ativo");
    }

    public void SalvarConfiguracao() => _armazenamento.Salvar(_configuracao);

    private readonly AcessoRemoto _acessoExterno = new();

    /// <summary>
    /// Prepara o acesso de fora da rede: descobre o IP público e, se permitido,
    /// pede ao roteador para encaminhar as portas do PCFlow.
    /// </summary>
    public async Task<ResultadoAcessoExterno> PrepararAcessoExternoAsync(CancellationToken ct = default)
    {
        var resultado = _configuracao.AbrirPortasUpnp
            ? await _acessoExterno.AbrirAsync(PortasTcp, ct)
            : new ResultadoAcessoExterno(false, await _acessoExterno.ObterIpPublicoAsync(ct), null, [], false,
                "Encaminhamento automático desligado: abra as portas no roteador ou use o servidor de retransmissão.");
        IpPublico = resultado.IpPublico;
        StatusAlterado?.Invoke(resultado.Detalhe);
        return resultado;
    }

    /// <summary>Desfaz o encaminhamento de portas feito no roteador.</summary>
    public Task FecharAcessoExternoAsync(CancellationToken ct = default) => _acessoExterno.FecharAsync(ct);

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

    /// <summary>
    /// Decide se uma origem pode sequer tentar conectar e como ela será tratada.
    /// Devolve null quando a conexão deve ser cortada ali mesmo.
    /// </summary>
    private OrigemConexao? ClassificarOrigem(IPAddress? endereco)
    {
        if (endereco is null) return null;
        if (EhRedeLocal(endereco)) return OrigemConexao.Local(endereco.ToString());
        if (!_configuracao.PermitirAcessoExterno) return null;
        return OrigemConexao.Internet(endereco.ToString());
    }

    /// <summary>Entrega ao servidor um canal já aberto pelo servidor de retransmissão.</summary>
    public Task AtenderCanalDoRelayAsync(string canal, Stream transporte, Func<bool> conectado, CancellationToken ct) =>
        canal switch
        {
            "tela" => TransmitirTelaFluxoAsync(transporte, conectado, ct),
            _ => AtenderControleFluxoAsync(transporte, OrigemConexao.Servidor(), conectado, ct)
        };

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
