package com.ander.pcflow.rede

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.ander.pcflow.ServicoConexao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.ArrayDeque
import java.util.UUID

/**
 * Sessão de controle com o PC.
 *
 * Problemas da versão anterior que estavam derrubando a conexão:
 *  - cada evento de toque criava uma corrotina própria, então os deltas do mouse
 *    chegavam fora de ordem e o ponteiro tremia; agora existe **um** escritor
 *    com fila ordenada e junção de movimentos consecutivos;
 *  - não havia heartbeat, então uma queda de Wi-Fi deixava o socket "vivo" para
 *    sempre e o app ficava mudo; agora há ping/pong com limite de silêncio;
 *  - a reconexão insistia no IP antigo; agora ela redescobre o PC pelo nome;
 *  - o token era guardado por IP e se perdia quando o DHCP mudava o endereço;
 *    agora a chave é o nome da máquina.
 */
// Guarda apenas o applicationContext, que vive tanto quanto o processo:
// não há vazamento de Activity aqui, então o aviso do lint não se aplica.
@SuppressLint("StaticFieldLeak")
object SessaoPcFlow {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _estado = MutableStateFlow(EstadoSessao())
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()

    private val _pcs = MutableStateFlow<List<PcEncontrado>>(emptyList())
    val pcs: StateFlow<List<PcEncontrado>> = _pcs.asStateFlow()

    private val _eventos = MutableStateFlow<EventoPc?>(null)
    val eventos: StateFlow<EventoPc?> = _eventos.asStateFlow()

    private var contexto: Context? = null
    private var socket: Socket? = null
    private var escritor: BufferedWriter? = null

    private var jobConexao: Job? = null
    private var jobDescoberta: Job? = null

    private val fila = ArrayDeque<JSONObject>()
    private val travaFila = Object()

    @Volatile private var desconexaoManual = false
    @Volatile private var alvo: PcEncontrado? = null
    @Volatile private var ultimoPong = 0L
    private var tentativas = 0

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    fun inicializar(context: Context) {
        if (contexto == null) {
            contexto = context.applicationContext
            _pcs.value = pcsSalvos()
        }
    }

    private fun prefs(): SharedPreferences =
        requireNotNull(contexto) { "SessaoPcFlow.inicializar não foi chamado" }
            .getSharedPreferences("pcflow", Context.MODE_PRIVATE)

    private fun dispositivoId(): String {
        val p = prefs()
        return p.getString("dispositivo_id", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("dispositivo_id", it).apply()
        }
    }

    private fun chaveToken(pc: PcEncontrado) = "token_" + pc.nome.lowercase().trim()

    private fun tokenDe(pc: PcEncontrado): String? =
        prefs().getString(chaveToken(pc), null) ?: prefs().getString("token_${pc.host}", null)

    // ------------------------------------------------------------------
    // Descoberta
    // ------------------------------------------------------------------

    fun descobrir(duracaoMs: Long = 2_500) {
        val ctx = contexto ?: return
        if (jobDescoberta?.isActive == true) return
        jobDescoberta = escopo.launch {
            if (!_estado.value.conectado) {
                _estado.value = _estado.value.copy(
                    estado = EstadoConexao.PROCURANDO,
                    mensagem = "Procurando PCs na rede…"
                )
            }
            val achados = runCatching {
                Descoberta.procurar(ctx, duracaoMs) { pc -> juntarPc(pc) }
            }.getOrElse {
                _estado.value = _estado.value.copy(
                    estado = EstadoConexao.ERRO,
                    mensagem = "Falha ao procurar na rede: ${it.message ?: "erro desconhecido"}"
                )
                emptyList()
            }

            if (!_estado.value.conectado) {
                _estado.value = _estado.value.copy(
                    estado = EstadoConexao.DESCONECTADO,
                    mensagem = if (achados.isEmpty() && _pcs.value.isEmpty())
                        "Nenhum PC encontrado. Confira se o PCFlow está aberto no computador e se os dois estão no mesmo Wi‑Fi."
                    else ""
                )
            }
        }
    }

    private fun juntarPc(pc: PcEncontrado) {
        val atual = _pcs.value.toMutableList()
        val i = atual.indexOfFirst { it.host == pc.host || it.nome.equals(pc.nome, true) }
        if (i >= 0) atual[i] = pc.copy(salvo = atual[i].salvo) else atual.add(pc)
        _pcs.value = atual
    }

    fun adicionarManual(pc: PcEncontrado) {
        juntarPc(pc.copy(salvo = true))
        salvarPc(pc)
    }

    private fun pcsSalvos(): List<PcEncontrado> = runCatching {
        val bruto = prefs().getString("pcs_salvos", "[]") ?: "[]"
        val array = JSONArray(bruto)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PcEncontrado(
                nome = o.optString("nome"),
                host = o.optString("host"),
                porta = o.optInt("porta", Protocolo.PORTA_CONTROLE),
                salvo = true
            )
        }
    }.getOrDefault(emptyList())

    private fun salvarPc(pc: PcEncontrado) {
        val lista = pcsSalvos().filterNot { it.nome.equals(pc.nome, true) } + pc.copy(salvo = true)
        val array = JSONArray()
        lista.takeLast(8).forEach {
            array.put(
                JSONObject()
                    .put("nome", it.nome).put("host", it.host).put("porta", it.porta)
            )
        }
        prefs().edit().putString("pcs_salvos", array.toString()).apply()
    }

    fun esquecerPc(pc: PcEncontrado) {
        prefs().edit()
            .remove(chaveToken(pc))
            .remove("token_${pc.host}")
            .apply()
        val lista = pcsSalvos().filterNot { it.nome.equals(pc.nome, true) }
        val array = JSONArray()
        lista.forEach {
            array.put(JSONObject().put("nome", it.nome).put("host", it.host).put("porta", it.porta))
        }
        prefs().edit().putString("pcs_salvos", array.toString()).apply()
        _pcs.value = _pcs.value.filterNot { it.nome.equals(pc.nome, true) }
    }

    /** Tenta reconectar sozinho ao último PC usado, sem pedir PIN. */
    fun reconectarAutomaticamente() {
        if (_estado.value.conectado || jobConexao?.isActive == true) return
        val nome = prefs().getString("ultimo_pc", null) ?: return
        val pc = pcsSalvos().firstOrNull { it.nome.equals(nome, true) } ?: return
        if (tokenDe(pc) == null) return
        conectar(pc, null)
    }

    // ------------------------------------------------------------------
    // Conexão
    // ------------------------------------------------------------------

    fun conectar(pc: PcEncontrado, pin: String?) {
        desconexaoManual = false
        alvo = pc
        tentativas = 0
        jobConexao?.cancel()
        jobConexao = escopo.launch { laco(pc, pin) }
    }

    fun desconectar() {
        desconexaoManual = true
        alvo = null
        jobConexao?.cancel()
        jobConexao = null
        fecharSocket()
        _estado.value = EstadoSessao(mensagem = "Desconectado")
        contexto?.let { runCatching { ServicoConexao.parar(it) } }
    }

    /**
     * Laço de conexão + reconexão automática. Sai apenas quando o usuário
     * desconecta ou o PC recusa de forma definitiva (PIN errado, bloqueado).
     */
    private suspend fun laco(inicial: PcEncontrado, pinInicial: String?) {
        var pc = inicial
        var pin = pinInicial

        while (escopo.isActive && !desconexaoManual) {
            val resultado = conectarUmaVez(pc, pin)
            pin = null // o PIN vale só para o pareamento

            when (resultado) {
                Resultado.RECUSADO -> return
                Resultado.OK, Resultado.QUEDA -> {
                    if (desconexaoManual) return
                    tentativas++
                }
            }

            val espera = minOf(15_000L, 800L * (1L shl minOf(tentativas, 4)))
            _estado.value = _estado.value.copy(
                estado = EstadoConexao.RECONECTANDO,
                pc = pc,
                mensagem = "Conexão perdida. Tentando de novo em ${espera / 1000}s…",
                latenciaMs = -1
            )
            delay(espera)
            if (desconexaoManual) return

            // O IP pode ter mudado (DHCP, troca de rede). Redescobre pelo nome.
            contexto?.let { ctx ->
                _estado.value = _estado.value.copy(mensagem = "Procurando o PC na rede…")
                val achados = runCatching { Descoberta.procurar(ctx, 2_000) { juntarPc(it) } }
                    .getOrDefault(emptyList())
                achados.firstOrNull { it.nome.equals(pc.nome, true) }?.let { pc = it }
            }
        }
    }

    private enum class Resultado { OK, QUEDA, RECUSADO }

    private suspend fun conectarUmaVez(pc: PcEncontrado, pin: String?): Resultado =
        withContext(Dispatchers.IO) {
            _estado.value = _estado.value.copy(
                estado = EstadoConexao.CONECTANDO, pc = pc,
                mensagem = "Conectando a ${pc.nome}…", latenciaMs = -1
            )

            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                s.soTimeout = Protocolo.TIMEOUT_LEITURA_MS
                s.connect(InetSocketAddress(pc.host, pc.porta), Protocolo.TIMEOUT_CONEXAO_MS)
            } catch (e: Exception) {
                s.runCatching { close() }
                _estado.value = _estado.value.copy(
                    estado = EstadoConexao.ERRO, pc = pc,
                    mensagem = "Não foi possível falar com ${pc.host}:${pc.porta}. " +
                        "Verifique se o PCFlow está aberto no PC e se o firewall liberou a porta."
                )
                return@withContext Resultado.QUEDA
            }

            val leitor = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val escreve = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            socket = s
            escritor = escreve
            synchronized(travaFila) { fila.clear() }

            try {
                // --- handshake ---
                val ola = Protocolo.ola(
                    dispositivoId = dispositivoId(),
                    nome = nomeDoCelular(),
                    modelo = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    token = tokenDe(pc),
                    pin = pin
                )
                escreve.write(ola.toString()); escreve.newLine(); escreve.flush()

                val respostaBruta = leitor.readLine()
                    ?: throw IllegalStateException("O PC fechou a conexão sem responder.")
                val resposta = JSONObject(respostaBruta)

                when (resposta.optString("tipo")) {
                    "pareado", "conectado" -> {
                        resposta.optString("token").takeIf { it.isNotBlank() }?.let {
                            prefs().edit().putString(chaveToken(pc), it).apply()
                        }
                        val nomePc = resposta.optString("nome").ifBlank { pc.nome }
                        val confirmado = pc.copy(nome = nomePc, salvo = true)
                        alvo = confirmado
                        salvarPc(confirmado)
                        prefs().edit().putString("ultimo_pc", nomePc).apply()
                        juntarPc(confirmado)

                        val r = resposta.optJSONObject("recursos")
                        tentativas = 0
                        ultimoPong = System.currentTimeMillis()
                        _estado.value = EstadoSessao(
                            estado = EstadoConexao.CONECTADO,
                            pc = confirmado,
                            mensagem = "Conectado",
                            recursos = Recursos(
                                arquivos = r?.optBoolean("arquivos") ?: false,
                                tela = r?.optBoolean("tela") ?: false,
                                energia = r?.optBoolean("energia") ?: true,
                                areaTransferencia = r?.optBoolean("areaTransferencia") ?: false,
                                atalhos = r?.optBoolean("atalhos") ?: false
                            )
                        )
                        contexto?.let { runCatching { ServicoConexao.iniciar(it, nomePc) } }
                    }
                    else -> {
                        val motivo = resposta.optString("mensagem")
                            .ifBlank { "O PC recusou a conexão." }
                        // Token inválido: apaga para a próxima tentativa pedir o PIN.
                        if (resposta.optString("codigo") == "naoautorizado") {
                            prefs().edit().remove(chaveToken(pc)).remove("token_${pc.host}").apply()
                        }
                        _estado.value = EstadoSessao(
                            estado = EstadoConexao.ERRO, pc = pc, mensagem = motivo
                        )
                        return@withContext Resultado.RECUSADO
                    }
                }

                // --- laço de tráfego ---
                val escritorJob = escopo.launch { lacoEscrita(escreve) }
                val pingJob = escopo.launch { lacoPing() }
                try {
                    while (isActive) {
                        val linha = leitor.readLine() ?: break
                        if (linha.isNotBlank()) tratarDoPc(linha)
                    }
                } finally {
                    escritorJob.cancel()
                    pingJob.cancel()
                }
                Resultado.QUEDA
            } catch (e: Exception) {
                if (!desconexaoManual) {
                    _estado.value = _estado.value.copy(
                        estado = EstadoConexao.ERRO, pc = pc,
                        mensagem = e.message ?: "Conexão interrompida."
                    )
                }
                Resultado.QUEDA
            } finally {
                fecharSocket()
                contexto?.let { runCatching { ServicoConexao.parar(it) } }
            }
        }

    private fun nomeDoCelular(): String {
        val salvo = prefs().getString("nome_dispositivo", null)
        if (!salvo.isNullOrBlank()) return salvo
        return Build.MODEL?.takeIf { it.isNotBlank() } ?: "Celular Android"
    }

    fun definirNomeDoCelular(nome: String) {
        prefs().edit().putString("nome_dispositivo", nome.trim().take(40)).apply()
    }

    fun nomeDoCelularAtual(): String = nomeDoCelular()

    private fun fecharSocket() {
        runCatching { escritor?.close() }
        runCatching { socket?.close() }
        escritor = null
        socket = null
    }

    // ------------------------------------------------------------------
    // Escrita ordenada
    // ------------------------------------------------------------------

    private suspend fun lacoEscrita(saida: BufferedWriter) {
        while (escopo.isActive) {
            val proxima = synchronized(travaFila) { fila.pollFirst() }
            if (proxima == null) {
                delay(4)
                continue
            }
            try {
                saida.write(proxima.toString())
                saida.newLine()
                // Esvazia o resto da fila antes do flush: menos syscalls por gesto.
                var extra = synchronized(travaFila) { fila.pollFirst() }
                var contador = 0
                while (extra != null && contador < 32) {
                    saida.write(extra.toString())
                    saida.newLine()
                    extra = synchronized(travaFila) { fila.pollFirst() }
                    contador++
                }
                saida.flush()
            } catch (e: Exception) {
                runCatching { socket?.close() } // força o leitor a sair e reconectar
                return
            }
        }
    }

    private suspend fun lacoPing() {
        while (escopo.isActive) {
            delay(Protocolo.INTERVALO_PING_MS)
            if (!_estado.value.conectado) continue
            enfileirar(JSONObject().put("tipo", "ping").put("t", System.currentTimeMillis()))
            if (System.currentTimeMillis() - ultimoPong > Protocolo.LIMITE_SEM_PONG_MS) {
                // O PC parou de responder: derruba para o laço de reconexão assumir.
                runCatching { socket?.close() }
                return
            }
        }
    }

    private fun enfileirar(json: JSONObject) {
        synchronized(travaFila) {
            // Movimentos consecutivos viram um só: evita fila crescendo em arrasto rápido.
            if (json.optString("tipo") == "mouse_move") {
                val ultimo = fila.peekLast()
                if (ultimo != null && ultimo.optString("tipo") == "mouse_move") {
                    ultimo.put("dx", ultimo.optDouble("dx") + json.optDouble("dx"))
                    ultimo.put("dy", ultimo.optDouble("dy") + json.optDouble("dy"))
                    return
                }
            }
            if (fila.size > 512) fila.pollFirst()
            fila.addLast(json)
        }
    }

    // ------------------------------------------------------------------
    // Envio de comandos
    // ------------------------------------------------------------------

    fun enviar(tipo: String, preencher: JSONObject.() -> Unit = {}) {
        if (!_estado.value.conectado) return
        enfileirar(JSONObject().put("tipo", tipo).apply(preencher))
    }

    fun mover(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        enviar("mouse_move") { put("dx", dx.toDouble()); put("dy", dy.toDouble()) }
    }

    fun clicar(botao: String = "left", acao: String = "click") =
        enviar("mouse_click") { put("botao", botao); put("acao", acao) }

    fun rolar(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        enviar("scroll") { put("dx", dx); put("dy", dy) }
    }

    fun digitar(texto: String) {
        if (texto.isEmpty()) return
        // Envia em blocos: o PC digita caractere a caractere e blocos gigantes travam.
        texto.chunked(200).forEach { pedaco -> enviar("texto") { put("texto", pedaco) } }
    }

    fun tecla(tecla: String, modificadores: List<String> = emptyList()) = enviar("tecla") {
        put("tecla", tecla)
        if (modificadores.isNotEmpty()) put("mods", JSONArray(modificadores))
    }

    fun atalho(combo: String) = enviar("atalho") { put("acao", combo) }
    fun midia(acao: String) = enviar("media") { put("acao", acao) }
    fun energia(acao: String) = enviar("power") { put("acao", acao) }

    fun listarAplicativos() = enviar("app_listar")
    fun abrirAplicativo(id: String) = enviar("app_abrir") { put("acao", id) }

    fun pedirAreaTransferencia() = enviar("clipboard_pedir")
    fun enviarAreaTransferencia(texto: String) = enviar("clipboard_enviar") { put("texto", texto) }

    fun listarArquivos(caminho: String?) = enviar("arq_listar") { put("caminho", caminho ?: "") }
    fun baixarArquivo(caminho: String, offset: Long = 0) = enviar("arq_baixar") {
        put("caminho", caminho); put("offset", offset)
    }

    fun iniciarTela(largura: Int, qualidade: Int, fps: Int) = enviar("tela_iniciar") {
        put("largura", largura); put("qualidade", qualidade); put("fps", fps)
    }

    fun pararTela() = enviar("tela_parar")

    // ------------------------------------------------------------------
    // Recebimento
    // ------------------------------------------------------------------

    private fun tratarDoPc(linha: String) {
        val json = runCatching { JSONObject(linha) }.getOrNull() ?: return
        when (json.optString("tipo")) {
            "pong" -> {
                ultimoPong = System.currentTimeMillis()
                val enviado = json.optLong("t")
                if (enviado > 0) {
                    _estado.value = _estado.value.copy(
                        latenciaMs = (System.currentTimeMillis() - enviado).coerceAtLeast(0)
                    )
                }
            }
            "clipboard" -> _eventos.value = EventoPc.AreaTransferencia(json.optString("texto"))
            "aviso" -> _eventos.value = EventoPc.Aviso(json.optString("mensagem"))
            "app_lista" -> _eventos.value = EventoPc.Aplicativos(itensDe(json))
            "arq_lista" -> _eventos.value =
                EventoPc.Arquivos(json.optString("caminho"), itensDe(json))
            "arq_erro" -> _eventos.value = EventoPc.Aviso(json.optString("mensagem"))
            "arq_dados" -> _eventos.value = EventoPc.BlocoArquivo(
                caminho = json.optString("caminho"),
                offset = json.optLong("offset"),
                tamanho = json.optLong("tamanho"),
                dadosBase64 = json.optString("dados"),
                fim = json.optBoolean("fim")
            )
            "tela_quadro" -> _eventos.value = EventoPc.QuadroTela(
                json.optString("dados"), json.optInt("largura"), json.optInt("altura")
            )
        }
    }

    private fun itensDe(json: JSONObject): List<ItemRemoto> {
        val array = json.optJSONArray("itens") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ItemRemoto(
                nome = o.optString("nome"),
                caminho = o.optString("caminho"),
                pasta = o.optBoolean("pasta"),
                tamanho = o.optLong("tamanho")
            )
        }
    }

    fun consumirEvento() { _eventos.value = null }
}

data class ItemRemoto(
    val nome: String,
    val caminho: String,
    val pasta: Boolean,
    val tamanho: Long = 0
)

sealed interface EventoPc {
    data class Aviso(val mensagem: String) : EventoPc
    data class AreaTransferencia(val texto: String) : EventoPc
    data class Aplicativos(val itens: List<ItemRemoto>) : EventoPc
    data class Arquivos(val caminho: String, val itens: List<ItemRemoto>) : EventoPc
    data class BlocoArquivo(
        val caminho: String, val offset: Long, val tamanho: Long,
        val dadosBase64: String, val fim: Boolean
    ) : EventoPc
    data class QuadroTela(val jpegBase64: String, val largura: Int, val altura: Int) : EventoPc
}
