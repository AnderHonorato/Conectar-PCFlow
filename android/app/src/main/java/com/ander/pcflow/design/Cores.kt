package com.ander.pcflow.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta única do PCFlow (seção 6 do contrato v2). Os valores são os mesmos no
 * Windows e no Android — é isso que faz as duas telas parecerem o mesmo produto.
 */
@Immutable
data class CoresPcFlow(
    val fundo: Color = Color(0xFF0E1216),
    val superficie: Color = Color(0xFF161B22),
    val superficieElevada: Color = Color(0xFF1C232C),
    val borda: Color = Color(0xFF262D37),
    val bordaFoco: Color = Color(0xFF3A434F),
    val acao: Color = Color(0xFFF2AA2E),
    val acaoPressionada: Color = Color(0xFFD9931D),
    val seguro: Color = Color(0xFF14D3C3),
    val erro: Color = Color(0xFFFF7A70),
    val texto: Color = Color(0xFFECF0F4),
    val textoSecundario: Color = Color(0xFF98A2AE),
    val textoDesabilitado: Color = Color(0xFF5C6672)
) {
    /**
     * Rótulo em cima do dourado. O dourado é claro demais para texto branco:
     * o contraste vem de escrever com o próprio fundo da tela.
     */
    val sobreAcao: Color get() = fundo

    /** Preenchimento discreto de um acento — pílula, realce de seleção, faixa. */
    fun tingir(cor: Color, alfa: Float = 0.14f): Color = cor.copy(alpha = alfa)

    /** Contorno de um acento, um pouco mais presente que o preenchimento. */
    fun contornar(cor: Color, alfa: Float = 0.38f): Color = cor.copy(alpha = alfa)
}

val CoresEscuroPcFlow = CoresPcFlow()

/** Véu do diálogo: preto a 55%, igual nas duas plataformas. */
const val OPACIDADE_VEU = 0.55f

val LocalCoresPcFlow = staticCompositionLocalOf { CoresEscuroPcFlow }
