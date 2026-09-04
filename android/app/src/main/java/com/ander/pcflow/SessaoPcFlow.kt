package com.ander.pcflow

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

object SessaoPcFlow {
    /** Deve casar com VersaoPcFlow.App no Windows: o servidor recusa versões diferentes. */
    const val VERSAO_APP = "1.2.0"

    private const val PORTA_DESCOBERTA = 45455
    private const val PORTA_RELAY = 45460
    private const val TEMPO_DESCOBERTA_MS = 1800

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(EstadoSessao())
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()
    private val _pcs = MutableStateFlow<List<PcEncontrado>>(emptyList())
    val pcs: StateFlow<List<PcEncontrado>> = _pcs.asStateFlow()
    private val _quadro = MutableStateFlow<Bitmap?>(null)
    val quadro: StateFlow<Bitmap?> = _quadro.asStateFlow()
    private val _clipboardRemoto = MutableStateFlow("")
    val clipboardRemoto: StateFlow<String> = _clipboardRemoto.asStateFlow()
    private val _monitorAtual = MutableStateFlow(0)
    val monitorAtual: StateFlow<Int> = _monitorAtual.asStateFlow()
    private val _arquivos = MutableStateFlow(EstadoArquivos())
    val arquivos: StateFlow<EstadoArquivos> = _arquivos.asStateFlow()

    private var socket: SSLSocket? = null
    private var writer: BufferedWriter? = null
    private var streamSocket: SSLSocket? = null
    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null
    private var contexto: Context? = null
    private val conectando = AtomicBoolean(false)
    @Volatile private var desconexaoManual = false
    @Volatile private var ultimoPc: PcEncontrado? = null
    private var tentativaReconexao = 0

    fun inicializar(context: Context) { contexto = context.applicationContext }

    fun descobrir() {
        escopo.launch {
            val encontrados = linkedMapOf<String, PcEncontrado>()
            try {
                DatagramSocket().use { udp ->
                    udp.broadcast = true
                    udp.soTimeout = 250
                    val dados = "PCFLOW_DISCOVER_V2".toByteArray()
                    runCatching { udp.send(DatagramPacket(dados, dados.size, InetAddress.getByName("255.255.255.255"), PORTA_DESCOBERTA)) }
                    val inicio = System.currentTimeMillis()
                    while (System.currentTimeMillis() - inicio < TEMPO_DESCOBERTA_MS) {
                        try {
                            val buffer = ByteArray(4096)
                            val pacote = DatagramPacket(buffer, buffer.size)
                            udp.receive(pacote)
                            val json = JSONObject(String(pacote.data, 0, pacote.length))
                            if (json.optString("tipo") == "pcflow") {
                                val host = pacote.address.hostAddress ?: continue
                                val pc = PcEncontrado(
                                    nome = json.optString("nome", host),
                                    host = host,
                                    porta = json.optInt("porta", 45456),
                                    portaTela = json.optInt("portaTela", 45457),
                                    portaArquivos = json.optInt("portaArquivos", 45458),
                                    maquinaId = json.optString("maquinaId", ""),
                                    tls = json.optString("tls", ""),
                                    monitores = json.optInt("monitores", 1).coerceAtLeast(1)
                                )
                                encontrados[pc.maquinaId.ifBlank { host }] = pc
                                _pcs.value = encontrados.values.sortedBy { it.nome.lowercase() }
                            }
                        } catch (_: SocketTimeoutException) { }
                    }
                }
            } catch (e: Exception) {
                if (_estado.value.estado != EstadoConexao.CONECTADO)
                    _estado.value = EstadoSessao(EstadoConexao.ERRO, mensagem = "Falha na descoberta: ${e.message}")
            }
        }
    }

    /**
     * Monta um destino a partir do código de acesso mostrado no PC.
     * `servidor` só é usado quando o código é do tipo que passa por servidor de
     * retransmissão. Devolve null quando o código não é válido.
     */
    fun destinoDoCodigo(codigo: String, servidor: String = ""): PcEncontrado? {
        val destino = CodigoAcesso.ler(codigo) ?: return null
        return if (destino.direto)
            PcEncontrado(
                nome = "PC em ${destino.host}",
                host = destino.host,
                porta = destino.porta,
                tls = destino.impressaoTls,
                porCodigo = true
            )
        else {
            if (servidor.isBlank()) return null
            PcEncontrado(
                nome = "PC ${destino.identificadorServidor}",
                host = "servidor",
                maquinaId = destino.identificadorServidor.toString(),
                tls = destino.impressaoTls,
                servidorRelay = servidor,
                codigoRelay = destino.identificadorServidor.toString(),
                porCodigo = true
            )
        }
    }

    /** O endereço do servidor de retransmissão fica guardado entre as sessões. */
    fun servidorSalvo(): String = prefs().getString("servidor_relay", "") ?: ""

    fun salvarServidor(endereco: String) {
        prefs().edit().putString("servidor_relay", endereco.trim()).apply()
    }

    fun conectar(pc: PcEncontrado, pin: String? = null, senha: String? = null) {
        desconexaoManual = false
        ultimoPc = pc
        conectarInterno(pc, pin, senha)
    }

    private fun conectarInterno(pc: PcEncontrado, pin: String? = null, senha: String? = null) {
        if (!conectando.compareAndSet(false, true)) return
        _estado.value = EstadoSessao(EstadoConexao.CONECTANDO, pc, "Conectando ao PCFlow…")
        escopo.launch {
            try {
                desconectarSockets()
                pararHeartbeat()
                val novoSocket = abrirTls(pc, pc.porta)
                novoSocket.soTimeout = 120_000
                val novoWriter = BufferedWriter(OutputStreamWriter(novoSocket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(novoSocket.getInputStream(), Charsets.UTF_8))
                socket = novoSocket
                writer = novoWriter

                val prefs = prefs()
                val id = obterDispositivoId()
                val chaveToken = chaveToken(pc)
                val token = prefs.getString(chaveToken, null)
                val ola = JSONObject()
                    .put("tipo", "ola")
                    .put("dispositivoId", id)
                    .put("maquinaId", pc.maquinaId)
                    .put("nome", android.os.Build.MODEL)
                    .put("appVersao", VERSAO_APP)
                if (token != null) ola.put("token", token)
                if (!pin.isNullOrBlank()) ola.put("pin", pin.replace(" ", ""))
                if (!senha.isNullOrBlank()) ola.put("senha", senha)
                escreverLinha(ola)

                _estado.value = EstadoSessao(EstadoConexao.CONECTANDO, pc, if (!pin.isNullOrBlank()) "Validando código…" else if (!senha.isNullOrBlank()) "Validando senha…" else "Aguardando aceite no computador…")

                val resposta = reader.readLine() ?: throw EOFException("O computador fechou a conexão antes de responder")
                val json = JSONObject(resposta)
                if (json.optString("tipo") != "conectado") error(json.optString("mensagem", "Conexão recusada"))

                novoSocket.soTimeout = 0
                val novoToken = json.optString("token")
                if (novoToken.isNotBlank()) prefs.edit().putString(chaveToken, novoToken).apply()
                val sessaoId = json.getString("sessaoId")
                val monitores = json.optJSONArray("monitores")?.length()?.coerceAtLeast(1) ?: pc.monitores
                val p = json.optJSONObject("permissoes")
                val permissoes = PermissoesRemotas(
                    tela = p?.optBoolean("tela", true) ?: true,
                    entrada = p?.optBoolean("entrada", true) ?: true,
                    clipboard = p?.optBoolean("clipboard", true) ?: true,
                    energia = p?.optBoolean("energia", true) ?: true,
                    arquivos = p?.optBoolean("arquivos", false) ?: false
                )
                tentativaReconexao = 0
                _monitorAtual.value = 0
                _estado.value = EstadoSessao(EstadoConexao.CONECTADO, pc, "Conectado", sessaoId, monitores, permissoes)
                contexto?.let { ServicoConexao.iniciar(it) }
                iniciarHeartbeat()
                if (permissoes.tela) iniciarStream(pc, sessaoId, 0)

                while (novoSocket.isConnected && !novoSocket.isClosed) {
                    val linha = reader.readLine() ?: throw EOFException("O computador encerrou a sessão")
                    if (linha.isBlank()) continue
                    val evento = JSONObject(linha)
                    when (evento.optString("tipo")) {
                        "clipboard" -> _clipboardRemoto.value = evento.optString("texto", "")
                        "pong" -> Unit
                    }
                }
            } catch (e: Exception) {
                if (!desconexaoManual) _estado.value = EstadoSessao(EstadoConexao.ERRO, pc, mensagemAmigavel(e))
            } finally {
                conectando.set(false)
                pararHeartbeat()
                pararStream()
                desconectarSockets()
                if (!desconexaoManual && ultimoPc == pc) agendarReconexao(pc)
            }
        }
    }

    private fun iniciarHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = escopo.launch {
            while (isActive && _estado.value.estado == EstadoConexao.CONECTADO) {
                delay(15_000)
                try { escreverLinha(JSONObject().put("tipo", "ping").put("t", System.currentTimeMillis())) }
                catch (_: Exception) { break }
            }
        }
    }

    private fun pararHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun enviar(tipo: String, preencher: JSONObject.() -> Unit = {}) {
        if (_estado.value.estado != EstadoConexao.CONECTADO) return
        escopo.launch {
            try { escreverLinha(JSONObject().put("tipo", tipo).apply(preencher)) }
            catch (_: Exception) { if (!desconexaoManual) _estado.value = _estado.value.copy(estado = EstadoConexao.ERRO, mensagem = "Conexão perdida. Tentando reconectar…") }
        }
    }

    fun alterarMonitor(indice: Int) {
        val estadoAtual = _estado.value
        val pc = estadoAtual.pc ?: return
        val sessao = estadoAtual.sessaoId ?: return
        val novo = indice.coerceIn(0, estadoAtual.quantidadeMonitores - 1)
        _monitorAtual.value = novo
        _quadro.value = null
        iniciarStream(pc, sessao, novo)
    }

    /** Posiciona o ponteiro do PC sem clicar, em coordenada normalizada 0..1. */
    fun enviarPosicao(x: Double, y: Double, monitor: Int) =
        enviar("mouse_abs") { put("x", x); put("y", y); put("monitor", monitor) }

    fun solicitarClipboard() = enviar("clipboard_get")
    fun enviarClipboard(texto: String) = enviar("clipboard_set") { put("texto", texto) }

    fun listarArquivos(caminho: String = "") {
        if (!_estado.value.permissoes.arquivos) return
        _arquivos.value = _arquivos.value.copy(carregando = true, mensagem = "")
        escopo.launch {
            try {
                usarCanalArquivos { input, output ->
                    escreverLinhaRaw(output, JSONObject().put("tipo", "listar").put("caminho", caminho))
                    val resposta = JSONObject(lerLinhaRaw(input) ?: error("Sem resposta"))
                    verificarResposta(resposta)
                    val lista = resposta.optJSONArray("itens")
                    val itens = buildList {
                        if (lista != null) for (i in 0 until lista.length()) {
                            val item = lista.getJSONObject(i)
                            add(ArquivoRemoto(
                                nome = item.optString("nome"),
                                caminho = item.optString("caminho"),
                                pasta = item.optBoolean("pasta"),
                                tamanho = item.optLong("tamanho"),
                                modificado = item.optString("modificado"),
                                raiz = item.optBoolean("raiz")
                            ))
                        }
                    }
                    _arquivos.value = EstadoArquivos(
                        carregando = false,
                        caminho = resposta.optString("caminho", caminho),
                        pai = resposta.optString("pai", ""),
                        itens = itens
                    )
                }
            } catch (e: Exception) {
                _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "Arquivos: ${e.message}")
            }
        }
    }

    fun criarPasta(nome: String) {
        val atual = _arquivos.value.caminho
        if (atual.isBlank() || nome.isBlank()) return
        val destino = combinarRemoto(atual, nome.trim())
        operarArquivo(JSONObject().put("tipo", "mkdir").put("caminho", destino)) { listarArquivos(atual) }
    }

    fun apagarArquivo(item: ArquivoRemoto) {
        val atual = _arquivos.value.caminho
        operarArquivo(JSONObject().put("tipo", "apagar").put("caminho", item.caminho)) { listarArquivos(atual) }
    }

    fun baixarArquivo(context: Context, item: ArquivoRemoto) {
        if (item.pasta) return
        _arquivos.value = _arquivos.value.copy(carregando = true, mensagem = "Baixando ${item.nome}…")
        escopo.launch {
            try {
                usarCanalArquivos { input, output ->
                    escreverLinhaRaw(output, JSONObject().put("tipo", "baixar").put("caminho", item.caminho))
                    val cabecalho = JSONObject(lerLinhaRaw(input) ?: error("Sem resposta"))
                    verificarResposta(cabecalho)
                    if (cabecalho.optString("tipo") != "arquivo") error("Resposta de arquivo inválida")
                    val tamanho = cabecalho.getLong("tamanho")
                    val (saida, descricao) = criarSaidaDownload(context, cabecalho.optString("nome", item.nome))
                    saida.use { destino ->
                        var restante = tamanho
                        val buffer = ByteArray(128 * 1024)
                        while (restante > 0) {
                            val lidos = input.read(buffer, 0, minOf(buffer.size.toLong(), restante).toInt())
                            if (lidos <= 0) error("Transferência interrompida")
                            destino.write(buffer, 0, lidos)
                            restante -= lidos
                        }
                    }
                    _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "Salvo em $descricao")
                }
            } catch (e: Exception) {
                _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "Download: ${e.message}")
            }
        }
    }

    fun enviarArquivo(context: Context, uri: Uri) {
        val pasta = _arquivos.value.caminho
        if (pasta.isBlank()) return
        _arquivos.value = _arquivos.value.copy(carregando = true, mensagem = "Preparando envio…")
        escopo.launch {
            var temporario: File? = null
            try {
                val nome = obterNomeArquivo(context, uri)
                temporario = File.createTempFile("pcflow-upload-", ".tmp", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { entrada ->
                    temporario.outputStream().use { saida -> entrada.copyTo(saida, 128 * 1024) }
                } ?: error("Não foi possível ler o arquivo")

                usarCanalArquivos { input, output ->
                    escreverLinhaRaw(output, JSONObject().put("tipo", "enviar").put("caminho", pasta).put("nome", nome).put("tamanho", temporario.length()))
                    temporario.inputStream().use { arquivo -> arquivo.copyTo(output, 128 * 1024) }
                    output.flush()
                    val resposta = JSONObject(lerLinhaRaw(input) ?: error("Sem confirmação"))
                    verificarResposta(resposta)
                }
                _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "$nome enviado")
                listarArquivos(pasta)
            } catch (e: Exception) {
                _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "Envio: ${e.message}")
            } finally {
                temporario?.delete()
            }
        }
    }

    private fun operarArquivo(comando: JSONObject, sucesso: () -> Unit) {
        _arquivos.value = _arquivos.value.copy(carregando = true, mensagem = "")
        escopo.launch {
            try {
                usarCanalArquivos { input, output ->
                    escreverLinhaRaw(output, comando)
                    verificarResposta(JSONObject(lerLinhaRaw(input) ?: error("Sem resposta")))
                }
                sucesso()
            } catch (e: Exception) {
                _arquivos.value = _arquivos.value.copy(carregando = false, mensagem = "Arquivos: ${e.message}")
            }
        }
    }

    private suspend fun usarCanalArquivos(bloco: suspend (InputStream, OutputStream) -> Unit) {
        val pc = _estado.value.pc ?: error("Sem computador conectado")
        val token = prefs().getString(chaveToken(pc), null) ?: error("Autorização não encontrada")
        val id = obterDispositivoId()
        val s = abrirTls(pc, pc.portaArquivos)
        s.soTimeout = 60_000
        s.use {
            val input = BufferedInputStream(s.getInputStream(), 128 * 1024)
            val output = BufferedOutputStream(s.getOutputStream(), 128 * 1024)
            escreverLinhaRaw(output, JSONObject().put("tipo", "arquivos_ola").put("dispositivoId", id).put("maquinaId", pc.maquinaId).put("token", token))
            val auth = JSONObject(lerLinhaRaw(input) ?: error("Servidor de arquivos não respondeu"))
            verificarResposta(auth)
            bloco(input, output)
        }
    }

    private fun verificarResposta(json: JSONObject) {
        if (json.optString("tipo") == "erro") error(json.optString("mensagem", "Operação recusada"))
    }

    fun desconectar() {
        desconexaoManual = true
        ultimoPc = null
        escopo.launch {
            pararHeartbeat()
            pararStream()
            desconectarSockets()
            _quadro.value = null
            _arquivos.value = EstadoArquivos()
            _estado.value = EstadoSessao()
            contexto?.let { ServicoConexao.parar(it) }
        }
    }

    private fun iniciarStream(pc: PcEncontrado, sessaoId: String, monitor: Int) {
        streamJob?.cancel()
        try { streamSocket?.close() } catch (_: Exception) { }
        streamJob = escopo.launch {
            var atraso = 300L
            while (isActive && _estado.value.estado == EstadoConexao.CONECTADO && _estado.value.sessaoId == sessaoId && _monitorAtual.value == monitor) {
                try {
                    val s = abrirTls(pc, pc.portaTela)
                    streamSocket = s
                    val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                    out.write(JSONObject().put("tipo", "stream").put("sessaoId", sessaoId).put("monitor", monitor).put("qualidade", 70).put("fps", 12).toString())
                    out.newLine(); out.flush()
                    val input = DataInputStream(BufferedInputStream(s.getInputStream(), 128 * 1024))
                    atraso = 300L
                    while (isActive && !s.isClosed && _monitorAtual.value == monitor) {
                        val tamanho = input.readInt()
                        if (tamanho <= 0 || tamanho > 16_000_000) error("Quadro inválido")
                        val bytes = ByteArray(tamanho)
                        input.readFully(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { _quadro.value = it }
                    }
                } catch (_: CancellationException) { break }
                catch (_: Exception) {
                    try { streamSocket?.close() } catch (_: Exception) { }
                    delay(atraso)
                    atraso = (atraso * 2).coerceAtMost(3000)
                }
            }
        }
    }

    private fun pararStream() {
        streamJob?.cancel()
        streamJob = null
        try { streamSocket?.close() } catch (_: Exception) { }
        streamSocket = null
    }

    private fun agendarReconexao(pc: PcEncontrado) {
        tentativaReconexao++
        val atraso = minOf(10_000L, 750L * (1 shl minOf(tentativaReconexao, 4)))
        escopo.launch {
            delay(atraso)
            if (!desconexaoManual && ultimoPc == pc && _estado.value.estado != EstadoConexao.CONECTADO) {
                _estado.value = EstadoSessao(EstadoConexao.CONECTANDO, pc, "Reconectando automaticamente…")
                conectarInterno(pc, null, null)
            }
        }
    }

    private fun desconectarSockets() {
        try { writer?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        writer = null
        socket = null
    }

    @Synchronized private fun escreverLinha(json: JSONObject) {
        writer?.apply { write(json.toString()); newLine(); flush() } ?: error("Sem conexão")
    }

    private fun abrirTls(pc: PcEncontrado, porta: Int): SSLSocket {
        if (pc.tls.isBlank()) error("Identidade TLS do PC não disponível. Atualize a descoberta ou use o código de acesso.")
        val trustAll = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        }
        val contextoSsl = SSLContext.getInstance("TLS")
        contextoSsl.init(null, arrayOf(trustAll), SecureRandom())

        // Direto no PC, ou por dentro de um canal já aberto no servidor de
        // retransmissão. Nos dois casos o TLS é com o PC, ponta a ponta: quem
        // estiver no meio só vê bytes embaralhados.
        val base = if (pc.viaRelay) abrirCanalRelay(pc, alvoDaPorta(pc, porta))
                   else Socket().apply { connect(InetSocketAddress(pc.host, porta), 8000) }

        val ssl = try {
            contextoSsl.socketFactory.createSocket(base, pc.host, porta, true) as SSLSocket
        } catch (e: Exception) {
            runCatching { base.close() }
            throw e
        }
        ssl.tcpNoDelay = true
        ssl.keepAlive = true
        ssl.startHandshake()

        val cert = ssl.session.peerCertificates.firstOrNull() as? X509Certificate ?: error("Certificado remoto ausente")
        val atual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        // O código de acesso traz a impressão truncada; a descoberta traz inteira.
        if (!CodigoAcesso.impressaoConfere(atual, pc.tls)) {
            ssl.close()
            throw SSLHandshakeException("A identidade deste PC mudou. Conexão bloqueada por segurança.")
        }
        return ssl
    }

    private fun alvoDaPorta(pc: PcEncontrado, porta: Int) = when (porta) {
        pc.portaTela -> "tela"
        pc.portaArquivos -> "arquivos"
        else -> "controle"
    }

    /**
     * Pede ao servidor de retransmissão um canal até o PC. O servidor avisa o
     * computador, ele traz a outra ponta e a partir do "pronto" os bytes passam
     * direto — o servidor não decifra nada, só emenda os dois lados.
     */
    private fun abrirCanalRelay(pc: PcEncontrado, alvo: String): Socket {
        val separador = pc.servidorRelay.lastIndexOf(':')
        val host = if (separador > 0) pc.servidorRelay.substring(0, separador) else pc.servidorRelay
        val porta = if (separador > 0) pc.servidorRelay.substring(separador + 1).toIntOrNull() ?: PORTA_RELAY
                    else PORTA_RELAY

        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, porta), 8000)
            socket.tcpNoDelay = true
            socket.soTimeout = 15_000

            val saida = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            saida.write(JSONObject().put("tipo", "conectar").put("codigo", pc.codigoRelay).put("alvo", alvo).toString())
            saida.newLine()
            saida.flush()

            val resposta = lerLinhaRaw(socket.getInputStream())
                ?: error("O servidor de retransmissão não respondeu")
            val json = JSONObject(resposta)
            if (json.optString("tipo") != "pronto")
                error(json.optString("mensagem", "O servidor não conseguiu falar com o computador"))

            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun obterDispositivoId(): String {
        val p = prefs()
        return p.getString("dispositivo_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("dispositivo_id", it).apply()
        }
    }

    private fun chaveToken(pc: PcEncontrado) = "token_${pc.maquinaId.ifBlank { pc.host }}"

    private fun combinarRemoto(pasta: String, nome: String): String = when {
        pasta.endsWith("\\") || pasta.endsWith("/") -> pasta + nome
        else -> "$pasta\\$nome"
    }

    private fun obterNomeArquivo(context: Context, uri: Uri): String {
        var nome: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) nome = cursor.getString(0)
        }
        return nome?.takeIf { it.isNotBlank() } ?: "arquivo-${System.currentTimeMillis()}"
    }

    private fun criarSaidaDownload(context: Context, nome: String): Pair<OutputStream, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PCFlow")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Não foi possível criar o download")
            val output = context.contentResolver.openOutputStream(uri) ?: error("Não foi possível abrir o download")
            return output to "Downloads/PCFlow/$nome"
        }
        val pasta = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "PCFlow").apply { mkdirs() }
        val arquivo = File(pasta, nome)
        return arquivo.outputStream() to arquivo.absolutePath
    }

    private fun escreverLinhaRaw(output: OutputStream, json: JSONObject) {
        output.write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun lerLinhaRaw(input: InputStream): String? {
        val ms = ByteArrayOutputStream()
        while (ms.size() < 256 * 1024) {
            val b = input.read()
            if (b < 0) return if (ms.size() == 0) null else ms.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return ms.toString(Charsets.UTF_8.name())
            if (b != '\r'.code) ms.write(b)
        }
        error("Mensagem muito grande")
    }

    private fun mensagemAmigavel(e: Exception): String = when (e) {
        is SSLHandshakeException -> e.message ?: "Falha de segurança TLS"
        is EOFException -> "O PC encerrou a conexão. Feche qualquer versão antiga do PCFlow na bandeja do Windows, abra a versão mais recente e tente novamente."
        is ConnectException -> "Não foi possível alcançar o PC. Confirme que o PCFlow está ativo e que ambos estão na mesma rede Wi‑Fi/LAN."
        is SocketTimeoutException -> "Tempo esgotado. Aceite a solicitação no computador ou tente conectar usando o QR/código exibido no PCFlow."
        is SSLException -> if (e.message?.contains("closed", ignoreCase = true) == true) "A conexão segura foi fechada pelo PC. Reinicie o PCFlow do Windows e tente novamente." else e.message ?: "Falha de segurança TLS"
        else -> e.message ?: "Falha ao conectar"
    }

    private fun prefs() = requireNotNull(contexto).getSharedPreferences("pcflow", Context.MODE_PRIVATE)
}
