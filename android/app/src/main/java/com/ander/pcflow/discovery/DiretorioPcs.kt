package com.ander.pcflow.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AnuncioPc(val deviceId: String, val nome: String, val host: String, val porta: Int, val vistoEm: Long)

object ProtocoloDescoberta {
    const val PEDIDO = "PCFLOW_DISCOVER_V1"

    fun lerResposta(json: String, host: String, vistoEm: Long): AnuncioPc? = try {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root["tipo"]?.jsonPrimitive?.content != "pcflow") null
        else {
            val id = root["deviceId"]?.jsonPrimitive?.content ?: ""
            if (id.isBlank()) null
            else AnuncioPc(
                id,
                root["nome"]?.jsonPrimitive?.content ?: host,
                host,
                root["porta"]?.jsonPrimitive?.int ?: 45456,
                vistoEm
            )
        }
    } catch (_: Exception) {
        null
    }
}

class DiretorioPcs {
    private val itens = linkedMapOf<String, AnuncioPc>()
    fun observar(anuncio: AnuncioPc) {
        val atual = itens[anuncio.deviceId]
        if (atual != null && anuncio.vistoEm < atual.vistoEm) return
        itens[anuncio.deviceId] = anuncio
    }
    fun ultimo(deviceId: String) = itens[deviceId]
    fun todos() = itens.values.toList()
}
