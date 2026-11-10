package com.ander.pcflow

import com.ander.pcflow.discovery.DiretorioPcs
import com.ander.pcflow.discovery.ProtocoloDescoberta
import com.ander.pcflow.protocol.Protocolo
import com.ander.pcflow.session.ChaveValor
import com.ander.pcflow.session.DiretorioToken
import com.ander.pcflow.session.EventoConexao
import com.ander.pcflow.session.Fase
import com.ander.pcflow.session.FilaSaida
import com.ander.pcflow.session.MaquinaConexao
import com.ander.pcflow.session.MonitorHeartbeat
import com.ander.pcflow.session.PoliticaReconexao
import com.ander.pcflow.session.SegurancaEntrada
import com.ander.pcflow.session.TipoErro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoldenFixtureTest {
    @Test fun helloAuthCapabilitiesHeartbeatAckError() {
        listOf(
            "session-hello.json" to "session.hello",
            "session-auth.json" to "session.auth",
            "session-capabilities.json" to "session.capabilities",
            "session-heartbeat.json" to "session.heartbeat",
            "session-ack.json" to "session.ack",
            "session-error.json" to "session.error"
        ).forEach { (arquivo, nome) ->
            val lido = Protocolo.ler(fixture(arquivo))
            assertTrue(lido.codigo, lido.ok)
            val envelope = lido.envelope!!
            assertEquals(nome, envelope.name)
            val deNovo = Protocolo.ler(Protocolo.escrever(envelope))
            assertTrue(deNovo.ok)
            val outra = deNovo.envelope!!
            assertEquals(envelope.messageId, outra.messageId)
            assertEquals(envelope.sequence, outra.sequence)
        }
    }

    @Test fun versaoEComandoDesconhecido() {
        assertEquals("PROTOCOL_VERSION_UNSUPPORTED", Protocolo.ler(fixture("version-mismatch.json")).codigo)
        assertEquals("UNKNOWN_COMMAND", Protocolo.ler(fixture("unknown-command.json")).codigo)
        assertNull(Protocolo.ler(fixture("unknown-command.json")).envelope)
    }

    private fun fixture(nome: String): String {
        val candidatos = listOf(
            File("protocol/fixtures/$nome"),
            File("../protocol/fixtures/$nome"),
            File("../../protocol/fixtures/$nome"),
            File("../../../protocol/fixtures/$nome")
        )
        return candidatos.first { it.exists() }.readText()
    }
}

class ConnectionMachineTest {
    @Test fun prontoSoDepoisDeAuthECapabilities() {
        val m = MaquinaConexao()
        m.ao(EventoConexao.CapabilitiesOk)
        assertFalse(m.foto.pronta)
        m.ao(EventoConexao.IniciarDescoberta)
        m.ao(EventoConexao.Selecionar)
        m.ao(EventoConexao.IniciarConexao)
        m.ao(EventoConexao.SocketAberto)
        m.ao(EventoConexao.AuthOk)
        m.ao(EventoConexao.CapabilitiesOk)
        assertEquals(Fase.Pronto, m.foto.fase)
    }

    @Test fun quedaEmConectandoEPermissaoEVersao() {
        val queda = MaquinaConexao()
        queda.ao(EventoConexao.IniciarDescoberta)
        queda.ao(EventoConexao.Selecionar)
        queda.ao(EventoConexao.IniciarConexao)
        assertEquals(TipoErro.Rede, queda.ao(EventoConexao.SocketCaiu).erro)
        val permissao = MaquinaConexao()
        permissao.ao(EventoConexao.IniciarDescoberta)
        assertEquals(TipoErro.Permissao, permissao.ao(EventoConexao.PermissaoNegada).erro)
    }
}

class ReconnectQueueTest {
    @Test fun moveAtrasadoNaoVoltaEBotaoSobra() {
        val fila = FilaSaida()
        fila.enfileirarMove("move-1")
        fila.enfileirarMove("move-2")
        fila.enfileirarDiscreto("up")
        fila.descartarMovesAtrasados()
        val drenado = fila.drenar()
        assertFalse(drenado.any { it.startsWith("move") })
        assertTrue(drenado.contains("up"))
    }

    @Test fun solturaNaQueda() {
        val seguranca = SegurancaEntrada()
        seguranca.down("pointer", "left")
        seguranca.down("key", "CTRL")
        val soltos = seguranca.soltarTudo()
        assertTrue(seguranca.vazio())
        assertEquals("pointer" to "left", soltos[0])
        assertEquals("key" to "CTRL", soltos[1])
    }

    @Test fun tresFalhasDeHeartbeatEAtraso() {
        val monitor = MonitorHeartbeat(1000, 3)
        monitor.visto(0)
        assertFalse(monitor.observar(2999))
        assertTrue(monitor.observar(3000))
        val politica = PoliticaReconexao()
        assertEquals(1500L, politica.proximoAtrasoMs())
        assertEquals(3000L, politica.proximoAtrasoMs())
    }

    @Test fun tokenSobreviveTrocaDeIp() {
        val memoria = object : ChaveValor {
            val dados = mutableMapOf<String, String>()
            override fun get(chave: String) = dados[chave]
            override fun put(chave: String, valor: String) { dados[chave] = valor }
        }
        val tokens = DiretorioToken(memoria)
        tokens.salvar("pc-1", "segredo")
        assertEquals("segredo", tokens.ler("pc-1"))
        assertNull(tokens.ler("10.0.0.9"))
        val diretorio = DiretorioPcs()
        val antigo = ProtocoloDescoberta.lerResposta("""{"tipo":"pcflow","nome":"Casa","porta":45456,"deviceId":"pc-1"}""", "10.0.0.2", 10)!!
        val velho = ProtocoloDescoberta.lerResposta("""{"tipo":"pcflow","nome":"Casa","porta":45456,"deviceId":"pc-1"}""", "10.0.0.9", 5)!!
        val novo = ProtocoloDescoberta.lerResposta("""{"tipo":"pcflow","nome":"Casa","porta":45456,"deviceId":"pc-1"}""", "10.0.0.8", 20)!!
        diretorio.observar(antigo)
        diretorio.observar(velho)
        diretorio.observar(novo)
        assertEquals("10.0.0.8", diretorio.ultimo("pc-1")!!.host)
        assertNull(ProtocoloDescoberta.lerResposta("""{"tipo":"pcflow","nome":"sem"}""", "10.0.0.1", 1))
    }
}
