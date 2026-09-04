package com.ander.pcflow

import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Interoperabilidade real entre o cliente Android e o servidor .NET.
 *
 * Este teste é o que reproduz o caminho que estava quebrado de ponta a ponta:
 * abre o mesmo socket TLS que o app abre, valida a pinagem por SHA-256 do
 * certificado e faz o handshake "ola" completo contra o servidor de verdade.
 *
 * O servidor é iniciado por `tests/rodar-interop.sh`, que passa host, porta e a
 * impressão digital esperada por variável de ambiente. Sem elas o teste é
 * ignorado, para que `gradle test` continue funcionando sozinho.
 */
class InteropTlsTest {

    private val host = System.getenv("PCFLOW_TESTE_HOST")
    private val porta = System.getenv("PCFLOW_TESTE_PORTA")?.toIntOrNull()
    private val impressao = System.getenv("PCFLOW_TESTE_TLS")

    /**
     * Cópia fiel de SessaoPcFlow.abrirTls: mesmo contexto, mesma pinagem.
     * `apenasTls12` simula um Windows 10, cujo SChannel não expõe TLS 1.3.
     */
    private fun abrirTls(
        host: String,
        porta: Int,
        impressaoEsperada: String,
        apenasTls12: Boolean = false
    ): SSLSocket {
        val confiarNaPinagem = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        }
        val contexto = SSLContext.getInstance("TLS")
        contexto.init(null, arrayOf(confiarNaPinagem), SecureRandom())
        val ssl = contexto.socketFactory.createSocket() as SSLSocket
        if (apenasTls12) ssl.enabledProtocols = arrayOf("TLSv1.2")
        ssl.connect(InetSocketAddress(host, porta), 5000)
        ssl.tcpNoDelay = true
        ssl.startHandshake()

        val certificado = ssl.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: error("Certificado remoto ausente")
        val atual = MessageDigest.getInstance("SHA-256")
            .digest(certificado.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (!atual.equals(impressaoEsperada.replace(":", ""), ignoreCase = true)) {
            ssl.close()
            throw SSLHandshakeException("A identidade deste PC mudou. Conexão bloqueada por segurança.")
        }
        return ssl
    }

    @Test
    fun `cliente Android conecta no servidor real e completa o handshake`() {
        Assume.assumeTrue(
            "Servidor de teste não iniciado (use tests/rodar-interop.sh)",
            host != null && porta != null && impressao != null
        )

        abrirTls(host!!, porta!!, impressao!!).use { ssl ->
            val leitor = BufferedReader(InputStreamReader(ssl.inputStream, Charsets.UTF_8))
            val escritor = BufferedWriter(OutputStreamWriter(ssl.outputStream, Charsets.UTF_8))

            // A negociação precisa ter fechado numa versão real de TLS.
            val protocolo = ssl.session.protocol
            println("TLS negociado: $protocolo / ${ssl.session.cipherSuite}")
            assertTrue("Protocolo inesperado: $protocolo", protocolo.startsWith("TLS"))

            // Handshake idêntico ao do app.
            val ola = JSONObject()
                .put("tipo", "ola")
                .put("dispositivoId", "dispositivo-de-teste")
                .put("maquinaId", "")
                .put("nome", "Celular de Teste")
                .put("appVersao", SessaoPcFlow.VERSAO_APP)
            escritor.write(ola.toString()); escritor.newLine(); escritor.flush()

            val resposta = JSONObject(leitor.readLine() ?: error("O servidor não respondeu"))
            assertEquals(
                "Servidor recusou o handshake: ${resposta.optString("mensagem")}",
                "conectado", resposta.optString("tipo")
            )
            assertTrue(resposta.optString("token").isNotBlank())
            assertTrue(resposta.optJSONObject("permissoes")?.optBoolean("entrada") == true)

            // A sessão precisa ficar utilizável: comandos e heartbeat.
            escritor.write(JSONObject().put("tipo", "mouse_abs")
                .put("x", 0.5).put("y", 0.5).put("monitor", 0).toString())
            escritor.newLine()
            escritor.write(JSONObject().put("tipo", "ping").put("t", 1).toString())
            escritor.newLine()
            escritor.flush()

            val pong = JSONObject(leitor.readLine() ?: error("Sem resposta ao ping"))
            assertEquals("pong", pong.optString("tipo"))
        }
    }

    /**
     * ESTE É O CENÁRIO DO DEFEITO: um par que não tem TLS 1.3.
     *
     * Com o código antigo (EnabledSslProtocols = Tls12 | Tls13) o servidor
     * estourava em AuthenticateAsServerAsync no Windows 10 e a conexão caía
     * antes de qualquer byte. Aqui a negociação precisa fechar em TLS 1.2 e a
     * sessão precisa continuar utilizável.
     */
    @Test
    fun `conexao funciona quando o par so tem TLS 1_2 como no Windows 10`() {
        Assume.assumeTrue(
            "Servidor de teste não iniciado",
            host != null && porta != null && impressao != null
        )

        abrirTls(host!!, porta!!, impressao!!, apenasTls12 = true).use { ssl ->
            assertEquals("TLSv1.2", ssl.session.protocol)
            println("TLS negociado (simulando Windows 10): ${ssl.session.protocol}")

            val leitor = BufferedReader(InputStreamReader(ssl.inputStream, Charsets.UTF_8))
            val escritor = BufferedWriter(OutputStreamWriter(ssl.outputStream, Charsets.UTF_8))

            val ola = JSONObject()
                .put("tipo", "ola")
                .put("dispositivoId", "dispositivo-windows-10")
                .put("nome", "Celular no Windows 10")
                .put("appVersao", SessaoPcFlow.VERSAO_APP)
            escritor.write(ola.toString()); escritor.newLine(); escritor.flush()

            val resposta = JSONObject(leitor.readLine() ?: error("O servidor não respondeu"))
            assertEquals("conectado", resposta.optString("tipo"))

            escritor.write(JSONObject().put("tipo", "ping").put("t", 7).toString())
            escritor.newLine(); escritor.flush()
            assertEquals("pong", JSONObject(leitor.readLine()!!).optString("tipo"))
        }
    }

    @Test
    fun `versao diferente e recusada com mensagem clara`() {
        Assume.assumeTrue(
            "Servidor de teste não iniciado",
            host != null && porta != null && impressao != null
        )

        abrirTls(host!!, porta!!, impressao!!).use { ssl ->
            val leitor = BufferedReader(InputStreamReader(ssl.inputStream, Charsets.UTF_8))
            val escritor = BufferedWriter(OutputStreamWriter(ssl.outputStream, Charsets.UTF_8))

            val ola = JSONObject()
                .put("tipo", "ola")
                .put("dispositivoId", "dispositivo-antigo")
                .put("nome", "Celular Antigo")
                .put("appVersao", "0.2.0")
            escritor.write(ola.toString()); escritor.newLine(); escritor.flush()

            val resposta = JSONObject(leitor.readLine() ?: error("Sem resposta"))
            assertEquals("erro", resposta.optString("tipo"))
            val mensagem = resposta.optString("mensagem")
            assertTrue("Mensagem pouco clara: $mensagem", mensagem.contains("Versões diferentes"))
            assertTrue(mensagem.contains("0.2.0"))
        }
    }

    @Test
    fun `pinagem bloqueia certificado diferente`() {
        Assume.assumeTrue("Servidor de teste não iniciado", host != null && porta != null)

        val impressaoErrada = "0".repeat(64)
        val erro = runCatching { abrirTls(host!!, porta!!, impressaoErrada) }.exceptionOrNull()
        assertTrue(
            "A pinagem deveria ter recusado a conexão, mas obtive: $erro",
            erro is SSLHandshakeException
        )
    }
}
