package com.ander.pcflow

import com.ander.pcflow.sessao.AcumuladorRolagem
import com.ander.pcflow.sessao.BotaoMouse
import com.ander.pcflow.sessao.ComandoEntrada
import com.ander.pcflow.sessao.ConfigGesto
import com.ander.pcflow.sessao.DecisorEixo
import com.ander.pcflow.sessao.Eixo
import com.ander.pcflow.sessao.MaquinaGesto
import com.ander.pcflow.sessao.ModoEntrada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaquinaGestoTest {

    private val config = ConfigGesto()

    private fun maquina(modo: ModoEntrada): MaquinaGesto =
        MaquinaGesto(config).apply { definirModo(modo) }

    /** Conta pressões e soltas para provar que nada fica pendurado no PC. */
    private fun saldoDeBotoes(comandos: List<ComandoEntrada>): Int =
        comandos.count { it is ComandoEntrada.Pressionar } - comandos.count { it is ComandoEntrada.Soltar }

    // ------------------------------------------------------------ toque simples

    @Test
    fun `toque curto vira um clique esquerdo`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        val saida = m.descer(100f, 200f, 0L) + m.subir(80L)
        assertTrue(saida.first() is ComandoEntrada.PosicionarEm)
        val clique = saida.filterIsInstance<ComandoEntrada.Clicar>().single()
        assertEquals(BotaoMouse.ESQUERDO, clique.botao)
        assertEquals(1, clique.cliques)
    }

    @Test
    fun `toque duplo vira um clique de dois cliques`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(100f, 200f, 0L); m.subir(60L)
        val saida = m.descer(104f, 203f, 160L) + m.subir(210L)
        val clique = saida.filterIsInstance<ComandoEntrada.Clicar>().single()
        assertEquals(2, clique.cliques)
    }

    @Test
    fun `segundo toque longe demais continua sendo clique simples`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(100f, 200f, 0L); m.subir(60L)
        val saida = m.descer(600f, 900f, 150L) + m.subir(200L)
        assertEquals(1, saida.filterIsInstance<ComandoEntrada.Clicar>().single().cliques)
    }

    @Test
    fun `segundo toque tardio continua sendo clique simples`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(100f, 200f, 0L); m.subir(60L)
        val saida = m.descer(100f, 200f, 900L) + m.subir(960L)
        assertEquals(1, saida.filterIsInstance<ComandoEntrada.Clicar>().single().cliques)
    }

    @Test
    fun `tres toques seguidos nao viram dois cliques duplos`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(100f, 200f, 0L); m.subir(50L)
        m.descer(100f, 200f, 120L)
        assertEquals(2, m.subir(170L).filterIsInstance<ComandoEntrada.Clicar>().single().cliques)
        m.descer(100f, 200f, 240L)
        assertEquals(1, m.subir(290L).filterIsInstance<ComandoEntrada.Clicar>().single().cliques)
    }

    // ------------------------------------------------------------ pressão longa

    @Test
    fun `pressao longa no toque direto vira clique direito e o soltar nao clica`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(300f, 400f, 0L)
        val longa = m.pressaoLonga(config.tempoPressaoLongaMs)
        val clique = longa.filterIsInstance<ComandoEntrada.Clicar>().single()
        assertEquals(BotaoMouse.DIREITO, clique.botao)
        assertTrue(m.subir(700L).none { it is ComandoEntrada.Clicar })
    }

    @Test
    fun `pressao longa no touchpad comeca arrasto e a solta devolve o botao`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        m.descer(300f, 400f, 0L)
        val inicio = m.pressaoLonga(500L)
        assertEquals(ComandoEntrada.Pressionar(BotaoMouse.ESQUERDO), inicio.single())
        val meio = m.mover(360f, 460f, 540L)
        assertTrue(meio.any { it is ComandoEntrada.Mover })
        val fim = m.subir(900L)
        assertEquals(ComandoEntrada.Soltar(BotaoMouse.ESQUERDO), fim.single())
        assertEquals(0, saldoDeBotoes(inicio + meio + fim))
        assertTrue(m.botoesSegurados.isEmpty())
    }

    @Test
    fun `pressao longa no modo mouse nao faz nada`() {
        val m = maquina(ModoEntrada.MOUSE)
        m.descer(300f, 400f, 0L)
        assertTrue(m.pressaoLonga(600L).isEmpty())
    }

    // ------------------------------------------------------------------ arrasto

    @Test
    fun `arrasto tem down move e up nessa ordem`() {
        // Tocar, soltar e tocar de novo já arrastando é o gesto de arrasto do
        // toque direto — o mesmo de segurar um ícone para levar embora.
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.descer(100f, 100f, 0L); m.subir(50L)
        val sequencia = mutableListOf<ComandoEntrada>()
        sequencia += m.descer(100f, 100f, 120L)
        sequencia += m.mover(200f, 240f, 160L)
        sequencia += m.mover(300f, 320f, 200L)
        sequencia += m.subir(400L)

        val indiceDown = sequencia.indexOfFirst { it is ComandoEntrada.Pressionar }
        val indiceUp = sequencia.indexOfFirst { it is ComandoEntrada.Soltar }
        assertTrue("faltou o down", indiceDown >= 0)
        assertTrue("faltou o up", indiceUp > indiceDown)
        assertTrue(
            "o movimento tem que ficar entre o down e o up",
            sequencia.subList(indiceDown, indiceUp).any { it is ComandoEntrada.PosicionarEm }
        )
        assertEquals(0, saldoDeBotoes(sequencia))
    }

    @Test
    fun `arrasto cancelado ainda solta o botao`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        m.descer(10f, 10f, 0L)
        val inicio = m.pressaoLonga(500L)
        val cancelado = m.cancelar()
        assertEquals(ComandoEntrada.Soltar(BotaoMouse.ESQUERDO), cancelado.single())
        assertEquals(0, saldoDeBotoes(inicio + cancelado))
    }

    @Test
    fun `segundo dedo no meio do arrasto solta o botao`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        m.descer(10f, 10f, 0L)
        val inicio = m.pressaoLonga(500L)
        val multitoque = m.entrarMultitoque()
        assertEquals(0, saldoDeBotoes(inicio + multitoque))
        assertTrue(m.botoesSegurados.isEmpty())
    }

    @Test
    fun `trocar de modo no meio do arrasto solta o botao`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        m.descer(10f, 10f, 0L)
        val inicio = m.pressaoLonga(500L)
        val troca = m.definirModo(ModoEntrada.TOQUE_DIRETO)
        assertEquals(0, saldoDeBotoes(inicio + troca))
    }

    @Test
    fun `encerrar solta tudo inclusive a trava de arrasto`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        val trava = m.definirTravaArraste(true)
        assertEquals(ComandoEntrada.Pressionar(BotaoMouse.ESQUERDO), trava.single())
        m.descer(10f, 10f, 0L)
        val arrasto = m.mover(200f, 200f, 40L)
        val fim = m.subir(80L)
        assertTrue("a trava segura o botão mesmo depois de soltar o dedo", fim.none { it is ComandoEntrada.Soltar })
        val encerramento = m.encerrar()
        assertEquals(0, saldoDeBotoes(trava + arrasto + fim + encerramento))
        assertTrue(m.botoesSegurados.isEmpty())
        assertFalse(m.travaArraste)
    }

    @Test
    fun `com a trava ligada o toque nao manda clique solto`() {
        val m = maquina(ModoEntrada.TOQUE_DIRETO)
        m.definirTravaArraste(true)
        m.descer(10f, 10f, 0L)
        assertTrue(m.subir(40L).none { it is ComandoEntrada.Clicar })
        m.encerrar()
    }

    @Test
    fun `sequencia longa e embaralhada nunca deixa botao preso`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        val tudo = mutableListOf<ComandoEntrada>()
        var instante = 0L
        repeat(40) { passo ->
            instante += 37L
            tudo += m.descer(10f + passo, 20f + passo, instante)
            if (passo % 3 == 0) tudo += m.pressaoLonga(instante + 500L)
            if (passo % 2 == 0) tudo += m.mover(60f + passo * 4, 80f + passo * 3, instante + 20L)
            when (passo % 5) {
                0 -> tudo += m.subir(instante + 60L)
                1 -> tudo += m.cancelar()
                2 -> tudo += m.entrarMultitoque().also { m.sairMultitoque() }
                3 -> tudo += m.definirModo(m.modo.proximo)
                else -> tudo += m.subir(instante + 90L)
            }
        }
        tudo += m.encerrar()
        assertEquals(0, saldoDeBotoes(tudo))
        assertTrue(m.botoesSegurados.isEmpty())
    }

    // ---------------------------------------------------------------- modos

    @Test
    fun `modo toque direto posiciona e modo touchpad move relativo`() {
        val direto = maquina(ModoEntrada.TOQUE_DIRETO)
        direto.descer(10f, 10f, 0L)
        assertTrue(direto.mover(200f, 300f, 20L).any { it is ComandoEntrada.PosicionarEm })

        val touchpad = maquina(ModoEntrada.TOUCHPAD)
        touchpad.descer(10f, 10f, 0L)
        val saida = touchpad.mover(200f, 300f, 20L)
        assertTrue(saida.none { it is ComandoEntrada.PosicionarEm })
        assertTrue(saida.any { it is ComandoEntrada.Mover })
    }

    @Test
    fun `no modo mouse o toque na tela nao clica`() {
        val m = maquina(ModoEntrada.MOUSE)
        m.descer(10f, 10f, 0L)
        assertTrue(m.subir(50L).none { it is ComandoEntrada.Clicar })
        assertEquals(
            ComandoEntrada.Clicar(BotaoMouse.MEIO, 1),
            m.cliqueManual(BotaoMouse.MEIO).single()
        )
    }

    @Test
    fun `movimento curto dentro da folga nao move o ponteiro`() {
        val m = maquina(ModoEntrada.TOUCHPAD)
        m.descer(100f, 100f, 0L)
        assertTrue(m.mover(104f, 103f, 10L).isEmpty())
        assertTrue(m.mover(160f, 160f, 30L).isNotEmpty())
    }

    @Test
    fun `aceleracao anda mais quando o dedo corre`() {
        val lento = config.aceleracao.fator(2f)
        val rapido = config.aceleracao.fator(60f)
        assertTrue(rapido > lento)
        assertEquals(config.aceleracao.base + config.aceleracao.extra, rapido, 0.001f)
    }
}

class DecisorEixoTest {

    @Test
    fun `so decide o eixo depois de andar o bastante`() {
        val decisor = DecisorEixo(limiar = 30f)
        assertEquals(Eixo.INDEFINIDO, decisor.atualizar(4f, 3f))
        assertEquals(Eixo.INDEFINIDO, decisor.atualizar(4f, 3f))
        assertEquals(Eixo.HORIZONTAL, decisor.atualizar(30f, 2f))
    }

    @Test
    fun `deslize horizontal escolhe horizontal`() {
        val decisor = DecisorEixo()
        assertEquals(Eixo.HORIZONTAL, decisor.atualizar(50f, 6f))
    }

    @Test
    fun `tremor do dedo nao troca o eixo escolhido`() {
        val decisor = DecisorEixo()
        assertEquals(Eixo.VERTICAL, decisor.atualizar(5f, 60f))
        repeat(20) { assertEquals(Eixo.VERTICAL, decisor.atualizar(6f, 4f)) }
    }

    @Test
    fun `mudanca clara de direcao troca o eixo`() {
        val decisor = DecisorEixo()
        assertEquals(Eixo.VERTICAL, decisor.atualizar(5f, 60f))
        assertEquals(Eixo.HORIZONTAL, decisor.atualizar(120f, 1f))
    }

    @Test
    fun `reiniciar volta ao indefinido`() {
        val decisor = DecisorEixo()
        decisor.atualizar(90f, 0f)
        decisor.reiniciar()
        assertEquals(Eixo.INDEFINIDO, decisor.eixo)
    }
}

class AcumuladorRolagemTest {

    @Test
    fun `pixels viram entalhes de cento e vinte`() {
        val acumulador = AcumuladorRolagem(pixelsPorEntalhe = 40f)
        assertEquals(0, acumulador.acumular(20f))
        assertEquals(120, acumulador.acumular(20f))
        assertEquals(0, acumulador.acumular(10f))
        assertEquals(240, acumulador.acumular(70f))
    }

    @Test
    fun `rolagem para tras vira delta negativo`() {
        val acumulador = AcumuladorRolagem(pixelsPorEntalhe = 40f)
        assertEquals(-120, acumulador.acumular(-45f))
    }

    @Test
    fun `reiniciar joga fora o resto acumulado`() {
        val acumulador = AcumuladorRolagem(pixelsPorEntalhe = 40f)
        acumulador.acumular(39f)
        acumulador.reiniciar()
        assertEquals(0, acumulador.acumular(20f))
    }
}
