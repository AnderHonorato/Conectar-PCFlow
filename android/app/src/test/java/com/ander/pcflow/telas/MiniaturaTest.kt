package com.ander.pcflow.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniaturaTest {

    @Test
    fun `mesmo id gera sempre a mesma miniatura`() {
        val primeira = Miniatura.de("145929922")
        val segunda = Miniatura.de("145929922")
        assertEquals(primeira, segunda)
    }

    @Test
    fun `ids diferentes geram miniaturas diferentes`() {
        val ids = listOf("145929922", "101362037", "160107334", "133455211", "892771502")
        val padroes = ids.map { Miniatura.de(it) }
        assertEquals(ids.size, padroes.toSet().size)
    }

    @Test
    fun `o padrao e espelhado na vertical`() {
        val padrao = Miniatura.de("160107334")
        for (linha in 0 until PadraoMiniatura.LINHAS) {
            for (coluna in 0 until PadraoMiniatura.COLUNAS) {
                val espelhada = PadraoMiniatura.COLUNAS - 1 - coluna
                assertEquals(
                    "linha $linha coluna $coluna",
                    padrao.aceso(coluna, linha),
                    padrao.aceso(espelhada, linha)
                )
            }
        }
    }

    @Test
    fun `tem uma celula por posicao da grade e cores da paleta`() {
        val padrao = Miniatura.de("892771502")
        assertEquals(PadraoMiniatura.COLUNAS * PadraoMiniatura.LINHAS, padrao.celulas.size)
        assertTrue(Miniatura.PALETA.contains(padrao.corTraco))
        assertTrue(Miniatura.FUNDOS.contains(padrao.corFundo))
    }

    @Test
    fun `id em branco nao quebra e cai num padrao estavel`() {
        assertEquals(Miniatura.de(""), Miniatura.de("   ".trim()))
        assertEquals(PadraoMiniatura.COLUNAS * PadraoMiniatura.LINHAS, Miniatura.de("").celulas.size)
    }
}
