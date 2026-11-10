package com.ander.pcflow.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

data class Envelope(
    val protocolVersion: Int,
    val messageId: String,
    val name: String,
    val timestamp: Long,
    val sequence: Long,
    val correlationId: String? = null,
    val sessionId: String? = null,
    val payload: JsonObject = JsonObject(emptyMap())
)

data class LeituraProtocolo(val ok: Boolean, val codigo: String, val envelope: Envelope?)

object Protocolo {
    const val VERSAO = 2
    const val MAX_BYTES = 65_536
    private val nomes = setOf(
        "session.hello", "session.auth", "session.capabilities", "session.heartbeat",
        "session.ack", "session.error",
        "pointer.move", "pointer.button", "pointer.scroll",
        "keyboard.text", "keyboard.key",
        "media.transport", "media.volume", "system.power"
    )

    fun ler(texto: String): LeituraProtocolo {
        if (texto.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return LeituraProtocolo(false, "PAYLOAD_TOO_LARGE", null)
        return try {
            val root = json.parseToJsonElement(texto).jsonObject
            val version = root["protocolVersion"]?.jsonPrimitive?.int ?: return LeituraProtocolo(false, "PROTOCOL_VERSION_UNSUPPORTED", null)
            if (version != VERSAO) return LeituraProtocolo(false, "PROTOCOL_VERSION_UNSUPPORTED", null)
            val messageId = root.texto("messageId")
            val name = root.texto("name")
            if (messageId.isBlank() || name.isBlank()) return LeituraProtocolo(false, "INVALID_MESSAGE", null)
            if (name !in nomes) return LeituraProtocolo(false, "UNKNOWN_COMMAND", null)
            val sequence = root["sequence"]?.jsonPrimitive?.long ?: return LeituraProtocolo(false, "INVALID_MESSAGE", null)
            if (sequence < 0 || root["timestamp"] == null) return LeituraProtocolo(false, "INVALID_MESSAGE", null)
            LeituraProtocolo(
                true, "",
                Envelope(
                    version, messageId, name, root["timestamp"]!!.jsonPrimitive.long, sequence,
                    root.texto("correlationId").ifBlank { null },
                    root.texto("sessionId").ifBlank { null },
                    root["payload"]?.jsonObject ?: JsonObject(emptyMap())
                )
            )
        } catch (_: Exception) {
            LeituraProtocolo(false, "INVALID_MESSAGE", null)
        }
    }

    fun escrever(envelope: Envelope): String = buildJsonObject {
        put("protocolVersion", envelope.protocolVersion)
        put("messageId", envelope.messageId)
        if (!envelope.correlationId.isNullOrBlank()) put("correlationId", envelope.correlationId)
        put("name", envelope.name)
        put("timestamp", envelope.timestamp)
        put("sequence", envelope.sequence)
        if (!envelope.sessionId.isNullOrBlank()) put("sessionId", envelope.sessionId)
        put("payload", envelope.payload)
    }.toString()

    fun novoId(): String = UUID.randomUUID().toString()

    fun texto(codigo: String) = when (codigo) {
        "PROTOCOL_VERSION_UNSUPPORTED" -> "Atualize o app do PC ou do celular."
        "AUTH_REQUIRED" -> "Informe o código de pareamento mostrado no computador."
        "AUTH_REJECTED" -> "Código ou token recusado."
        "AUTH_REVOKED" -> "Este aparelho foi revogado. Pareie de novo."
        "NOT_READY" -> "A sessão ainda não está pronta."
        "SESSION_IN_USE" -> "Já existe um controle ativo neste computador."
        "LOCAL_NETWORK_PERMISSION_DENIED" -> "Permita o acesso à rede local para achar o computador."
        else -> "Falha de sessão."
    }
}

fun JsonObject.texto(chave: String) = this[chave]?.jsonPrimitive?.content ?: ""

fun campos(vararg pares: Pair<String, Any?>): JsonObject = buildJsonObject {
    pares.forEach { (chave, valor) ->
        when (valor) {
            null -> Unit
            is String -> if (valor.isNotEmpty()) put(chave, valor)
            is Int -> put(chave, valor)
            is Long -> put(chave, valor)
            is Double -> put(chave, valor)
            is Boolean -> put(chave, valor)
            else -> put(chave, JsonPrimitive(valor.toString()))
        }
    }
}
