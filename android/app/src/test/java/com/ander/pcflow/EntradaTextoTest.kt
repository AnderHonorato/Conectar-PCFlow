package com.ander.pcflow

import com.ander.pcflow.sessao.AcaoTeclado
import com.ander.pcflow.sessao.EstadoModificador
import com.ander.pcflow.sessao.Modificador
import com.ander.pcflow.sessao.TravaModificadores
import com.ander.pcflow.sessao.acoesDeTextoLivre
import com.ander.pcflow.sessao.calcularDeltaTexto
import com.ander.pcflow.sessao.deveZerarCampoAoVivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaTextoTest {

    @Test
    fun `nada mudou nao manda nada`() {
        assertTrue(calcularDeltaTexto("oi", "oi").isEmpty())
        assertTrue(calcularDeltaTexto("", "").isEmpty())
    }

    @Test
    fun `digitar uma letra manda so a letra`() {
        assertEquals(listOf(AcaoTeclado.Digitar("a")), calcularDeltaTexto("", "a"))
        assertEquals(listOf(AcaoTeclado.Digitar("s")), calcularDeltaTexto("ca", "cas"))
    }

    @Test
    fun `digitar nunca reenvia o texto inteiro`() {
        var enviado = ""
        var campo = ""
        for (letra in "reuniao amanha") {
            val novo = campo + letra
            calcularDeltaTexto(campo, novo).forEach {
                if (it is AcaoTeclado.Digitar) enviado += it.texto
            }
            campo = novo
        }
        assertEquals("reuniao amanha", enviado)
    }

    @Test
    fun `apagar uma letra vira um backspace`() {
        assertEquals(listOf(AcaoTeclado.Apagar(1)), calcularDeltaTexto("casa", "cas"))
    }

    @Test
    fun `apagar tudo conta todos os backspaces`() {
        assertEquals(listOf(AcaoTeclado.Apagar(4)), calcularDeltaTexto("casa", ""))
    }

    @Test
    fun `ime trocando a palavra em composicao apaga o que sobrou e redigita`() {
        // Gboard reescreve a composição inteira: "cao" vira "ção" de uma vez.
        assertEquals(
            listOf(AcaoTeclado.Apagar(3), AcaoTeclado.Digitar("ção")),
            calcularDeltaTexto("cao", "ção")
        )
    }

    @Test
    fun `correcao automatica no meio da frase so refaz o que mudou`() {
        val acoes = calcularDeltaTexto("bom dia teh", "bom dia the")
        assertEquals(listOf(AcaoTeclado.Apagar(2), AcaoTeclado.Digitar("he")), acoes)
    }

    @Test
    fun `acento composto chega como um caractere so`() {
        assertEquals(listOf(AcaoTeclado.Digitar("á")), calcularDeltaTexto("", "á"))
        // "ç" não é "c": o IME trocou a palavra inteira, então ela é refeita inteira.
        assertEquals(
            listOf(AcaoTeclado.Apagar(2), AcaoTeclado.Digitar("ção")),
            calcularDeltaTexto("ca", "ção")
        )
    }

    @Test
    fun `cedilha entra como cedilha`() {
        assertEquals(listOf(AcaoTeclado.Digitar("ç")), calcularDeltaTexto("a", "aç"))
    }

    @Test
    fun `emoji vai inteiro e volta com um backspace so`() {
        val emoji = "🙂"
        assertEquals(listOf(AcaoTeclado.Digitar(emoji)), calcularDeltaTexto("oi ", "oi $emoji"))
        assertEquals(listOf(AcaoTeclado.Apagar(1)), calcularDeltaTexto("oi $emoji", "oi "))
    }

    @Test
    fun `emoji nao e cortado no meio do par substituto`() {
        val emoji = "🙂"
        val outro = "😀"
        // Os dois começam com o mesmo caractere alto: cortar ali mandaria meio emoji.
        val acoes = calcularDeltaTexto(emoji, outro)
        assertEquals(listOf(AcaoTeclado.Apagar(1), AcaoTeclado.Digitar(outro)), acoes)
    }

    @Test
    fun `quebra de linha vira tecla enter`() {
        assertEquals(
            listOf(AcaoTeclado.Digitar("oi"), AcaoTeclado.Tecla("ENTER")),
            calcularDeltaTexto("", "oi\n")
        )
        assertEquals(
            listOf(
                AcaoTeclado.Digitar("linha um"),
                AcaoTeclado.Tecla("ENTER"),
                AcaoTeclado.Digitar("linha dois")
            ),
            acoesDeTextoLivre("linha um\nlinha dois")
        )
    }

    @Test
    fun `campo so e zerado fora de composicao`() {
        val longo = "x".repeat(200)
        assertTrue(deveZerarCampoAoVivo(longo, emComposicao = false))
        assertFalse(deveZerarCampoAoVivo(longo, emComposicao = true))
        assertFalse(deveZerarCampoAoVivo("curto", emComposicao = false))
    }
}

class TravaModificadoresTest {

    @Test
    fun `toque arma e o proximo toque solta`() {
        val trava = TravaModificadores()
        trava.alternar(Modificador.CTRL)
        assertEquals(EstadoModificador.ARMADO, trava.estado(Modificador.CTRL))
        assertEquals(listOf("ctrl"), trava.nomes())
        trava.alternar(Modificador.CTRL)
        assertEquals(EstadoModificador.SOLTO, trava.estado(Modificador.CTRL))
    }

    @Test
    fun `modificador armado cai depois de uma tecla e o fixo continua`() {
        val trava = TravaModificadores()
        trava.alternar(Modificador.SHIFT)
        trava.fixar(Modificador.CTRL)
        assertEquals(listOf("ctrl", "shift"), trava.nomes().sorted())
        trava.consumir()
        assertEquals(listOf("ctrl"), trava.nomes())
        assertEquals(EstadoModificador.FIXO, trava.estado(Modificador.CTRL))
    }

    @Test
    fun `limpar nao deixa modificador preso`() {
        val trava = TravaModificadores()
        trava.fixar(Modificador.ALT)
        trava.alternar(Modificador.WIN)
        trava.limpar()
        assertTrue(trava.nomes().isEmpty())
        assertFalse(trava.algumAtivo)
    }
}
