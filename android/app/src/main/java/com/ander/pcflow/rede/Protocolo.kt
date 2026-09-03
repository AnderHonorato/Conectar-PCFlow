package com.ander.pcflow.rede

import android.net.Uri
import org.json.JSONObject

/**
 * Espelho Kotlin do protocolo definido em windows/PCFlow.Core/Protocolo.cs.
 * Mantido sem dependência de Android (exceto o parser de URI) para poder ser
 * coberto por testes unitários de JVM.
 */
object Protocolo {
    const val VERSAO = 2
    const val VERSAO_APP = "1.0.0"

    const val PORTA_DESCOBERTA = 45455
    const val PORTA_CONTROLE = 45456

    const val SONDA = "PCFLOW_DISCOVER_V2"

    /** Intervalo entre heartbeats. O PC derruba a sessão após 25 s sem sinal. */
    const val INTERVALO_PING_MS = 7_000L

    /** Tempo máximo esperando o "pong" antes de considerar a conexão morta. */
    const val LIMITE_SEM_PONG_MS = 21_000L

    const val TIMEOUT_CONEXAO_MS = 4_000
    const val TIMEOUT_LEITURA_MS = 30_000

    fun ola(
        dispositivoId: String,
        nome: String,
        modelo: String,
        token: String?,
        pin: String?
    ): JSONObject = JSONObject().apply {
        put("tipo", "ola")
        put("protocolo", VERSAO)
        put("dispositivoId", dispositivoId)
        put("nome", nome)
        put("modelo", modelo)
        put("versao", VERSAO_APP)
        if (!token.isNullOrBlank()) put("token", token)
        if (!pin.isNullOrBlank()) put("pin", pin.filter(Char::isDigit))
    }
}

/** Um PC visto na rede (por descoberta, QR, endereço manual ou histórico). */
data class PcEncontrado(
    val nome: String,
    val host: String,
    val porta: Int = Protocolo.PORTA_CONTROLE,
    val versao: String = "",
    val salvo: Boolean = false
) {
    val endereco: String get() = "$host:$porta"
}

enum class EstadoConexao { DESCONECTADO, PROCURANDO, CONECTANDO, CONECTADO, RECONECTANDO, ERRO }

data class EstadoSessao(
    val estado: EstadoConexao = EstadoConexao.DESCONECTADO,
    val pc: PcEncontrado? = null,
    val mensagem: String = "",
    /** Latência do último ping/pong em ms; -1 enquanto não houver medida. */
    val latenciaMs: Long = -1,
    /** Recursos que o PC autorizou nesta sessão. */
    val recursos: Recursos = Recursos()
) {
    val conectado: Boolean get() = estado == EstadoConexao.CONECTADO
}

data class Recursos(
    val arquivos: Boolean = false,
    val tela: Boolean = false,
    val energia: Boolean = false,
    val areaTransferencia: Boolean = false,
    val atalhos: Boolean = false
)

/**
 * Interpreta o QR mostrado no PC: `pcflow://host:porta?pin=123456&nome=DESKTOP`.
 * Também aceita "192.168.0.10", "192.168.0.10:45456" e o texto colado pelo botão
 * "Copiar endereço" do Windows.
 */
object EnderecoPcFlow {
    data class Resultado(val pc: PcEncontrado, val pin: String?)

    fun interpretar(texto: String?): Resultado? {
        val bruto = texto?.trim().orEmpty()
        if (bruto.isEmpty()) return null

        if (bruto.startsWith("pcflow://", ignoreCase = true)) {
            return runCatching {
                val uri = Uri.parse(bruto)
                val host = uri.host ?: return@runCatching null
                val porta = if (uri.port > 0) uri.port else Protocolo.PORTA_CONTROLE
                val nome = uri.getQueryParameter("nome")?.takeIf { it.isNotBlank() } ?: host
                Resultado(PcEncontrado(nome, host, porta), uri.getQueryParameter("pin"))
            }.getOrNull()
        }

        // host[:porta] simples
        val semEsquema = bruto.removePrefix("tcp://")
        val partes = semEsquema.split(":")
        val host = partes[0].trim()
        if (host.isEmpty() || host.contains(" ")) return null
        val porta = partes.getOrNull(1)?.trim()?.toIntOrNull() ?: Protocolo.PORTA_CONTROLE
        if (porta !in 1..65535) return null
        return Resultado(PcEncontrado(host, host, porta), null)
    }
}
