package com.ander.pcflow.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentidadeTest {

    @Test
    fun `id de nove digitos vira tres grupos de tres`() {
        assertEquals("145 929 922", Identidade.formatarId("145929922"))
    }

    @Test
    fun `agrupamento comeca pela direita`() {
        assertEquals("1 145 929 922", Identidade.formatarId("1145929922"))
        assertEquals("92 922", Identidade.formatarId("92922"))
        assertEquals("2", Identidade.formatarId("2"))
    }

    @Test
    fun `formatar ignora o que nao e digito e nao quebra com vazio`() {
        assertEquals("145 929 922", Identidade.formatarId("145 929 922"))
        assertEquals("145 929 922", Identidade.formatarId("PC-145.929.922"))
        assertEquals("", Identidade.formatarId(""))
        assertEquals("", Identidade.formatarId("sem número"))
    }

    @Test
    fun `campo de id aceita so digitos e para no tamanho do id`() {
        assertEquals("145929922", Identidade.aoDigitarId("145 929 922 77"))
        assertEquals("123", Identidade.aoDigitarId("a1b2c3"))
    }

    @Test
    fun `id valido so com nove digitos`() {
        val bom = Identidade.validarId("145 929 922")
        assertTrue(bom.valido)
        assertEquals("145929922", bom.id)
        assertNull(bom.mensagem)
    }

    @Test
    fun `id invalido explica o motivo`() {
        assertEquals(Identidade.MotivoId.VAZIO, Identidade.validarId("   ").motivo)
        assertEquals(Identidade.MotivoId.CURTO, Identidade.validarId("1459299").motivo)
        assertEquals(Identidade.MotivoId.LONGO, Identidade.validarId("1459299221").motivo)
        assertEquals(Identidade.MotivoId.NAO_NUMERICO, Identidade.validarId("14592992A").motivo)
        assertNotNull(Identidade.validarId("1459299").mensagem)
        assertFalse(Identidade.validarId("1459299").valido)
    }

    @Test
    fun `codigo vazio e codigo sem sentido nao passam`() {
        assertEquals(Identidade.MotivoCodigo.VAZIO, Identidade.validarCodigo("", "").motivo)
        assertEquals(
            Identidade.MotivoCodigo.INVALIDO,
            Identidade.validarCodigo("ABC", "").motivo
        )
    }

    @Test
    fun `codigo direto vale sem servidor e codigo de servidor exige endereco`() {
        val direto = codificar(byteArrayOf(1) + byteArrayOf(192.toByte(), 168.toByte(), 0, 12) + byteArrayOf(0xB1.toByte(), 0x90.toByte()) + ByteArray(16) { it.toByte() })
        assertTrue(Identidade.validarCodigo(direto, "").valido)

        val servidor = codificar(byteArrayOf(2) + byteArrayOf(0, 0, 4, 0xD2.toByte()) + ByteArray(16) { it.toByte() })
        assertEquals(
            Identidade.MotivoCodigo.SERVIDOR_FALTANDO,
            Identidade.validarCodigo(servidor, "").motivo
        )
        assertTrue(Identidade.validarCodigo(servidor, "relay.exemplo.com").valido)
    }

    /** Mesmo Base32 (Crockford) que o PCFlow do Windows usa para gerar o código. */
    private fun codificar(bytes: ByteArray): String {
        val alfabeto = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val texto = StringBuilder()
        var acumulador = 0
        var bits = 0
        for (b in bytes) {
            acumulador = (acumulador shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                texto.append(alfabeto[(acumulador shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) texto.append(alfabeto[(acumulador shl (5 - bits)) and 0x1F])
        return texto.toString()
    }
}
