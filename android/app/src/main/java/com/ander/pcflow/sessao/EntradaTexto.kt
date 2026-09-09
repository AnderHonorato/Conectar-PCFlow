package com.ander.pcflow.sessao

/** O que a digitação ao vivo precisa mandar para o PC. */
sealed interface AcaoTeclado {
    /** Texto literal, com acento, ç e emoji (pares substitutos) inteiros. */
    data class Digitar(val texto: String) : AcaoTeclado

    /** Quantos BACKSPACE mandar, contados em caracteres visíveis. */
    data class Apagar(val quantidade: Int) : AcaoTeclado

    /** Tecla nomeada do protocolo — ENTER, TAB, F5… */
    data class Tecla(val nome: String) : AcaoTeclado
}

/** Depois disso o campo invisível é zerado para não crescer sem fim. */
const val LIMITE_CAMPO_AO_VIVO = 160

/**
 * Diferença entre o que o PC já recebeu e o que está no campo agora.
 *
 * Mandar o campo inteiro a cada tecla duplicaria tudo, e mandar só o último
 * caractere quebra com IME: Gboard e o teclado da Samsung reescrevem a palavra
 * em composição a cada toque ("ca" vira "ção" numa tacada), corrigem
 * automaticamente e trocam letra do meio. Por isso a conta é sempre a mesma:
 * apaga do cursor para trás tudo que mudou e redigita o restante — o cursor do
 * PC está no fim do que já foi enviado, então essa é a única sequência que
 * chega ao mesmo resultado.
 *
 * A quebra de linha vira ENTER: digitar "\n" literal não aciona botão nem envia
 * mensagem em aplicativo nenhum do Windows.
 */
fun calcularDeltaTexto(anterior: String, atual: String): List<AcaoTeclado> {
    if (anterior == atual) return emptyList()

    var comum = 0
    val limite = minOf(anterior.length, atual.length)
    while (comum < limite && anterior[comum] == atual[comum]) comum++
    // Nunca cortar um emoji no meio: o par substituto precisa sair inteiro.
    if (comum > 0 && comum < limite &&
        Character.isHighSurrogate(anterior[comum - 1]) && Character.isLowSurrogate(atual[comum])
    ) comum--

    val acoes = mutableListOf<AcaoTeclado>()
    val removido = anterior.substring(comum)
    if (removido.isNotEmpty()) {
        val quantidade = removido.codePointCount(0, removido.length)
        if (quantidade > 0) acoes += AcaoTeclado.Apagar(quantidade)
    }

    val acrescentado = atual.substring(comum)
    if (acrescentado.isNotEmpty()) acoes += quebrarLinhas(acrescentado)
    return acoes
}

/** Texto vindo do campo "escrever e enviar", que aceita várias linhas. */
fun acoesDeTextoLivre(texto: String): List<AcaoTeclado> =
    if (texto.isEmpty()) emptyList() else quebrarLinhas(texto)

private fun quebrarLinhas(texto: String): List<AcaoTeclado> {
    if (!texto.contains('\n')) return listOf(AcaoTeclado.Digitar(texto))
    val acoes = mutableListOf<AcaoTeclado>()
    val partes = texto.split('\n')
    partes.forEachIndexed { indice, parte ->
        if (parte.isNotEmpty()) acoes += AcaoTeclado.Digitar(parte)
        if (indice < partes.lastIndex) acoes += AcaoTeclado.Tecla("ENTER")
    }
    return acoes
}

/**
 * O campo da digitação ao vivo só pode ser zerado fora de composição: apagar
 * texto que o IME ainda considera "em construção" faz o teclado repetir letra.
 */
fun deveZerarCampoAoVivo(texto: String, emComposicao: Boolean): Boolean =
    !emComposicao && texto.length >= LIMITE_CAMPO_AO_VIVO
