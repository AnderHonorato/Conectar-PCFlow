package com.ander.pcflow

import com.ander.pcflow.rede.Protocolo
import com.ander.pcflow.rede.RedeLocal
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloTest {

    @Test
    fun `handshake carrega identidade e versao do protocolo`() {
        val ola = Protocolo.ola("id-1", "Galaxy", "Samsung SM-G991", null, "482731")
        assertEquals("ola", ola.getString("tipo"))
        assertEquals(Protocolo.VERSAO, ola.getInt("protocolo"))
        assertEquals("id-1", ola.getString("dispositivoId"))
        assertEquals("482731", ola.getString("pin"))
        assertFalse("sem token pareado, o campo não deve existir", ola.has("token"))
    }

    @Test
    fun `pin com espacos e tracos vira apenas digitos`() {
        val ola = Protocolo.ola("id", "n", "m", null, "482 731")
        assertEquals("482731", ola.getString("pin"))
    }

    @Test
    fun `dispositivo ja pareado envia token e nao envia pin`() {
        val ola = Protocolo.ola("id", "n", "m", "tok-abc", null)
        assertEquals("tok-abc", ola.getString("token"))
        assertFalse(ola.has("pin"))
    }

    @Test
    fun `heartbeat cabe dentro do limite de inatividade do PC`() {
        // O servidor derruba a sessão com 25 s de silêncio; três pings precisam caber.
        assertTrue(Protocolo.INTERVALO_PING_MS * 3 < 25_000)
        assertTrue(Protocolo.LIMITE_SEM_PONG_MS < 25_000)
    }

    @Test
    fun `resposta de pareamento traz o token para reconexao`() {
        val resposta = JSONObject(
            """{"tipo":"pareado","token":"abc123","nome":"DESKTOP-ANDER","protocolo":2}"""
        )
        assertEquals("pareado", resposta.getString("tipo"))
        assertEquals("abc123", resposta.getString("token"))
    }
}

class RedeLocalTest {

    @Test
    fun `prefixo de 24 bits é extraído do IPv4`() {
        assertEquals("192.168.0", RedeLocal.prefixo("192.168.0.23"))
        assertEquals("10.0.1", RedeLocal.prefixo("10.0.1.7"))
    }

    @Test
    fun `prefixo invalido devolve null`() {
        assertNull(RedeLocal.prefixo("nao-e-ip"))
        assertNull(RedeLocal.prefixo(null))
    }

    @Test
    fun `sub-redes diferentes sao detectadas`() {
        assertTrue(RedeLocal.mesmaRede("192.168.0.23", "192.168.0.10"))
        assertFalse(RedeLocal.mesmaRede("192.168.0.23", "192.168.15.10"))
        assertFalse(RedeLocal.mesmaRede("192.168.0.23", "10.0.0.5"))
    }

    @Test
    fun `sem informacao de IP nao gera alarme falso`() {
        assertTrue(RedeLocal.mesmaRede(null, "192.168.0.10"))
        assertTrue(RedeLocal.mesmaRede("192.168.0.23", null))
    }
}

/**
 * Garante que o handshake do Android continua idêntico ao que o servidor Windows
 * espera. O mesmo arquivo é consumido por um teste do lado C#.
 */
class InteropTest {

    private val arquivo = java.io.File("../../tests/interop/handshake-android.json")

    private fun canonico(json: JSONObject): String =
        json.keys().asSequence().sorted().joinToString(",", "{", "}") { chave ->
            val valor = json.get(chave)
            val texto = if (valor is String) "\"$valor\"" else valor.toString()
            "\"$chave\":$texto"
        }

    @Test
    fun `handshake gerado bate com o arquivo de interoperabilidade`() {
        val gerado = canonico(
            Protocolo.ola(
                dispositivoId = "dispositivo-interop",
                nome = "Galaxy de Anderson",
                modelo = "Samsung SM-G991B",
                token = null,
                pin = "482731"
            )
        )
        assertTrue(
            "Arquivo de interoperabilidade não encontrado em ${arquivo.absolutePath}",
            arquivo.exists()
        )
        assertEquals(
            "O handshake do Android mudou. Atualize tests/interop/handshake-android.json " +
                "e confirme que o servidor Windows continua aceitando.",
            arquivo.readText().trim(),
            gerado
        )
    }
}
