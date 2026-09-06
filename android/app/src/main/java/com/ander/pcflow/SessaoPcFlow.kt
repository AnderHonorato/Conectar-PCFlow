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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.*

/**
 * Monta as mensagens do canal de controle (contrato v2, seção 1). Fica fora do
 * objeto de sessão de propósito: o formato é acordo entre celular e PC, então
 * ele precisa ser testável sem Android, sem rede e sem PC do outro lado.
 */
object MontadorMensagens {
    fun normalizarBotao(botao: String): String = when (botao.trim().lowercase()) {
        "direito", "right" -> "direito"
        "meio", "middle", "central" -> "meio"
        else -> "esquerdo"
    }

    fun normalizarModificador(modificador: String): String = when (modificador.trim().lowercase()) {
        "ctrl", "control", "controle" -> "ctrl"
        "alt", "menu" -> "alt"
        "shift" -> "shift"
        "win", "windows", "meta", "super", "cmd" -> "win"
        else -> modificador.trim().lowercase()
    }

    fun mouseAbsoluto(x: Double, y: Double, monitor: Int): JSONObject = JSONObject()
        .put("tipo", "mouse_abs")
        .put("x", normalizada(x))
        .put("y", normalizada(y))
        .put("monitor", monitor.coerceAtLeast(0))

    fun mouseRelativo(dx: Double, dy: Double): JSONObject = JSONObject()
        .put("tipo", "mouse_move")
        .put("x", finita(dx))
        .put("y", finita(dy))

    fun mouseClique(botao: String, cliques: Int): JSONObject = JSONObject()
        .put("tipo", "mouse_click")
        .put("botao", normalizarBotao(botao))
        .put("cliques", cliques.coerceIn(1, 2))

    fun mouseBaixar(botao: String): JSONObject = JSONObject()
        .put("tipo", "mouse_down")
        .put("botao", normalizarBotao(botao))

    fun mouseSoltar(botao: String): JSONObject = JSONObject()
        .put("tipo", "mouse_up")
        .put("botao", normalizarBotao(botao))

    fun rolagem(delta: Int, horizontal: Boolean): JSONObject = JSONObject()
        .put("tipo", "scroll")
        .put("delta", delta)
        .put("eixo", if (horizontal) "horizontal" else "vertical")

    fun texto(texto: String): JSONObject = JSONObject()
        .put("tipo", "texto")
        .put("texto", texto)

    /** Sem modificador o campo nem aparece — é assim que a versão antiga entende. */
    fun tecla(tecla: String, modificadores: List<String> = emptyList()): JSONObject {
        val json = JSONObject().put("tipo", "tecla").put("tecla", tecla)
        val limpos = modificadores.map { normalizarModificador(it) }.filter { it.isNotBlank() }
        if (limpos.isNotEmpty()) json.put("modificadores", org.json.JSONArray(limpos))
        return json
    }

    fun clipboardPegar(): JSONObject = JSONObject().put("tipo", "clipboard_get")

    fun clipboardDefinir(texto: String): JSONObject = JSONObject()
        .put("tipo", "clipboard_set")
        .put("texto", texto)

    fun clipboardModo(modo: ModoClipboard): JSONObject = JSONObject()
        .put("tipo", "clipboard_modo")
        .put("modo", when (modo) {
            ModoClipboard.DESLIGADO -> "desligado"
            ModoClipboard.AUTOMATICO -> "auto"
            ModoClipboard.MANUAL -> "manual"
        })

    fun pedidoStream(sessaoId: String, monitor: Int, perfil: PerfilVideo): JSONObject = JSONObject()
        .put("tipo", "stream")
        .put("sessaoId", sessaoId)
        .put("monitor", monitor.coerceAtLeast(0))
        .put("fps", perfil.fps)
        .put("qualidade", perfil.qualidade)
        .put("larguraMaxima", perfil.larguraMaxima)

    private fun normalizada(valor: Double) = if (valor.isNaN()) 0.0 else valor.coerceIn(0.0, 1.0)
    private fun finita(valor: Double) = if (valor.isFinite()) valor else 0.0
}

/**
 * Um evento de entrada esperando a vez de ir para o PC. Só movimento e rolagem
 * podem ser agrupados; clique, botão e tecla saem inteiros e na ordem
 * (contrato v2, seção 4, regra 4).
 */
sealed class EventoEntrada {
    abstract fun paraJson(): JSONObject
    open val agrupavel: Boolean get() = false

    /** Devolve o evento resultante quando `proximo` continua este; null quando não dá para juntar. */
    open fun agrupar(proximo: EventoEntrada): EventoEntrada? = null

    data class Absoluto(val x: Double, val y: Double, val monitor: Int) : EventoEntrada() {
        override val agrupavel: Boolean get() = true
        // Posição absoluta não soma: a última apaga as anteriores.
        override fun agrupar(proximo: EventoEntrada): EventoEntrada? = proximo as? Absoluto
        override fun paraJson(): JSONObject = MontadorMensagens.mouseAbsoluto(x, y, monitor)
    }

    data class Relativo(val dx: Double, val dy: Double) : EventoEntrada() {
        override val agrupavel: Boolean get() = true
        override fun agrupar(proximo: EventoEntrada): EventoEntrada? =
            (proximo as? Relativo)?.let { Relativo(dx + it.dx, dy + it.dy) }
        override fun paraJson(): JSONObject = MontadorMensagens.mouseRelativo(dx, dy)
    }

    data class Rolagem(val delta: Int, val horizontal: Boolean) : EventoEntrada() {
        override val agrupavel: Boolean get() = true
        // Roda vertical e horizontal são canais diferentes: só soma com a igual.
        override fun agrupar(proximo: EventoEntrada): EventoEntrada? =
            (proximo as? Rolagem)?.takeIf { it.horizontal == horizontal }?.let { Rolagem(delta + it.delta, horizontal) }
        override fun paraJson(): JSONObject = MontadorMensagens.rolagem(delta, horizontal)
    }

    data class Clique(val botao: String, val cliques: Int) : EventoEntrada() {
        override fun paraJson(): JSONObject = MontadorMensagens.mouseClique(botao, cliques)
    }

    data class Pressao(val botao: String, val solta: Boolean) : EventoEntrada() {
        override fun paraJson(): JSONObject =
            if (solta) MontadorMensagens.mouseSoltar(botao) else MontadorMensagens.mouseBaixar(botao)
    }

    data class Texto(val texto: String) : EventoEntrada() {
        override fun paraJson(): JSONObject = MontadorMensagens.texto(texto)
    }

    data class Tecla(val tecla: String, val modificadores: List<String>) : EventoEntrada() {
        override fun paraJson(): JSONObject = MontadorMensagens.tecla(tecla, modificadores)
    }

    /** Mensagem já pronta (mídia, energia, área de transferência). Nunca é agrupada. */
    class Bruto(private val json: JSONObject) : EventoEntrada() {
        override fun paraJson(): JSONObject = json
    }
}

/**
 * Fila de saída da entrada do usuário.
 *
 * O agrupamento só acontece contra o ÚLTIMO evento da fila. É isso que mantém
 * a ordem: um movimento que chegou antes do clique continua antes dele, e um
 * movimento que chegou depois vira um item novo em vez de se somar ao de trás.
 * Se a soma pudesse pular por cima do clique, o clique aconteceria no lugar
 * errado.
 */
class FilaEntrada(private val limite: Int = 240) {
    private val itens = ArrayDeque<EventoEntrada>()

    /** Devolve true quando a fila tem que ser despachada já, sem esperar o quadro. */
    @Synchronized fun enfileirar(evento: EventoEntrada): Boolean {
        val agrupado = if (evento.agrupavel) itens.lastOrNull()?.agrupar(evento) else null
        if (agrupado != null) {
            itens.removeLast()
            itens.addLast(agrupado)
        } else {
            itens.addLast(evento)
        }
        return !evento.agrupavel || itens.size >= limite
    }

    @Synchronized fun drenar(): List<EventoEntrada> {
        if (itens.isEmpty()) return emptyList()
        val copia = itens.toList()
        itens.clear()
        return copia
    }

    @Synchronized fun limpar() = itens.clear()

    @get:Synchronized val vazia: Boolean get() = itens.isEmpty()
    @get:Synchronized val tamanho: Int get() = itens.size
}

/**
 * Guarda um único item à espera: o que chega joga fora o que ainda não foi
 * consumido. Latência importa mais que completude — quadro atrasado só serve
 * para atrasar o próximo.
 */
class CaixaUltimoQuadro<T> {
    private var pendente: T? = null
    private var descartados = 0

    /** Devolve o item atropelado (útil para reaproveitar o buffer dele). */
    @Synchronized fun publicar(item: T): T? {
        val antigo = pendente
        if (antigo != null) descartados++
        pendente = item
        return antigo
    }

    @Synchronized fun consumir(): T? {
        val item = pendente
        pendente = null
        return item
    }

    @get:Synchronized val quantidadeDescartada: Int get() = descartados
    @get:Synchronized val vazia: Boolean get() = pendente == null
}

/**
 * Rodízio dos quadros já publicados. Só devolve para reúso o quadro publicado
 * há `capacidade` publicações, porque a interface pode ainda estar desenhando
 * os últimos — reaproveitar cedo demais rasga a imagem na tela.
 */
class RodizioDeQuadros<T>(private val capacidade: Int = 3) {
    private val circulando = ArrayDeque<T>()

    @Synchronized fun publicar(novo: T): T? {
        circulando.addLast(novo)
        return if (circulando.size > capacidade) circulando.removeFirst() else null
    }

    @Synchronized fun limpar() = circulando.clear()

    @get:Synchronized val emCirculacao: Int get() = circulando.size
}

/** Quadro comprimido recém-chegado. O buffer pode ser maior que `tamanho` porque é reaproveitado. */
class QuadroBruto(val bytes: ByteArray, val tamanho: Int)

object SessaoPcFlow {
    /** Deve casar com VersaoPcFlow.App no Windows: o servidor recusa versões diferentes. */
    const val VERSAO_APP = "1.2.0"

    private const val PORTA_DESCOBERTA = 45455
    private const val PORTA_RELAY = 45460
    private const val TEMPO_DESCOBERTA_MS = 1800

    /** Um quadro de 60 Hz: movimento e rolagem viram no máximo uma mensagem por vez desta. */
    private const val INTERVALO_AGRUPAMENTO_MS = 16L
    private const val INTERVALO_PING_MS = 4_000L
    /** Depois disso sem quadro a interface precisa dizer que está reconectando. */
    private const val LIMITE_SEM_QUADRO_MS = 5_000L
    private const val MENSAGEM_CONECTADO = "Conectado"
    private const val MENSAGEM_SEM_TELA = "Reconectando a tela…"

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
    private val _estatisticas = MutableStateFlow(EstatisticasSessao())
    val estatisticas: StateFlow<EstatisticasSessao> = _estatisticas.asStateFlow()
    private val _perfilVideo = MutableStateFlow(PerfilVideo.EQUILIBRADO)
    val perfilVideo: StateFlow<PerfilVideo> = _perfilVideo.asStateFlow()
    private val _modoClipboard = MutableStateFlow(ModoClipboard.MANUAL)
    val modoClipboard: StateFlow<ModoClipboard> = _modoClipboard.asStateFlow()

    private var socket: SSLSocket? = null
    private var writer: BufferedWriter? = null
    private var streamSocket: SSLSocket? = null
    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null
    private var estatisticasJob: Job? = null
    private var contexto: Context? = null
    private val conectando = AtomicBoolean(false)
    @Volatile private var desconexaoManual = false
    @Volatile private var ultimoPc: PcEncontrado? = null
    private var tentativaReconexao = 0

    private val filaEntrada = FilaEntrada()
    private val tickerEntradaAtivo = AtomicBoolean(false)
    /** Botões que o dedo deixou pressionados no PC — precisam ser soltos ao cair a sessão. */
    private val botoesPressionados = Collections.synchronizedSet(LinkedHashSet<String>())

    /**
     * Decodificar JPEG na thread da interface é o que faz a tela parecer travada:
     * a 24 fps o desenho fica esperando o decodificador. Aqui ele tem thread só
     * dele, um pouco acima da prioridade normal.
     */
    private val despachanteVideo by lazy {
        Executors.newSingleThreadExecutor { tarefa ->
            Thread(tarefa, "pcflow-video").apply { isDaemon = true; priority = Thread.NORM_PRIORITY + 1 }
        }.asCoroutineDispatcher()
    }
    private val quadrosNaJanela = AtomicInteger(0)
    private val bytesNaJanela = AtomicLong(0)
    @Volatile private var instanteUltimoQuadro = 0L
    @Volatile private var instanteUltimoPing = 0L
    @Volatile private var latenciaMs = 0

    fun inicializar(context: Context) {
        contexto = context.applicationContext
        val p = prefs()
        _perfilVideo.value = runCatching { PerfilVideo.valueOf(p.getString("perfil_video", "") ?: "") }
            .getOrDefault(PerfilVideo.EQUILIBRADO)
        _modoClipboard.value = runCatching { ModoClipboard.valueOf(p.getString("modo_clipboard", "") ?: "") }
            .getOrDefault(ModoClipboard.MANUAL)
    }

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
                botoesPressionados.clear()
                filaEntrada.limpar()
                _estatisticas.value = EstatisticasSessao()
                _estado.value = EstadoSessao(EstadoConexao.CONECTADO, pc, MENSAGEM_CONECTADO, sessaoId, monitores, permissoes)
                contexto?.let { ServicoConexao.iniciar(it) }
                iniciarHeartbeat()
                iniciarEstatisticas()
                if (permissoes.clipboard) definirModoClipboard(_modoClipboard.value)
                if (permissoes.tela) iniciarStream(pc, sessaoId, 0)

                while (novoSocket.isConnected && !novoSocket.isClosed) {
                    val linha = reader.readLine() ?: throw EOFException("O computador encerrou a sessão")
                    if (linha.isBlank()) continue
                    val evento = JSONObject(linha)
                    when (evento.optString("tipo")) {
                        // Em modo automático o PC empurra a área de transferência dele sozinho.
                        "clipboard" -> _clipboardRemoto.value = evento.optString("texto", "")
                        "pong" -> registrarPong(evento.optLong("t", 0L))
                    }
                }
            } catch (e: Exception) {
                if (!desconexaoManual) _estado.value = EstadoSessao(EstadoConexao.ERRO, pc, mensagemAmigavel(e))
            } finally {
                conectando.set(false)
                // Antes de fechar: solta o que ficou pressionado, senão o PC fica
                // com o botão preso e arrastando ícones sozinho.
                soltarTudoPendente()
                pararHeartbeat()
                pararEstatisticas()
                pararStream()
                desconectarSockets()
                if (!desconexaoManual && ultimoPc == pc) agendarReconexao(pc)
            }
        }
    }

    /** O ping também é o cronômetro da latência mostrada na interface. */
    private fun iniciarHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = escopo.launch {
            while (isActive && _estado.value.estado == EstadoConexao.CONECTADO) {
                delay(INTERVALO_PING_MS)
                val agora = System.currentTimeMillis()
                try {
                    escreverLinha(JSONObject().put("tipo", "ping").put("t", agora))
                    instanteUltimoPing = agora
                } catch (_: Exception) { break }
            }
        }
    }

    private fun pararHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** O servidor devolve o `t` que mandamos; quando não devolve, vale o último envio. */
    private fun registrarPong(carimbo: Long) {
        val partida = if (carimbo > 0) carimbo else instanteUltimoPing
        if (partida <= 0) return
        val ida = System.currentTimeMillis() - partida
        if (ida in 0..60_000) latenciaMs = ida.toInt()
    }

    private fun iniciarEstatisticas() {
        estatisticasJob?.cancel()
        estatisticasJob = escopo.launch {
            while (isActive && _estado.value.estado == EstadoConexao.CONECTADO) {
                delay(1_000)
                val quadros = quadrosNaJanela.getAndSet(0)
                val bytes = bytesNaJanela.getAndSet(0)
                _estatisticas.value = EstatisticasSessao(quadros, latenciaMs, bytes)

                // Tela parada sem aviso parece aplicativo travado. Se o canal de
                // vídeo sumiu, a interface precisa poder dizer que está voltando.
                val atual = _estado.value
                if (atual.estado != EstadoConexao.CONECTADO || !atual.permissoes.tela) continue
                val semQuadro = instanteUltimoQuadro > 0 &&
                    System.currentTimeMillis() - instanteUltimoQuadro > LIMITE_SEM_QUADRO_MS
                val mensagem = if (semQuadro) MENSAGEM_SEM_TELA else MENSAGEM_CONECTADO
                if (atual.mensagem != mensagem && (atual.mensagem == MENSAGEM_CONECTADO || atual.mensagem == MENSAGEM_SEM_TELA))
                    _estado.value = atual.copy(mensagem = mensagem)
            }
        }
    }

    private fun pararEstatisticas() {
        estatisticasJob?.cancel()
        estatisticasJob = null
        quadrosNaJanela.set(0)
        bytesNaJanela.set(0)
        _estatisticas.value = EstatisticasSessao()
    }

    /**
     * Mensagem avulsa (mídia, energia, arquivos). Vai pela mesma fila da entrada
     * para nunca ultrapassar um movimento que já estava esperando.
     */
    fun enviar(tipo: String, preencher: JSONObject.() -> Unit = {}) {
        if (_estado.value.estado != EstadoConexao.CONECTADO) return
        enfileirar(EventoEntrada.Bruto(JSONObject().put("tipo", tipo).apply(preencher)))
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

    /** Mesmo destino de `alterarMonitor`; nome novo do contrato v2. */
    fun alternarMonitor(indice: Int) = alterarMonitor(indice)

    // ---- ponteiro ----

    /** Posiciona o ponteiro do PC sem clicar, em coordenada normalizada 0..1. */
    fun posicionar(x: Double, y: Double, monitor: Int) = enfileirar(EventoEntrada.Absoluto(x, y, monitor))

    /** Nome antigo de `posicionar`, ainda usado pela tela de sessão. */
    fun enviarPosicao(x: Double, y: Double, monitor: Int) = posicionar(x, y, monitor)

    /** Movimento relativo, do modo touchpad. */
    fun mover(dx: Double, dy: Double) = enfileirar(EventoEntrada.Relativo(dx, dy))

    /**
     * `cliques = 2` manda um clique duplo só, resolvido no Windows com o
     * intervalo do sistema. Dois cliques separados daqui viram dois cliques
     * soltos e não abrem pasta nem selecionam palavra.
     */
    fun clicar(botao: String, cliques: Int = 1) =
        enfileirar(EventoEntrada.Clique(MontadorMensagens.normalizarBotao(botao), cliques))

    fun pressionar(botao: String) {
        val normalizado = MontadorMensagens.normalizarBotao(botao)
        botoesPressionados.add(normalizado)
        enfileirar(EventoEntrada.Pressao(normalizado, solta = false))
    }

    fun soltar(botao: String) {
        val normalizado = MontadorMensagens.normalizarBotao(botao)
        botoesPressionados.remove(normalizado)
        enfileirar(EventoEntrada.Pressao(normalizado, solta = true))
    }

    fun rolar(delta: Int, horizontal: Boolean = false) = enfileirar(EventoEntrada.Rolagem(delta, horizontal))

    // ---- teclado ----

    fun digitar(texto: String) {
        if (texto.isEmpty()) return
        enfileirar(EventoEntrada.Texto(texto))
    }

    fun teclaEspecial(tecla: String, modificadores: List<String> = emptyList()) {
        if (tecla.isBlank()) return
        enfileirar(EventoEntrada.Tecla(tecla, modificadores))
    }

    // ---- área de transferência ----

    fun definirModoClipboard(modo: ModoClipboard) {
        _modoClipboard.value = modo
        runCatching { prefs().edit().putString("modo_clipboard", modo.name).apply() }
        if (_estado.value.estado == EstadoConexao.CONECTADO)
            enfileirar(EventoEntrada.Bruto(MontadorMensagens.clipboardModo(modo)))
    }

    fun enviarClipboardParaPc(texto: String) = enviarClipboard(texto)

    fun puxarClipboardDoPc() = solicitarClipboard()

    fun solicitarClipboard() = enviar("clipboard_get")
    fun enviarClipboard(texto: String) = enviar("clipboard_set") { put("texto", texto) }

    // ---- fila de saída ----

    /**
     * Entrada do usuário nunca é escrita direto no socket: entra na fila e sai
     * por um único caminho. Assim a ordem que o dedo produziu é a ordem que o
     * PC recebe, mesmo com várias corrotinas empurrando ao mesmo tempo.
     */
    private fun enfileirar(evento: EventoEntrada) {
        if (_estado.value.estado != EstadoConexao.CONECTADO) return
        if (filaEntrada.enfileirar(evento)) escopo.launch { despachar() }
        else garantirTickerEntrada()
    }

    /** Movimento e rolagem esperam no máximo um quadro para sair somados. */
    private fun garantirTickerEntrada() {
        if (!tickerEntradaAtivo.compareAndSet(false, true)) return
        escopo.launch {
            try {
                while (isActive) {
                    delay(INTERVALO_AGRUPAMENTO_MS)
                    if (filaEntrada.vazia) break
                    despachar()
                }
            } finally {
                tickerEntradaAtivo.set(false)
                if (!filaEntrada.vazia) garantirTickerEntrada()
            }
        }
    }

    /**
     * Drenar e escrever acontecem sob o mesmo cadeado: se duas corrotinas
     * despacharem juntas, uma delas leva a fila inteira na ordem e a outra não
     * acha nada — nunca dá para uma mensagem passar na frente da outra.
     */
    @Synchronized private fun despachar() {
        if (_estado.value.estado != EstadoConexao.CONECTADO) { filaEntrada.limpar(); return }
        for (evento in filaEntrada.drenar()) {
            try {
                escreverLinha(evento.paraJson())
            } catch (_: Exception) {
                filaEntrada.limpar()
                if (!desconexaoManual) _estado.value = _estado.value.copy(
                    estado = EstadoConexao.ERRO,
                    mensagem = "Conexão perdida. Tentando reconectar…"
                )
                return
            }
        }
    }

    /**
     * Solta o que ficou pressionado. Os modificadores não precisam de mensagem:
     * o contrato manda `tecla` com a lista dentro, e o Windows já solta na ordem
     * inversa dentro da mesma mensagem — nenhum fica pendurado aqui.
     */
    private fun soltarTudoPendente() {
        val presos = synchronized(botoesPressionados) { botoesPressionados.toList() }
        botoesPressionados.clear()
        filaEntrada.limpar()
        if (presos.isEmpty()) return
        for (botao in presos.asReversed())
            runCatching { escreverLinha(MontadorMensagens.mouseSoltar(botao)) }
    }

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
            soltarTudoPendente()
            pararHeartbeat()
            pararEstatisticas()
            pararStream()
            desconectarSockets()
            _quadro.value = null
            _arquivos.value = EstadoArquivos()
            _estado.value = EstadoSessao()
            contexto?.let { ServicoConexao.parar(it) }
        }
    }

    /** Troca o ajuste de captura e reabre o canal de tela com os números do perfil. */
    fun definirPerfilVideo(perfil: PerfilVideo) {
        if (_perfilVideo.value == perfil) return
        _perfilVideo.value = perfil
        runCatching { prefs().edit().putString("perfil_video", perfil.name).apply() }
        val atual = _estado.value
        val pc = atual.pc ?: return
        val sessao = atual.sessaoId ?: return
        if (atual.estado == EstadoConexao.CONECTADO && atual.permissoes.tela)
            iniciarStream(pc, sessao, _monitorAtual.value)
    }

    /**
     * Canal de tela. O laço de rede só lê bytes e entrega o quadro mais recente;
     * quem decodifica é outra corrotina, em thread própria. Quadro que chega
     * enquanto o anterior ainda não foi decodificado atropela o anterior — fila
     * de quadro velho vira atraso acumulado, que é exatamente o que faz a tela
     * parecer travada.
     */
    private fun iniciarStream(pc: PcEncontrado, sessaoId: String, monitor: Int) {
        streamJob?.cancel()
        try { streamSocket?.close() } catch (_: Exception) { }
        val perfil = _perfilVideo.value
        instanteUltimoQuadro = System.currentTimeMillis()
        streamJob = escopo.launch {
            val caixa = CaixaUltimoQuadro<QuadroBruto>()
            val aviso = Channel<Unit>(Channel.CONFLATED)
            val decodificador = launch(despachanteVideo) { decodificarEnquantoChega(caixa, aviso, perfil) }
            var atraso = 300L
            try {
                while (isActive && _estado.value.estado == EstadoConexao.CONECTADO && _estado.value.sessaoId == sessaoId && _monitorAtual.value == monitor) {
                    try {
                        val s = abrirTls(pc, pc.portaTela)
                        streamSocket = s
                        val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                        out.write(MontadorMensagens.pedidoStream(sessaoId, monitor, perfil).toString())
                        out.newLine(); out.flush()
                        val input = DataInputStream(BufferedInputStream(s.getInputStream(), 128 * 1024))
                        atraso = 300L
                        var reserva: ByteArray? = null
                        while (isActive && !s.isClosed && _monitorAtual.value == monitor) {
                            val tamanho = input.readInt()
                            if (tamanho <= 0 || tamanho > 16_000_000) error("Quadro inválido")
                            val destino = reserva?.takeIf { it.size >= tamanho } ?: ByteArray(tamanho)
                            reserva = null
                            input.readFully(destino, 0, tamanho)
                            bytesNaJanela.addAndGet(tamanho.toLong())
                            // O quadro atropelado morre aqui, e o buffer dele volta para o reúso.
                            reserva = caixa.publicar(QuadroBruto(destino, tamanho))?.bytes
                            aviso.trySend(Unit)
                        }
                    } catch (_: CancellationException) { break }
                    catch (_: Exception) {
                        try { streamSocket?.close() } catch (_: Exception) { }
                        delay(atraso)
                        // Espera progressiva, mas com teto baixo: a interface avisa
                        // que está reconectando depois de 5 s sem quadro.
                        atraso = (atraso * 2).coerceAtMost(3000)
                    }
                }
            } finally {
                decodificador.cancel()
                aviso.close()
            }
        }
    }

    /**
     * Decodifica sempre o quadro mais recente e reaproveita o Bitmap anterior.
     * Sem `inBitmap` cada quadro aloca alguns megabytes: a 24 fps isso enche o
     * coletor de lixo e a interface engasga entre um quadro e outro.
     */
    private suspend fun decodificarEnquantoChega(
        caixa: CaixaUltimoQuadro<QuadroBruto>,
        aviso: Channel<Unit>,
        perfil: PerfilVideo
    ) {
        val rodizio = RodizioDeQuadros<Bitmap>(3)
        val livres = ArrayDeque<Bitmap>()
        // Metade da memória e decodificação bem mais rápida quando o que importa
        // é a resposta; só o perfil de imagem paga o preço da cor completa.
        val formato = if (perfil == PerfilVideo.IMAGEM) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        try {
            for (@Suppress("UNUSED_PARAMETER") ignorado in aviso) {
                var bruto = caixa.consumir()
                while (bruto != null) {
                    val bitmap = decodificarQuadro(bruto, livres, formato)
                    if (bitmap != null) {
                        rodizio.publicar(bitmap)?.let { if (livres.size < 2) livres.addLast(it) }
                        _quadro.value = bitmap
                        quadrosNaJanela.incrementAndGet()
                        instanteUltimoQuadro = System.currentTimeMillis()
                    }
                    // Enquanto decodificava pode ter chegado outro: pega o mais novo.
                    bruto = caixa.consumir()
                }
            }
        } catch (_: CancellationException) {
            // encerramento normal do canal de tela
        } finally {
            rodizio.limpar()
            livres.clear()
        }
    }

    private fun decodificarQuadro(bruto: QuadroBruto, livres: ArrayDeque<Bitmap>, formato: Bitmap.Config): Bitmap? {
        val opcoes = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = formato
            inSampleSize = 1
        }
        val candidato = livres.removeFirstOrNull()
        if (candidato != null && !candidato.isRecycled && candidato.isMutable && candidato.config == formato)
            opcoes.inBitmap = candidato

        return try {
            BitmapFactory.decodeByteArray(bruto.bytes, 0, bruto.tamanho, opcoes)
        } catch (_: IllegalArgumentException) {
            // Reúso só vale quando o tamanho bate. Mudou a resolução do PC:
            // joga fora os buffers antigos e aloca de novo.
            livres.clear()
            opcoes.inBitmap = null
            runCatching { BitmapFactory.decodeByteArray(bruto.bytes, 0, bruto.tamanho, opcoes) }.getOrNull()
        } catch (_: Exception) {
            null
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
