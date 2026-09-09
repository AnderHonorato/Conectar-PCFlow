package com.ander.pcflow.telas

import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PerfilVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorioCascaTest {

    private fun repositorio(armazem: Armazem = ArmazemMemoria()) = RepositorioCasca(armazem)

    @Test
    fun `favorito e gravado e continua depois de reabrir`() {
        val armazem = ArmazemMemoria()
        repositorio(armazem).definirFavorito("145929922", true)

        val depoisDeReabrir = repositorio(armazem)
        assertEquals(setOf("145929922"), depoisDeReabrir.favoritos())

        depoisDeReabrir.alternarFavorito("145929922")
        assertTrue(repositorio(armazem).favoritos().isEmpty())
    }

    @Test
    fun `apelido em branco apaga em vez de gravar vazio`() {
        val armazem = ArmazemMemoria()
        val repo = repositorio(armazem)
        repo.definirApelido("145929922", "  PC de Casa  ")
        assertEquals(mapOf("145929922" to "PC de Casa"), repositorio(armazem).apelidos())

        repo.definirApelido("145929922", "   ")
        assertTrue(repositorio(armazem).apelidos().isEmpty())
    }

    @Test
    fun `dispositivo lembrado volta com endereco e apelido`() {
        val armazem = ArmazemMemoria()
        repositorio(armazem).lembrar(
            Dispositivo(
                id = "145929922",
                nome = "DESKTOP-8F2K",
                apelido = "PC de Casa",
                host = "192.168.0.31",
                porta = 45456,
                tls = "aa:bb"
            ),
            quandoMs = 1_700_000_000_000
        )
        val lido = repositorio(armazem).lembrados().single()
        assertEquals("145929922", lido.id)
        assertEquals("192.168.0.31", lido.host)
        assertEquals("PC de Casa", lido.apelido)
        assertEquals(1_700_000_000_000, lido.ultimoAcessoMs)
        assertFalse(lido.naRede)
    }

    @Test
    fun `lembrar de novo atualiza em vez de duplicar`() {
        val armazem = ArmazemMemoria()
        val repo = repositorio(armazem)
        val base = Dispositivo(id = "145929922", nome = "PC", host = "192.168.0.31")
        repo.lembrar(base, 1000)
        repo.lembrar(base.copy(host = "192.168.0.99"), 2000)

        val lista = repositorio(armazem).lembrados()
        assertEquals(1, lista.size)
        assertEquals("192.168.0.99", lista.single().host)
        assertEquals(2000L, lista.single().ultimoAcessoMs)
    }

    @Test
    fun `esquecer tira o dispositivo da lista guardada`() {
        val armazem = ArmazemMemoria()
        val repo = repositorio(armazem)
        repo.lembrar(Dispositivo(id = "1", nome = "A"), 1)
        repo.lembrar(Dispositivo(id = "2", nome = "B"), 2)
        repo.esquecer("1")
        assertEquals(listOf("2"), repositorio(armazem).lembrados().map { it.id })
    }

    @Test
    fun `historico guarda data duracao computador e resultado`() {
        val armazem = ArmazemMemoria()
        repositorio(armazem).registrarSessao(
            RegistroSessao(1_700_000_000_000, 723, "145929922", "PC de Casa", ResultadoSessao.INTERROMPIDA)
        )
        val lido = repositorio(armazem).historico().single()
        assertEquals(1_700_000_000_000, lido.inicioMs)
        assertEquals(723L, lido.duracaoSegundos)
        assertEquals("145929922", lido.dispositivoId)
        assertEquals("PC de Casa", lido.nome)
        assertEquals(ResultadoSessao.INTERROMPIDA, lido.resultado)
    }

    @Test
    fun `limpar historico deixa a lista vazia`() {
        val armazem = ArmazemMemoria()
        val repo = repositorio(armazem)
        repo.registrarSessao(RegistroSessao(1, 2, "1", "PC", ResultadoSessao.CONCLUIDA))
        repo.limparHistorico()
        assertTrue(repositorio(armazem).historico().isEmpty())
    }

    @Test
    fun `ajustes voltam iguais depois de gravados`() {
        val armazem = ArmazemMemoria()
        val escolhidos = AjustesCasca(
            tema = TemaApp.CLARO,
            qualidade = PerfilVideo.IMAGEM,
            modoToque = ModoToquePadrao.TRACKPAD,
            modoClipboard = ModoClipboard.AUTOMATICO,
            servidorRetransmissao = " relay.exemplo.com "
        )
        repositorio(armazem).salvarAjustes(escolhidos)
        assertEquals(
            escolhidos.copy(servidorRetransmissao = "relay.exemplo.com"),
            repositorio(armazem).ajustes()
        )
    }

    @Test
    fun `preferencia estragada volta ao padrao em vez de derrubar`() {
        val armazem = ArmazemMemoria(
            mapOf(
                RepositorioCasca.CHAVE_AJUSTES to "{\"tema\":\"NEON\"}",
                RepositorioCasca.CHAVE_HISTORICO to "isto não é json",
                RepositorioCasca.CHAVE_FAVORITOS to "{}"
            )
        )
        val repo = repositorio(armazem)
        assertEquals(AjustesCasca(), repo.ajustes().copy(servidorRetransmissao = ""))
        assertTrue(repo.historico().isEmpty())
        assertTrue(repo.favoritos().isEmpty())
    }
}
