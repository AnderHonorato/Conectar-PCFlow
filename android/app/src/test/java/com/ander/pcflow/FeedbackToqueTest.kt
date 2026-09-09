package com.ander.pcflow

import com.ander.pcflow.sessao.CicloFeedbackToque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackToqueTest {

    @Test
    fun `a marca nasce onde o dedo tocou e nao no centro`() {
        val ciclo = CicloFeedbackToque()
        ciclo.tocar(320f, 980f)
        val marca = ciclo.marca
        assertNotNull(marca)
        assertEquals(320f, marca!!.x, 0.01f)
        assertEquals(980f, marca.y, 0.01f)
        assertTrue(marca.pressionada)
        assertEquals(1f, ciclo.opacidade(0L), 0.001f)
    }

    @Test
    fun `a marca acompanha o arrasto`() {
        val ciclo = CicloFeedbackToque()
        ciclo.tocar(10f, 10f)
        ciclo.arrastar(200f, 400f)
        assertEquals(200f, ciclo.marca!!.x, 0.01f)
        assertEquals(400f, ciclo.marca!!.y, 0.01f)
    }

    @Test
    fun `depois de soltar some em no maximo quatrocentos e cinquenta ms`() {
        val ciclo = CicloFeedbackToque(duracaoSumicoMs = 450L)
        ciclo.tocar(10f, 10f)
        ciclo.soltar(1_000L)
        assertTrue(ciclo.visivel(1_100L))
        assertTrue(ciclo.opacidade(1_225L) in 0.4f..0.6f)
        assertFalse(ciclo.visivel(1_450L))
        ciclo.envelhecer(1_500L)
        assertNull(ciclo.marca)
    }

    @Test
    fun `soltar nao move mais a marca`() {
        val ciclo = CicloFeedbackToque()
        ciclo.tocar(10f, 10f)
        ciclo.soltar(100L)
        ciclo.arrastar(999f, 999f)
        assertEquals(10f, ciclo.marca!!.x, 0.01f)
    }

    @Test
    fun `cancelar apaga na hora`() {
        val ciclo = CicloFeedbackToque()
        ciclo.tocar(10f, 10f)
        ciclo.limpar()
        assertNull(ciclo.marca)
        assertEquals(0f, ciclo.opacidade(0L), 0.001f)
    }

    @Test
    fun `com animacao reduzida a marca some assim que o dedo sai`() {
        val ciclo = CicloFeedbackToque(duracaoSumicoMs = 0L)
        ciclo.tocar(10f, 10f)
        assertTrue(ciclo.visivel(0L))
        ciclo.soltar(10L)
        assertNull(ciclo.marca)
        assertFalse(ciclo.visivel(10L))
    }

    @Test
    fun `um toque novo recomeca o ciclo em outro lugar`() {
        val ciclo = CicloFeedbackToque()
        ciclo.tocar(10f, 10f)
        ciclo.soltar(100L)
        ciclo.tocar(500f, 700f)
        assertTrue(ciclo.marca!!.pressionada)
        assertEquals(500f, ciclo.marca!!.x, 0.01f)
        assertEquals(1f, ciclo.opacidade(120L), 0.001f)
    }

    @Test
    fun `soltar sem ter tocado nao inventa marca`() {
        val ciclo = CicloFeedbackToque()
        ciclo.soltar(10L)
        ciclo.arrastar(1f, 1f)
        assertNull(ciclo.marca)
    }
}
