package com.ander.pcflow

import com.ander.pcflow.sessao.Visualizacao
import com.ander.pcflow.sessao.aplicarDeslocamento
import com.ander.pcflow.sessao.aplicarPinca
import com.ander.pcflow.sessao.areaDaImagem
import com.ander.pcflow.sessao.mapearToqueParaMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Celular em pé (1080x2400) mostrando um monitor 16:9: sobra barra preta em
 * cima e embaixo. É o caso em que o toque errava o alvo antes.
 */
class MapeamentoTelaTest {

    private val larguraArea = 1080
    private val alturaArea = 2400
    private val larguraQuadro = 1920
    private val alturaQuadro = 1080

    @Test
    fun `imagem deitada em tela em pe deixa barra em cima e embaixo`() {
        val area = areaDaImagem(larguraArea, alturaArea, larguraQuadro, alturaQuadro)
        assertNotNull(area)
        area!!
        assertEquals(0f, area.esquerda, 0.01f)
        assertEquals(1080f, area.largura, 0.01f)
        assertEquals(607.5f, area.altura, 0.01f)
        assertEquals((2400f - 607.5f) / 2f, area.topo, 0.01f)
    }

    @Test
    fun `centro da area cai no centro do monitor`() {
        val ponto = mapearToqueParaMonitor(540f, 1200f, larguraArea, alturaArea, larguraQuadro, alturaQuadro)
        assertNotNull(ponto)
        assertEquals(0.5, ponto!!.x, 0.001)
        assertEquals(0.5, ponto.y, 0.001)
    }

    @Test
    fun `cantos da imagem chegam em zero e um`() {
        val area = areaDaImagem(larguraArea, alturaArea, larguraQuadro, alturaQuadro)!!
        val superiorEsquerdo = mapearToqueParaMonitor(
            area.esquerda, area.topo, larguraArea, alturaArea, larguraQuadro, alturaQuadro
        )
        val inferiorDireito = mapearToqueParaMonitor(
            area.direita, area.base, larguraArea, alturaArea, larguraQuadro, alturaQuadro
        )
        assertEquals(0.0, superiorEsquerdo!!.x, 0.001)
        assertEquals(0.0, superiorEsquerdo.y, 0.001)
        assertEquals(1.0, inferiorDireito!!.x, 0.001)
        assertEquals(1.0, inferiorDireito.y, 0.001)
    }

    @Test
    fun `toque na barra preta nao vira posicao`() {
        assertNull(mapearToqueParaMonitor(540f, 10f, larguraArea, alturaArea, larguraQuadro, alturaQuadro))
        assertNull(mapearToqueParaMonitor(540f, 2390f, larguraArea, alturaArea, larguraQuadro, alturaQuadro))
    }

    @Test
    fun `celular deitado com monitor em pe deixa barra nas laterais`() {
        val area = areaDaImagem(2400, 1080, 1080, 1920)!!
        assertTrue(area.esquerda > 0f)
        assertEquals(0f, area.topo, 0.01f)
        assertEquals(1080f, area.altura, 0.01f)
        assertNull(mapearToqueParaMonitor(5f, 540f, 2400, 1080, 1080, 1920))
        assertNotNull(mapearToqueParaMonitor(1200f, 540f, 2400, 1080, 1080, 1920))
    }

    @Test
    fun `monitor ultra largo tambem chega aos cantos`() {
        val ponto = mapearToqueParaMonitor(1079f, 1200f, 1080, 2400, 3440, 1440)
        assertEquals(1.0, ponto!!.x, 0.01)
        assertEquals(0.5, ponto.y, 0.01)
    }

    @Test
    fun `com zoom no centro o mesmo ponto continua no centro`() {
        val visual = Visualizacao(zoom = 2f)
        val ponto = mapearToqueParaMonitor(
            540f, 1200f, larguraArea, alturaArea, larguraQuadro, alturaQuadro, visual
        )
        assertEquals(0.5, ponto!!.x, 0.001)
        assertEquals(0.5, ponto.y, 0.001)
    }

    @Test
    fun `zoom com deslocamento revela o lado direito do monitor`() {
        // Ampliado em 2x e arrastado o máximo para a esquerda: a borda direita
        // do monitor encosta na borda direita da tela.
        val visual = Visualizacao(zoom = 2f, deslocX = -540f, deslocY = 0f)
        val ponto = mapearToqueParaMonitor(
            1080f, 1200f, larguraArea, alturaArea, larguraQuadro, alturaQuadro, visual
        )
        assertEquals(1.0, ponto!!.x, 0.005)
    }

    @Test
    fun `deslocamento nao passa do limite da imagem ampliada`() {
        val visual = aplicarDeslocamento(Visualizacao(zoom = 2f), 9000f, 9000f, larguraArea, alturaArea)
        assertEquals(540f, visual.deslocX, 0.01f)
        assertEquals(1200f, visual.deslocY, 0.01f)
    }

    @Test
    fun `pinca respeita o intervalo de uma a quatro vezes`() {
        val ampliado = aplicarPinca(Visualizacao(), 10f, 540f, 1200f, larguraArea, alturaArea)
        assertEquals(4f, ampliado.zoom, 0.001f)
        val reduzido = aplicarPinca(ampliado, 0.01f, 540f, 1200f, larguraArea, alturaArea)
        assertEquals(1f, reduzido.zoom, 0.001f)
        assertEquals(0f, reduzido.deslocX, 0.01f)
        assertEquals(0f, reduzido.deslocY, 0.01f)
    }

    @Test
    fun `pinca mantem debaixo dos dedos o ponto que estava ali`() {
        val foco = 300f to 900f
        val antes = mapearToqueParaMonitor(
            foco.first, foco.second, larguraArea, alturaArea, larguraQuadro, alturaQuadro
        )!!
        val visual = aplicarPinca(Visualizacao(), 2f, foco.first, foco.second, larguraArea, alturaArea)
        val depois = mapearToqueParaMonitor(
            foco.first, foco.second, larguraArea, alturaArea, larguraQuadro, alturaQuadro, visual
        )!!
        assertEquals(antes.x, depois.x, 0.01)
        assertEquals(antes.y, depois.y, 0.01)
    }

    @Test
    fun `area sem tamanho nao mapeia nada`() {
        assertNull(mapearToqueParaMonitor(10f, 10f, 0, 0, larguraQuadro, alturaQuadro))
        assertNull(mapearToqueParaMonitor(10f, 10f, larguraArea, alturaArea, 0, 0))
    }
}
