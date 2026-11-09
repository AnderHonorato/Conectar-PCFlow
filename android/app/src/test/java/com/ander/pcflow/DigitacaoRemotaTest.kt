package com.ander.pcflow

import org.junit.Assert.assertEquals
import org.junit.Test

class DigitacaoRemotaTest {
    @Test fun inserirCaracteresEmTempoReal() {
        assertEquals(
            listOf(ComandoDigitacao("texto", "Olá")),
            calcularEdicaoRemota("", "Olá")
        )
    }

    @Test fun apagarAoFinalNaoApagaOutrasLetras() {
        assertEquals(
            listOf(ComandoDigitacao("tecla", "BACKSPACE")),
            calcularEdicaoRemota("Bom dia", "Bom di")
        )
    }

    @Test fun correcaoAutomaticaSubstituiSomenteLetraAlterada() {
        assertEquals(
            listOf(
                ComandoDigitacao("tecla", "LEFT"),
                ComandoDigitacao("tecla", "BACKSPACE"),
                ComandoDigitacao("texto", "r"),
                ComandoDigitacao("tecla", "RIGHT")
            ),
            calcularEdicaoRemota("caso", "caro")
        )
    }

    @Test fun editarNoMeioPreservaSufixo() {
        assertEquals(
            listOf(
                ComandoDigitacao("tecla", "LEFT"),
                ComandoDigitacao("tecla", "LEFT"),
                ComandoDigitacao("texto", "X"),
                ComandoDigitacao("tecla", "RIGHT"),
                ComandoDigitacao("tecla", "RIGHT")
            ),
            calcularEdicaoRemota("abcd", "abXcd")
        )
    }

    @Test fun emojiInteiroExigeUmBackspace() {
        assertEquals(
            listOf(ComandoDigitacao("tecla", "BACKSPACE")),
            calcularEdicaoRemota("Olá 😀", "Olá ")
        )
    }

    @Test fun emojiNovoNaoSeparaParUnicode() {
        assertEquals(
            listOf(ComandoDigitacao("texto", "🧡")),
            calcularEdicaoRemota("oi", "oi🧡")
        )
    }

    @Test fun novaLinhaViraEnter() {
        assertEquals(
            listOf(
                ComandoDigitacao("tecla", "ENTER"),
                ComandoDigitacao("texto", "mundo")
            ),
            calcularEdicaoRemota("olá", "olá\nmundo")
        )
    }

    @Test fun semAlteracaoNaoEnviaComando() {
        assertEquals(emptyList<ComandoDigitacao>(), calcularEdicaoRemota("teste", "teste"))
    }
}
