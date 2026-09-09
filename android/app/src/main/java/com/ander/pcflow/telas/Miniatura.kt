package com.ander.pcflow.telas

/**
 * Miniatura de dispositivo gerada a partir do ID.
 *
 * Decisão 4 de TELAS_V13: nada de foto de banco de imagem. O padrão é
 * determinístico, então o mesmo computador aparece sempre com a mesma cara —
 * é isso que faz a pessoa reconhecer a máquina antes de ler o nome.
 *
 * As cores são ARGB em Int para esta parte não depender do Compose e poder
 * ser testada na JVM.
 */
data class PadraoMiniatura(
    val corFundo: Int,
    val corTraco: Int,
    val celulas: List<Boolean>
) {
    val colunas: Int get() = COLUNAS
    val linhas: Int get() = LINHAS

    fun aceso(coluna: Int, linha: Int): Boolean = celulas[linha * COLUNAS + coluna]

    companion object {
        const val COLUNAS = 5
        const val LINHAS = 5
    }
}

object Miniatura {

    /**
     * Tons frios e quentes discretos. Vermelho fica de fora: no PCFlow ele
     * significa erro, e uma miniatura não pode dar a impressão de problema.
     */
    val PALETA = listOf(
        0xFFF2AA2E.toInt(), // dourado
        0xFF14D3C3.toInt(), // turquesa
        0xFF5B8DEF.toInt(), // azul
        0xFF9B7BE8.toInt(), // violeta
        0xFF3FBF7F.toInt(), // verde
        0xFFE08A4C.toInt()  // âmbar queimado
    )

    val FUNDOS = listOf(
        0xFF1C232C.toInt(),
        0xFF17222B.toInt(),
        0xFF1E1F2C.toInt(),
        0xFF19242A.toInt()
    )

    /**
     * FNV-1a: pequeno, estável entre versões da linguagem e bem espalhado.
     * Estabilidade importa aqui — o padrão precisa ser o mesmo depois de
     * atualizar o aplicativo.
     */
    fun embaralhar(texto: String): Int {
        var hash = -0x7ee3623b // 2166136261 em 32 bits com sinal
        for (caractere in texto) {
            hash = hash xor caractere.code
            hash *= 16777619
        }
        return hash
    }

    fun de(identificador: String): PadraoMiniatura {
        val semente = embaralhar(identificador.ifBlank { "pcflow" })
        val positivo = semente.toLong() and 0xFFFFFFFFL

        val corTraco = PALETA[(positivo % PALETA.size).toInt()]
        val corFundo = FUNDOS[((positivo shr 8) % FUNDOS.size).toInt()]

        // Metade esquerda sorteada e espelhada: padrão simétrico é mais fácil
        // de reconhecer de relance do que ruído.
        val meia = (PadraoMiniatura.COLUNAS + 1) / 2
        val celulas = ArrayList<Boolean>(PadraoMiniatura.COLUNAS * PadraoMiniatura.LINHAS)
        for (linha in 0 until PadraoMiniatura.LINHAS) {
            val esquerda = BooleanArray(meia) { coluna ->
                val bit = (linha * meia + coluna) % 32
                ((positivo shr bit) and 1L) == 1L
            }
            for (coluna in 0 until PadraoMiniatura.COLUNAS) {
                val espelhada = if (coluna < meia) coluna else PadraoMiniatura.COLUNAS - 1 - coluna
                celulas.add(esquerda[espelhada])
            }
        }
        return PadraoMiniatura(corFundo, corTraco, celulas)
    }
}
