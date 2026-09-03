using System.Net;
using System.Net.Sockets;
using System.Text;

namespace PCFlow.Core;

/// <summary>
/// Uma conexão de celular. Lê linhas JSON, autentica e executa comandos.
///
/// Pontos que corrigem travamentos da v1:
///  - leitura com limite de tamanho por linha, evitando alocação ilimitada;
///  - timeout de inatividade: sem heartbeat em 25 s a sessão cai e o celular reconecta;
///  - escrita serializada por SemaphoreSlim (a captura de tela escreve em paralelo aos "pong");
///  - qualquer exceção de um comando é registrada e a sessão continua viva.
/// </summary>
internal sealed class SessaoCliente
{
    private readonly TcpClient _cliente;
    private readonly ServidorPcFlow _servidor;
    private readonly ServicosPlataforma _plataforma;
    private readonly ServicoArquivos _arquivos;
    private readonly SemaphoreSlim _escrita = new(1, 1);
    private readonly CancellationTokenSource _encerramento = new();

    private StreamWriter? _writer;
    private CancellationTokenSource? _tela;

    public bool Autenticada { get; private set; }
    public string DispositivoId { get; private set; } = "";
    public string NomeDispositivo { get; private set; } = "";

    public SessaoCliente(TcpClient cliente, ServidorPcFlow servidor,
        ServicosPlataforma plataforma, ServicoArquivos arquivos)
    {
        _cliente = cliente;
        _servidor = servidor;
        _plataforma = plataforma;
        _arquivos = arquivos;
    }

    public void Encerrar()
    {
        try { _encerramento.Cancel(); } catch (ObjectDisposedException) { }
        try { _cliente.Close(); } catch (Exception) { }
    }

    public async Task ExecutarAsync(CancellationToken ctExterno)
    {
        using var vinculado = CancellationTokenSource.CreateLinkedTokenSource(ctExterno, _encerramento.Token);
        var ct = vinculado.Token;
        var origem = (_cliente.Client.RemoteEndPoint as IPEndPoint)?.Address ?? IPAddress.None;

        using (_cliente)
        {
            _cliente.NoDelay = true;
            using var stream = _cliente.GetStream();
            using var reader = new StreamReader(stream, Encoding.UTF8, false, 8192, leaveOpen: true);
            await using var writer = new StreamWriter(stream, new UTF8Encoding(false), 8192, leaveOpen: true)
            { AutoFlush = false };
            _writer = writer;

            if (!_servidor.PermiteOrigem(origem))
            {
                await EnviarAsync(new { tipo = "erro", codigo = "fora_da_lan", mensagem = "Modo somente rede local ativo." }, ct);
                _servidor.Log.Escrever(Categoria.Conexao, $"Recusado (fora da LAN): {origem}");
                return;
            }

            // --- handshake ---
            var primeira = await LerLinhaAsync(reader, TimeSpan.FromSeconds(10), ct);
            var ola = primeira is null ? null : Protocolo.Desserializar(primeira);
            if (ola is null || ola.Tipo != "ola")
            {
                await EnviarAsync(new { tipo = "erro", codigo = "handshake", mensagem = "Handshake inválido." }, ct);
                return;
            }
            if (ola.ProtocoloVersao > 0 && ola.ProtocoloVersao != Protocolo.Versao)
            {
                await EnviarAsync(new
                {
                    tipo = "erro",
                    codigo = "versao",
                    mensagem = $"Versões incompatíveis. O PC usa o protocolo v{Protocolo.Versao} e o app v{ola.ProtocoloVersao}. Atualize os dois."
                }, ct);
                return;
            }

            var resultado = _servidor.Autenticar(ola, origem, out var dispositivo, out var detalhe);
            if (resultado is ServidorPcFlow.ResultadoHandshake.PinInvalido
                or ServidorPcFlow.ResultadoHandshake.Bloqueado
                or ServidorPcFlow.ResultadoHandshake.Recusado
                or ServidorPcFlow.ResultadoHandshake.NaoAutorizado || dispositivo is null)
            {
                await EnviarAsync(new
                {
                    tipo = "erro",
                    codigo = resultado.ToString().ToLowerInvariant(),
                    mensagem = detalhe ?? "Conexão recusada."
                }, ct);
                return;
            }

            Autenticada = true;
            DispositivoId = dispositivo.Id;
            NomeDispositivo = dispositivo.Nome;

            await EnviarAsync(new
            {
                tipo = resultado == ServidorPcFlow.ResultadoHandshake.Pareado ? "pareado" : "conectado",
                token = dispositivo.Token,
                nome = _plataforma.NomeMaquina,
                protocolo = Protocolo.Versao,
                versao = Protocolo.VersaoApp,
                recursos = Recursos()
            }, ct);
            _servidor.RegistrarConectado(dispositivo);
            _servidor.Log.Escrever(Categoria.Conexao, $"{dispositivo.Nome} conectado ({origem})");

            // --- laço de comandos ---
            try
            {
                while (!ct.IsCancellationRequested)
                {
                    var linha = await LerLinhaAsync(reader, Protocolo.TempoLimiteInatividade, ct);
                    if (linha is null) break;
                    if (linha.Length == 0) continue;

                    var msg = Protocolo.Desserializar(linha);
                    if (msg is null) continue;

                    try { await TratarAsync(msg, ct); }
                    catch (OperationCanceledException) { throw; }
                    catch (Exception ex)
                    {
                        _servidor.Log.Escrever(Categoria.Erro, $"Comando '{msg.Tipo}': {ex.GetType().Name}");
                    }
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (SocketException) { }
            finally
            {
                _tela?.Cancel();
                Autenticada = false;
                _servidor.Log.Escrever(Categoria.Conexao, $"{NomeDispositivo} desconectou");
            }
        }
    }

    private object Recursos() => new
    {
        arquivos = _servidor.Configuracao.PermitirArquivos,
        tela = _servidor.Configuracao.PermitirTelaRemota && _plataforma.CapturaTela is not null,
        energia = _servidor.Configuracao.PermitirEnergia,
        areaTransferencia = _servidor.Configuracao.SincronizarAreaTransferencia && _plataforma.AreaTransferencia is not null,
        atalhos = _plataforma.Lancador is not null
    };

    /// <summary>
    /// Lê uma linha com timeout e limite de tamanho. StreamReader.ReadLineAsync não
    /// respeita CancellationToken em .NET 8 sobre sockets, então o timeout é aplicado
    /// aqui e fecha o socket, que é o que efetivamente destrava a leitura.
    /// </summary>
    private async Task<string?> LerLinhaAsync(StreamReader reader, TimeSpan limite, CancellationToken ct)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(limite);
        var buffer = new StringBuilder(256);
        var um = new char[1];
        while (true)
        {
            int lidos;
            try
            {
                lidos = await reader.ReadAsync(um.AsMemory(0, 1), timeout.Token).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
                _servidor.Log.Escrever(Categoria.Conexao, $"{NomeDispositivo}: sem sinal de vida, encerrando sessão");
                return null;
            }
            if (lidos == 0) return buffer.Length > 0 ? buffer.ToString() : null;

            var c = um[0];
            if (c == '\n') return buffer.ToString().TrimEnd('\r');
            if (c == '\r') continue;
            if (buffer.Length >= Protocolo.TamanhoMaximoLinha)
            {
                _servidor.Log.Escrever(Categoria.Erro, "Mensagem acima do limite: sessão encerrada.");
                return null;
            }
            buffer.Append(c);
        }
    }

    private async Task EnviarAsync(object payload, CancellationToken ct)
    {
        var texto = Protocolo.Serializar(payload);
        await _escrita.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (_writer is null) return;
            await _writer.WriteLineAsync(texto.AsMemory(), ct).ConfigureAwait(false);
            await _writer.FlushAsync(ct).ConfigureAwait(false);
        }
        catch (Exception) when (!ct.IsCancellationRequested) { Encerrar(); }
        finally { _escrita.Release(); }
    }

    private async Task TratarAsync(Mensagem m, CancellationToken ct)
    {
        // "ping" responde sempre, mesmo pausado: é o heartbeat da conexão.
        if (m.Tipo == "ping")
        {
            await EnviarAsync(new { tipo = "pong", t = m.Carimbo, conectados = _servidor.Conectados }, ct);
            return;
        }
        if (_servidor.Pausado) return;

        var cfg = _servidor.Configuracao;
        switch (m.Tipo)
        {
            case "mouse_move":
                _plataforma.Entrada.MoverRelativo(m.Dx, m.Dy);
                break;

            case "mouse_abs":
                _plataforma.Entrada.MoverAbsoluto(m.X, m.Y);
                break;

            case "mouse_click":
                _plataforma.Entrada.Botao(ConverterBotao(m.Botao), ConverterAcao(m.Acao));
                break;

            case "scroll":
                _plataforma.Entrada.Rolar((int)m.Dx, (int)m.Dy);
                break;

            case "texto":
                if (!string.IsNullOrEmpty(m.Texto)) _plataforma.Entrada.DigitarTexto(m.Texto);
                break;

            case "tecla":
                if (!string.IsNullOrEmpty(m.Tecla)) _plataforma.Entrada.PressionarTecla(m.Tecla, m.Modificadores);
                break;

            case "atalho":
                if (!string.IsNullOrEmpty(m.Acao))
                {
                    var partes = m.Acao.Split('+',
                        StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
                    if (partes.Length > 0)
                        _plataforma.Entrada.PressionarTecla(partes[^1], partes[..^1]);
                }
                break;

            case "media":
                if (!string.IsNullOrEmpty(m.Acao)) _plataforma.Midia.Executar(m.Acao);
                break;

            case "power":
                if (!cfg.PermitirEnergia)
                {
                    await EnviarAsync(new { tipo = "aviso", mensagem = "Controle de energia desativado no PC." }, ct);
                    break;
                }
                if (!string.IsNullOrEmpty(m.Acao))
                {
                    var ok = _plataforma.Energia.Executar(m.Acao);
                    _servidor.Log.Escrever(Categoria.Comando, $"Energia: {m.Acao} ({(ok ? "executado" : "recusado")})");
                    if (!ok) await EnviarAsync(new { tipo = "aviso", mensagem = "Ação de energia indisponível." }, ct);
                }
                break;

            case "app_listar":
                await EnviarAsync(new
                {
                    tipo = "app_lista",
                    itens = (_plataforma.Lancador?.Listar() ?? [])
                        .Select(a => new ItemArquivo { Nome = a.Nome, Caminho = a.Id, Pasta = false }).ToList()
                }, ct);
                break;

            case "app_abrir":
                {
                    var ok = _plataforma.Lancador?.Executar(m.Acao ?? "") ?? false;
                    _servidor.Log.Escrever(Categoria.Comando, $"Abrir '{m.Acao}': {(ok ? "ok" : "falhou")}");
                    if (!ok) await EnviarAsync(new { tipo = "aviso", mensagem = "Não foi possível abrir esse item." }, ct);
                }
                break;

            case "clipboard_enviar":
                if (cfg.SincronizarAreaTransferencia && m.Texto is not null)
                    _plataforma.AreaTransferencia?.Escrever(m.Texto);
                break;

            case "clipboard_pedir":
                if (cfg.SincronizarAreaTransferencia)
                    await EnviarAsync(new { tipo = "clipboard", texto = _plataforma.AreaTransferencia?.Ler() ?? "" }, ct);
                break;

            case "arq_listar":
                if (!cfg.PermitirArquivos) { await NegarArquivos(ct); break; }
                try
                {
                    await EnviarAsync(new { tipo = "arq_lista", caminho = m.Caminho ?? "", itens = _arquivos.Listar(m.Caminho) }, ct);
                }
                catch (Exception ex)
                {
                    await EnviarAsync(new { tipo = "arq_erro", mensagem = ex.Message }, ct);
                }
                break;

            case "arq_baixar":
                if (!cfg.PermitirArquivos) { await NegarArquivos(ct); break; }
                await EnviarArquivoAsync(m, ct);
                break;

            case "arq_enviar":
                if (!cfg.PermitirArquivos) { await NegarArquivos(ct); break; }
                try
                {
                    var dados = Convert.FromBase64String(m.Dados ?? "");
                    var destino = _arquivos.GravarBloco(m.Nome ?? "arquivo.bin", m.Offset, dados);
                    if (m.Fim)
                    {
                        _servidor.Log.Escrever(Categoria.Arquivo, $"Recebido: {Path.GetFileName(destino)}");
                        await EnviarAsync(new { tipo = "arq_recebido", caminho = destino, ok = true }, ct);
                    }
                    else
                    {
                        await EnviarAsync(new { tipo = "arq_ack", offset = m.Offset + dados.Length }, ct);
                    }
                }
                catch (Exception ex)
                {
                    await EnviarAsync(new { tipo = "arq_erro", mensagem = ex.Message }, ct);
                }
                break;

            case "tela_iniciar":
                if (!cfg.PermitirTelaRemota || _plataforma.CapturaTela is null)
                {
                    await EnviarAsync(new { tipo = "aviso", mensagem = "Tela remota desativada no PC." }, ct);
                    break;
                }
                IniciarTela(m, ct);
                break;

            case "tela_parar":
                _tela?.Cancel();
                _tela = null;
                break;

            case "desconectar":
                Encerrar();
                break;
        }
    }

    private Task NegarArquivos(CancellationToken ct)
        => EnviarAsync(new { tipo = "arq_erro", mensagem = "Acesso a arquivos desativado no PC." }, ct);

    private async Task EnviarArquivoAsync(Mensagem m, CancellationToken ct)
    {
        try
        {
            var bloco = _arquivos.LerBloco(m.Caminho ?? "", m.Offset, out var total);
            if (bloco is null)
            {
                await EnviarAsync(new { tipo = "arq_dados", caminho = m.Caminho, offset = m.Offset, fim = true, tamanho = total }, ct);
                return;
            }
            await EnviarAsync(new
            {
                tipo = "arq_dados",
                caminho = m.Caminho,
                offset = m.Offset,
                tamanho = total,
                dados = Convert.ToBase64String(bloco),
                fim = m.Offset + bloco.Length >= total
            }, ct);
        }
        catch (Exception ex)
        {
            await EnviarAsync(new { tipo = "arq_erro", mensagem = ex.Message }, ct);
        }
    }

    private void IniciarTela(Mensagem m, CancellationToken ct)
    {
        _tela?.Cancel();
        var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        _tela = cts;
        var qualidade = Math.Clamp(m.Qualidade <= 0 ? 55 : m.Qualidade, 20, 90);
        var largura = Math.Clamp(m.Largura <= 0 ? 1280 : m.Largura, 480, 1920);
        var intervalo = TimeSpan.FromMilliseconds(1000.0 / Math.Clamp(m.Fps <= 0 ? 15 : m.Fps, 5, 30));

        _ = Task.Run(async () =>
        {
            _servidor.Log.Escrever(Categoria.Tela, $"Tela remota iniciada ({largura}px, q{qualidade})");
            try
            {
                while (!cts.Token.IsCancellationRequested)
                {
                    var inicio = DateTime.UtcNow;
                    var quadro = _plataforma.CapturaTela?.Capturar(largura, qualidade);
                    if (quadro is not null)
                    {
                        await EnviarAsync(new
                        {
                            tipo = "tela_quadro",
                            largura = quadro.Largura,
                            altura = quadro.Altura,
                            dados = Convert.ToBase64String(quadro.Jpeg)
                        }, cts.Token).ConfigureAwait(false);
                    }
                    var gasto = DateTime.UtcNow - inicio;
                    var espera = intervalo - gasto;
                    if (espera > TimeSpan.Zero) await Task.Delay(espera, cts.Token).ConfigureAwait(false);
                }
            }
            catch (OperationCanceledException) { }
            catch (Exception ex) { _servidor.Log.Escrever(Categoria.Erro, $"Tela remota: {ex.GetType().Name}"); }
            finally { _servidor.Log.Escrever(Categoria.Tela, "Tela remota parada"); }
        }, cts.Token);
    }

    private static BotaoMouse ConverterBotao(string? b) => b?.ToLowerInvariant() switch
    {
        "right" or "direito" => BotaoMouse.Direito,
        "middle" or "meio" => BotaoMouse.Meio,
        _ => BotaoMouse.Esquerdo
    };

    private static AcaoBotao ConverterAcao(string? a) => a?.ToLowerInvariant() switch
    {
        "down" or "pressionar" => AcaoBotao.Pressionar,
        "up" or "soltar" => AcaoBotao.Soltar,
        _ => AcaoBotao.Clique
    };
}
