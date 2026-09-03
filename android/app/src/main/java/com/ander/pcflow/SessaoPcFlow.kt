package com.ander.pcflow

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object SessaoPcFlow {
    private const val PORTA_DESCOBERTA = 45455
    private const val TEMPO_DESCOBERTA_MS = 1300

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(EstadoSessao())
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()
    private val _pcs = MutableStateFlow<List<PcEncontrado>>(emptyList())
    val pcs: StateFlow<List<PcEncontrado>> = _pcs.asStateFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
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
                    val dados = "PCFLOW_DISCOVER_V1".toByteArray()
                    udp.send(DatagramPacket(dados, dados.size, InetAddress.getByName("255.255.255.255"), PORTA_DESCOBERTA))
                    val inicio = System.currentTimeMillis()
                    while (System.currentTimeMillis() - inicio < TEMPO_DESCOBERTA_MS) {
                        try {
                            val buffer = ByteArray(2048)
                            val pacote = DatagramPacket(buffer, buffer.size)
                            udp.receive(pacote)
                            val json = JSONObject(String(pacote.data, 0, pacote.length))
                            if (json.optString("tipo") == "pcflow") {
                                val host = pacote.address.hostAddress ?: continue
                                encontrados[host] = PcEncontrado(json.optString("nome", host), host, json.optInt("porta", 45456))
                                _pcs.value = encontrados.values.toList()
                            }
                        } catch (_: SocketTimeoutException) { }
                    }
                }
            } catch (e: Exception) {
                _estado.value = EstadoSessao(EstadoConexao.ERRO, mensagem = "Falha na descoberta: ${e.message}")
            }
        }
    }

    fun conectar(pc: PcEncontrado, pin: String? = null) {
        desconexaoManual = false
        ultimoPc = pc
        conectarInterno(pc, pin)
    }

    private fun conectarInterno(pc: PcEncontrado, pin: String? = null) {
        if (!conectando.compareAndSet(false, true)) return
        _estado.value = EstadoSessao(EstadoConexao.CONECTANDO, pc, "Conectando…")
        escopo.launch {
            try {
                desconectarInterno()
                val novoSocket = Socket()
                novoSocket.tcpNoDelay = true
                novoSocket.keepAlive = true
                novoSocket.connect(InetSocketAddress(pc.host, pc.porta), 3000)
                val novoWriter = BufferedWriter(OutputStreamWriter(novoSocket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(novoSocket.getInputStream(), Charsets.UTF_8))
                socket = novoSocket
                writer = novoWriter

                val prefs = prefs()
                val id = prefs.getString("dispositivo_id", null) ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString("dispositivo_id", it).apply()
                }
                val token = prefs.getString("token_${pc.host}", null)
                val ola = JSONObject()
                    .put("tipo", "ola")
                    .put("dispositivoId", id)
                    .put("nome", android.os.Build.MODEL)
                if (token != null) ola.put("token", token)
                if (!pin.isNullOrBlank()) ola.put("pin", pin.replace(" ", ""))
                escreverLinha(ola)

                val resposta = reader.readLine() ?: error("Servidor não respondeu")
                val json = JSONObject(resposta)
                when (json.optString("tipo")) {
                    "pareado" -> {
                        prefs.edit().putString("token_${pc.host}", json.getString("token")).apply()
                        conectado(pc)
                    }
                    "conectado" -> conectado(pc)
                    else -> error(json.optString("mensagem", "Pareamento recusado"))
                }

                while (novoSocket.isConnected && !novoSocket.isClosed) {
                    val linha = reader.readLine() ?: break
                    if (linha.isNotBlank()) { /* reservado para eventos do PC */ }
                }
                if (_estado.value.pc == pc) _estado.value = EstadoSessao(EstadoConexao.DESCONECTADO, pc, "Reconectando…")
            } catch (e: Exception) {
                _estado.value = EstadoSessao(EstadoConexao.ERRO, pc, e.message ?: "Falha ao conectar")
            } finally {
                conectando.set(false)
                if (!desconexaoManual && ultimoPc == pc) agendarReconexao(pc)
            }
        }
    }

    private fun conectado(pc: PcEncontrado) {
        tentativaReconexao = 0
        _estado.value = EstadoSessao(EstadoConexao.CONECTADO, pc, "Conectado")
        contexto?.let { ServicoConexao.iniciar(it) }
    }

    fun enviar(tipo: String, preencher: JSONObject.() -> Unit = {}) {
        if (_estado.value.estado != EstadoConexao.CONECTADO) return
        escopo.launch {
            try {
                escreverLinha(JSONObject().put("tipo", tipo).apply(preencher))
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(estado = EstadoConexao.ERRO, mensagem = "Conexão perdida")
            }
        }
    }

    fun desconectar() {
        desconexaoManual = true
        ultimoPc = null
        escopo.launch {
            desconectarInterno()
            _estado.value = EstadoSessao()
            contexto?.let { ServicoConexao.parar(it) }
        }
    }

    private fun agendarReconexao(pc: PcEncontrado) {
        tentativaReconexao++
        val atraso = minOf(10_000L, 750L * (1 shl minOf(tentativaReconexao, 4)))
        escopo.launch {
            delay(atraso)
            if (!desconexaoManual && ultimoPc == pc && _estado.value.estado != EstadoConexao.CONECTADO) {
                _estado.value = EstadoSessao(EstadoConexao.CONECTANDO, pc, "Reconectando automaticamente…")
                conectarInterno(pc, null)
            }
        }
    }

    private fun desconectarInterno() {
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    @Synchronized private fun escreverLinha(json: JSONObject) {
        writer?.apply {
            write(json.toString())
            newLine()
            flush()
        } ?: error("Sem conexão")
    }

    private fun prefs() = requireNotNull(contexto).getSharedPreferences("pcflow", Context.MODE_PRIVATE)
}
