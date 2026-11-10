package com.ander.pcflow

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ander.pcflow.discovery.DiretorioPcs
import com.ander.pcflow.discovery.ProtocoloDescoberta
import com.ander.pcflow.protocol.Envelope
import com.ander.pcflow.protocol.Protocolo
import com.ander.pcflow.protocol.campos
import com.ander.pcflow.protocol.texto
import com.ander.pcflow.session.ChaveValor
import com.ander.pcflow.session.DiretorioToken
import com.ander.pcflow.session.EventoConexao
import com.ander.pcflow.session.Fase
import com.ander.pcflow.session.FilaSaida
import com.ander.pcflow.session.MaquinaConexao
import com.ander.pcflow.session.MonitorHeartbeat
import com.ander.pcflow.session.PoliticaReconexao
import com.ander.pcflow.session.SegurancaEntrada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object SessaoPcFlow {
    private const val PORTA_DESCOBERTA = 45455
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maquina = MaquinaConexao()
    private val fila = FilaSaida()
    private val seguranca = SegurancaEntrada()
    private val diretorio = DiretorioPcs()
    private val heartbeat = MonitorHeartbeat(2_000, 3)
    private val reconexao = PoliticaReconexao()
    private val _estado = MutableStateFlow(EstadoSessao())
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()
    private val _pcs = MutableStateFlow<List<PcEncontrado>>(emptyList())
    val pcs: StateFlow<List<PcEncontrado>> = _pcs.asStateFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var contexto: Context? = null
    private var tokens: DiretorioToken? = null
    private var dispositivoId: String = ""
    private val conectando = AtomicBoolean(false)
    @Volatile private var desconexaoManual = false
    @Volatile private var ultimoPc: PcEncontrado? = null
    @Volatile private var pararReconexao = false
    private var sequencia = 1L
    private var ultimoEntrada = 0L
    private var ultimoHeartbeatEnviado = 0L

    fun inicializar(context: Context) {
        contexto = context.applicationContext
        val prefs = prefsCifradas(context.applicationContext)
        tokens = DiretorioToken(object : ChaveValor {
            override fun get(chave: String) = prefs.getString(chave, null)
            override fun put(chave: String, valor: String) { prefs.edit().putString(chave, valor).apply() }
        })
        dispositivoId = prefs.getString("dispositivo_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("dispositivo_id", it).apply()
        }
    }

    fun descobrir() {
        maquina.ao(EventoConexao.IniciarDescoberta)
        publicar(mensagem = "Procurando computadores na rede local")
        escopo.launch {
            try {
                DatagramSocket().use { udp ->
                    udp.broadcast = true
                    udp.soTimeout = 250
                    val dados = ProtocoloDescoberta.PEDIDO.toByteArray()
                    udp.send(DatagramPacket(dados, dados.size, InetAddress.getByName("255.255.255.255"), PORTA_DESCOBERTA))
                    val inicio = System.currentTimeMillis()
                    while (System.currentTimeMillis() - inicio < 1300) {
                        try {
                            val buffer = ByteArray(2048)
                            val pacote = DatagramPacket(buffer, buffer.size)
                            udp.receive(pacote)
                            val texto = String(pacote.data, 0, pacote.length)
                            val host = pacote.address.hostAddress ?: continue
                            val anuncio = ProtocoloDescoberta.lerResposta(texto, host, System.currentTimeMillis()) ?: continue
                            diretorio.observar(anuncio)
                            _pcs.value = diretorio.todos().map { PcEncontrado(it.nome, it.host, it.porta, it.deviceId) }
                        } catch (_: SocketTimeoutException) { }
                    }
                }
            } catch (e: SecurityException) {
                maquina.ao(EventoConexao.PermissaoNegada)
                publicar(codigo = "LOCAL_NETWORK_PERMISSION_DENIED", mensagem = Protocolo.texto("LOCAL_NETWORK_PERMISSION_DENIED"))
            } catch (e: Exception) {
                publicar(mensagem = "Falha na descoberta: ${e.message}")
            }
        }
    }

    fun conectar(pc: PcEncontrado, pin: String? = null) {
        desconexaoManual = false
        pararReconexao = false
        ultimoPc = pc
        maquina.ao(EventoConexao.Selecionar)
        conectarInterno(pc, pin)
    }

    fun enviar(tipo: String, preencher: org.json.JSONObject.() -> Unit = {}) {
        if (!maquina.foto.pronta) return
        val legado = org.json.JSONObject().put("tipo", tipo).apply(preencher)
        val acao = legado.optString("acao")
        val nome = when (tipo) {
            "mouse_move" -> "pointer.move"
            "mouse_click" -> "pointer.button"
            "scroll" -> "pointer.scroll"
            "texto" -> "keyboard.text"
            "tecla" -> "keyboard.key"
            "media" -> if (acao.startsWith("volume") || acao == "mute") "media.volume" else "media.transport"
            "power" -> "system.power"
            else -> return
        }
        if (nome == "system.power" && !maquina.foto.aceitaDestrutivo) return
        val payload = when (nome) {
            "pointer.move" -> campos("x" to legado.optDouble("x"), "y" to legado.optDouble("y"))
            "pointer.button" -> campos("button" to legado.optString("botao", "left"), "direction" to "click")
            "pointer.scroll" -> campos("delta" to legado.optInt("delta"))
            "keyboard.text" -> campos("text" to legado.optString("texto"))
            "keyboard.key" -> campos("code" to legado.optString("tecla"), "direction" to "click")
            "media.transport", "media.volume", "system.power" -> campos("action" to acao)
            else -> campos()
        }
        val json = Protocolo.escrever(envelope(nome, payload))
        if (nome == "pointer.move") fila.enfileirarMove(json) else fila.enfileirarDiscreto(json)
        escopo.launch { drenarFila() }
    }

    fun desconectar() {
        desconexaoManual = true
        pararReconexao = true
        ultimoPc = null
        escopo.launch {
            soltarLocal()
            fecharSocket()
            maquina.ao(EventoConexao.Reset)
            publicar()
            contexto?.let { ServicoConexao.parar(it) }
        }
    }

    private fun conectarInterno(pcBruto: PcEncontrado, pin: String?) {
        if (!conectando.compareAndSet(false, true)) return
        val pc = resolver(pcBruto)
        maquina.ao(EventoConexao.IniciarConexao)
        publicar(pc, "Conectando…")
        escopo.launch {
            try {
                fecharSocket()
                val novo = Socket()
                novo.tcpNoDelay = true
                novo.keepAlive = true
                novo.connect(InetSocketAddress(pc.host, pc.porta), 3000)
                socket = novo
                writer = BufferedWriter(OutputStreamWriter(novo.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(novo.getInputStream(), Charsets.UTF_8))
                maquina.ao(EventoConexao.SocketAberto)
                val token = tokens?.ler(pc.deviceId)
                val hello = campos("deviceId" to dispositivoId, "deviceName" to android.os.Build.MODEL, "token" to token)
                escreverLinha(Protocolo.escrever(envelope("session.hello", hello)))
                ultimoEntrada = System.currentTimeMillis()
                heartbeat.visto(ultimoEntrada)
                var pinUsado = false
                while (novo.isConnected && !novo.isClosed) {
                    if (maquina.foto.pronta && heartbeat.observar(System.currentTimeMillis())) {
                        maquina.ao(EventoConexao.HeartbeatPerdido)
                        publicar(pc, "Conexão sem resposta. Reconectando…")
                        break
                    }
                    val agoraLoop = System.currentTimeMillis()
                    if (maquina.foto.pronta && agoraLoop - ultimoHeartbeatEnviado > 2_000) {
                        ultimoHeartbeatEnviado = agoraLoop
                        escreverLinha(Protocolo.escrever(envelope("session.heartbeat", campos())))
                    }
                    if (!reader.ready()) {
                        drenarFila()
                        Thread.sleep(40)
                        continue
                    }
                    val linha = reader.readLine() ?: break
                    if (linha.isBlank()) continue
                    ultimoEntrada = System.currentTimeMillis()
                    val lido = Protocolo.ler(linha)
                    if (!lido.ok || lido.envelope == null) {
                        if (lido.codigo == "PROTOCOL_VERSION_UNSUPPORTED") {
                            maquina.ao(EventoConexao.VersaoRejeitada)
                            pararReconexao = true
                        }
                        publicar(pc, Protocolo.texto(lido.codigo), lido.codigo)
                        break
                    }
                    when (lido.envelope.name) {
                        "session.error" -> {
                            val codigo = lido.envelope.payload.texto("code")
                            if (codigo == "AUTH_REQUIRED" && !pin.isNullOrBlank() && !pinUsado) {
                                pinUsado = true
                                val auth = campos("deviceId" to dispositivoId, "deviceName" to android.os.Build.MODEL, "pin" to pin.trim())
                                escreverLinha(Protocolo.escrever(envelope("session.auth", auth)))
                            } else {
                                tratarErro(pc, codigo)
                                if (codigo == "AUTH_REVOKED" || codigo == "AUTH_REJECTED" || codigo == "PROTOCOL_VERSION_UNSUPPORTED" || codigo == "SESSION_IN_USE" || codigo == "AUTH_REQUIRED") {
                                    pararReconexao = true
                                    break
                                }
                            }
                        }
                        "session.ack" -> {
                            val novoToken = lido.envelope.payload.texto("token")
                            if (novoToken.isNotBlank() && pc.deviceId.isNotBlank()) tokens?.salvar(pc.deviceId, novoToken)
                            heartbeat.visto(System.currentTimeMillis())
                        }
                        "session.capabilities" -> {
                            maquina.ao(EventoConexao.AuthOk)
                            maquina.ao(EventoConexao.CapabilitiesOk)
                            reconexao.reset()
                            heartbeat.visto(System.currentTimeMillis())
                            publicar(pc, "Conectado")
                            contexto?.let { ServicoConexao.iniciar(it) }
                        }
                    }
                }
            } catch (e: SecurityException) {
                maquina.ao(EventoConexao.PermissaoNegada)
                publicar(pc, Protocolo.texto("LOCAL_NETWORK_PERMISSION_DENIED"), "LOCAL_NETWORK_PERMISSION_DENIED")
                pararReconexao = true
            } catch (e: Exception) {
                if (maquina.foto.fase == Fase.Conectando) maquina.ao(EventoConexao.SocketCaiu)
                else if (maquina.foto.fase != Fase.Erro) {
                    soltarLocal()
                    fila.descartarMovesAtrasados()
                    maquina.ao(EventoConexao.SocketCaiu)
                }
                publicar(pc, e.message ?: "Falha ao conectar")
            } finally {
                soltarLocal()
                fila.descartarMovesAtrasados()
                fecharSocket()
                conectando.set(false)
                if (!desconexaoManual && !pararReconexao && ultimoPc?.deviceId == pc.deviceId) agendarReconexao(pc)
            }
        }
    }

    private fun tratarErro(pc: PcEncontrado, codigo: String) {
        when (codigo) {
            "AUTH_REVOKED" -> maquina.ao(EventoConexao.AuthRevogada)
            "AUTH_REJECTED" -> maquina.ao(EventoConexao.AuthFalhou)
            "PROTOCOL_VERSION_UNSUPPORTED" -> maquina.ao(EventoConexao.VersaoRejeitada)
        }
        publicar(pc, Protocolo.texto(codigo), codigo)
    }

    private fun agendarReconexao(pc: PcEncontrado) {
        val atraso = reconexao.proximoAtrasoMs()
        escopo.launch {
            delay(atraso)
            if (!desconexaoManual && !pararReconexao && ultimoPc?.deviceId == pc.deviceId && !maquina.foto.pronta) {
                publicar(pc, "Reconectando automaticamente…")
                conectarInterno(pc, null)
            }
        }
    }

    private fun resolver(pc: PcEncontrado): PcEncontrado {
        val recente = diretorio.ultimo(pc.deviceId) ?: return pc
        return PcEncontrado(recente.nome, recente.host, recente.porta, recente.deviceId)
    }

    private fun soltarLocal() { seguranca.soltarTudo() }

    private fun drenarFila() {
        if (!maquina.foto.pronta) return
        for (json in fila.drenar()) escreverLinha(json)
    }

    private fun envelope(nome: String, payload: kotlinx.serialization.json.JsonObject) = Envelope(
        Protocolo.VERSAO, Protocolo.novoId(), nome, System.currentTimeMillis(), sequencia++, payload = payload
    )

    private fun publicar(pc: PcEncontrado? = _estado.value.pc, mensagem: String = "", codigo: String = "") {
        _estado.value = EstadoSessao(maquina.foto.fase, pc, mensagem, codigo)
    }

    private fun fecharSocket() {
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }

    @Synchronized private fun escreverLinha(json: String) {
        val atual = writer ?: return
        atual.write(json)
        atual.newLine()
        atual.flush()
    }

    private fun prefsCifradas(context: Context): SharedPreferences {
        val chave = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            "pcflow_secret",
            chave,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
