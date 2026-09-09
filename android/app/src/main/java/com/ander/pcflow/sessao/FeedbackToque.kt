package com.ander.pcflow.sessao

/** Onde o dedo está (ou estava) e desde quando ele saiu da tela. */
data class MarcaToque(
    val x: Float,
    val y: Float,
    /** 0 enquanto o dedo está encostado; instante da solta depois disso. */
    val instanteSolta: Long = 0L
) {
    val pressionada: Boolean get() = instanteSolta == 0L
}

/**
 * Ciclo de vida do sinal de toque.
 *
 * O defeito relatado — "animação travada que aparece só ao tocar" e fica parada
 * no meio da tela — vem de guardar só um booleano de "tocou". Aqui a marca nasce
 * na coordenada do dedo, acompanha o arrasto e tem prazo de validade: passados
 * `duracaoSumicoMs` da solta ela deixa de existir, e qualquer cancelamento
 * (giro de tela, troca de monitor, queda da sessão) apaga na hora.
 */
class CicloFeedbackToque(private val duracaoSumicoMs: Long = 450L) {

    var marca: MarcaToque? = null
        private set

    fun tocar(x: Float, y: Float) {
        marca = MarcaToque(x, y)
    }

    fun arrastar(x: Float, y: Float) {
        val atual = marca ?: return
        if (!atual.pressionada) return
        marca = atual.copy(x = x, y = y)
    }

    fun soltar(agora: Long) {
        val atual = marca ?: return
        if (!atual.pressionada) return
        // Com animação reduzida o sumiço é imediato: nada de ponto sobrando.
        if (duracaoSumicoMs <= 0L) marca = null
        else marca = atual.copy(instanteSolta = agora)
    }

    fun limpar() {
        marca = null
    }

    /** Deixa de existir sozinha; quem desenha chama isto a cada quadro. */
    fun envelhecer(agora: Long) {
        val atual = marca ?: return
        if (!atual.pressionada && agora - atual.instanteSolta >= duracaoSumicoMs) marca = null
    }

    /** 1 com o dedo na tela, caindo até 0 no fim do prazo. */
    fun opacidade(agora: Long): Float {
        val atual = marca ?: return 0f
        if (atual.pressionada) return 1f
        if (duracaoSumicoMs <= 0L) return 0f
        val decorrido = (agora - atual.instanteSolta).coerceAtLeast(0L)
        return (1f - decorrido.toFloat() / duracaoSumicoMs).coerceIn(0f, 1f)
    }

    fun visivel(agora: Long): Boolean = opacidade(agora) > 0f
}
