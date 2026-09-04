package com.ander.pcflow

/**
 * Lê o código de acesso que o PCFlow do Windows mostra na tela.
 *
 * O código carrega para onde ir e a identidade do computador, então dá para
 * conectar de qualquer internet sem abrir mão da verificação do certificado.
 * O formato é o mesmo gerado por CodigoAcesso.cs no lado do PC.
 *
 *  tipo 1 (23 bytes): [1] tipo · [4] IPv4 · [2] porta · [16] início do SHA-256
 *  tipo 2 (21 bytes): [1] tipo · [4] identificador no servidor · [16] início do SHA-256
 */
object CodigoAcesso {
    private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIPO_DIRETO = 1
    private const val TIPO_SERVIDOR = 2

    data class Destino(
        val direto: Boolean,
        val host: String,
        val porta: Int,
        val identificadorServidor: Long,
        val impressaoTls: String
    )

    /** Devolve null quando o texto não é um código de acesso válido. */
    fun ler(codigo: String?): Destino? {
        if (codigo.isNullOrBlank()) return null
        val bytes = runCatching { deBase32(codigo) }.getOrNull() ?: return null

        return when {
            bytes.size == 23 && bytes[0].toInt() == TIPO_DIRETO -> {
                val host = (1..4).joinToString(".") { (bytes[it].toInt() and 0xFF).toString() }
                val porta = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
                Destino(true, host, porta, 0, hex(bytes, 7, 16))
            }
            bytes.size == 21 && bytes[0].toInt() == TIPO_SERVIDOR -> {
                var id = 0L
                for (i in 1..4) id = (id shl 8) or (bytes[i].toLong() and 0xFF)
                Destino(false, "", 0, id, hex(bytes, 5, 16))
            }
            else -> null
        }
    }

    /**
     * A impressão do código vem truncada em 16 bytes para o texto caber na tela.
     * Aceita tanto a completa (vinda da descoberta) quanto a truncada, sempre
     * exigindo que o início bata caractere por caractere.
     */
    fun impressaoConfere(doCertificado: String, esperada: String): Boolean {
        val a = normalizar(doCertificado)
        val b = normalizar(esperada)
        if (a.isEmpty() || b.length < 32) return false
        return a.length >= b.length && a.substring(0, b.length) == b
    }

    private fun normalizar(impressao: String) =
        impressao.replace(":", "").replace(" ", "").lowercase()

    private fun hex(bytes: ByteArray, inicio: Int, tamanho: Int) =
        (inicio until inicio + tamanho).joinToString("") { "%02x".format(bytes[it].toInt() and 0xFF) }

    private fun deBase32(texto: String): ByteArray {
        val saida = ArrayList<Byte>(texto.length * 5 / 8 + 1)
        var acumulador = 0
        var bits = 0
        for (caractere in texto) {
            if (caractere == '-' || caractere.isWhitespace()) continue
            val valor = valorDe(caractere)
            require(valor >= 0) { "Caractere inválido no código: $caractere" }
            acumulador = (acumulador shl 5) or valor
            bits += 5
            if (bits >= 8) {
                saida.add(((acumulador shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return saida.toByteArray()
    }

    /** Aceita as trocas que as pessoas fazem sem perceber: O por 0, I e L por 1. */
    private fun valorDe(caractere: Char): Int {
        val c = when (val maiusculo = caractere.uppercaseChar()) {
            'O' -> '0'
            'I', 'L' -> '1'
            'U' -> 'V'
            else -> maiusculo
        }
        return ALFABETO.indexOf(c)
    }
}
