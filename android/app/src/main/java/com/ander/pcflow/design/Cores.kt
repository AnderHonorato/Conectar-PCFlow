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
    /** Falso só no tema claro — decide o esquema do Material e a barra do sistema. */
    val escuro: Boolean = true,
    val fundo: Color = Color(0xFF0E1216),
    val superficie: Color = Color(0xFF161B22),
    val superficieElevada: Color = Color(0xFF1C232C),
    val borda: Color = Color(0xFF262D37),
    val bordaFoco: Color = Color(0xFF3A434F),
    val acao: Color = Color(0xFFF2AA2E),
    val acaoPressionada: Color = Color(0xFFD9931D),
    /**
     * Dourado quando ele é *texto*, e não preenchimento. No claro, o dourado
     * de botão não tem contraste sobre fundo claro; escrito, precisa ser mais
     * fundo. No escuro os dois são a mesma cor.
     */
    val acaoTexto: Color = Color(0xFFF2AA2E),
    val seguro: Color = Color(0xFF14D3C3),
    val erro: Color = Color(0xFFFF7A70),
    val texto: Color = Color(0xFFECF0F4),
    val textoSecundario: Color = Color(0xFF98A2AE),
    val textoDesabilitado: Color = Color(0xFF5C6672),
    /**
     * Rótulo em cima do dourado. O dourado é claro demais para texto branco:
     * o contraste vem de escrever em tinta escura, nos dois temas.
     */
    val sobreAcao: Color = Color(0xFF0E1216)
) {

    /** Preenchimento discreto de um acento — pílula, realce de seleção, faixa. */
    fun tingir(cor: Color, alfa: Float = 0.14f): Color = cor.copy(alpha = alfa)

    /** Contorno de um acento, um pouco mais presente que o preenchimento. */
    fun contornar(cor: Color, alfa: Float = 0.38f): Color = cor.copy(alpha = alfa)
}

val CoresEscuroPcFlow = CoresPcFlow()

/**
 * Tema claro. Não é o escuro com as cores trocadas na mão: cada papel foi
 * refeito para manter o contraste do contrato — texto acima de 7:1 sobre o
 * fundo, apoio acima de 4.5:1, e os acentos escurecidos o bastante para
 * continuarem legíveis como texto sobre branco. O dourado claro passaria
 * despercebido, por isso vira âmbar profundo.
 */
val CoresClaroPcFlow = CoresPcFlow(
    escuro = false,
    fundo = Color(0xFFF3F5F8),
    superficie = Color(0xFFFFFFFF),
    superficieElevada = Color(0xFFEAEEF3),
    borda = Color(0xFFD3D9E1),
    bordaFoco = Color(0xFF98A3B0),
    acao = Color(0xFFF2AA2E),
    acaoPressionada = Color(0xFFD9931D),
    acaoTexto = Color(0xFF8A5300),
    seguro = Color(0xFF07786F),
    erro = Color(0xFFB3271E),
    texto = Color(0xFF10151A),
    textoSecundario = Color(0xFF4C5763),
    textoDesabilitado = Color(0xFF767F8A),
    sobreAcao = Color(0xFF10151A)
)

/** Véu do diálogo: preto a 55%, igual nas duas plataformas. */
const val OPACIDADE_VEU = 0.55f

val LocalCoresPcFlow = staticCompositionLocalOf { CoresEscuroPcFlow }
