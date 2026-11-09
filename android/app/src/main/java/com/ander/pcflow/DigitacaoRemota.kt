package com.ander.pcflow

/**
 * Converte a nova edição do teclado Android em ações ordenadas para o Windows.
 * Compara pontos Unicode para não enviar duas exclusões ao apagar um emoji.
 * O cursor remoto deve estar no fim do texto anteriormente confirmado.
 */
internal data class ComandoDigitacao(val tipo: String, val valor: String)

internal fun calcularEdicaoRemota(anterior: String, novo: String): List<ComandoDigitacao> {
    if (anterior == novo) return emptyList()

    val origem = anterior.codePoints().toArray()
    val destino = novo.codePoints().toArray()
    var prefixo = 0
    while (prefixo < minOf(origem.size, destino.size) &&
        origem[prefixo] == destino[prefixo]) prefixo++

    var sufixo = 0
    while (sufixo < minOf(origem.size - prefixo, destino.size - prefixo) &&
        origem[origem.lastIndex - sufixo] == destino[destino.lastIndex - sufixo]) sufixo++

    val inicioInsercao = novo.offsetByCodePoints(0, prefixo)
    val fimInsercao = novo.offsetByCodePoints(novo.length, -sufixo)
    val inserido = novo.substring(inicioInsercao, fimInsercao)

    return buildList {
        repeat(sufixo) { add(ComandoDigitacao("tecla", "LEFT")) }
        repeat(origem.size - prefixo - sufixo) {
            add(ComandoDigitacao("tecla", "BACKSPACE"))
        }
        val linhas = inserido.split("\n")
        linhas.forEachIndexed { indice, trecho ->
            if (trecho.isNotEmpty()) add(ComandoDigitacao("texto", trecho))
            if (indice < linhas.lastIndex) add(ComandoDigitacao("tecla", "ENTER"))
        }
        repeat(sufixo) { add(ComandoDigitacao("tecla", "RIGHT")) }
    }
}
