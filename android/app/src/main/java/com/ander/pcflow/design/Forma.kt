package com.ander.pcflow.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Raios de canto do sistema visual: 8 / 14 / 20 / 999. */
object RaioPcFlow {
    val controle = 8.dp
    val cartao = 14.dp
    val painel = 20.dp
    val pilula = 999.dp
}

@Immutable
data class FormasPcFlow(
    // CornerBasedShape, e não Shape: é o que o Shapes do Material3 exige para
    // conseguir derivar os cantos dos componentes internos dele.
    val controle: CornerBasedShape = RoundedCornerShape(RaioPcFlow.controle),
    val cartao: CornerBasedShape = RoundedCornerShape(RaioPcFlow.cartao),
    val painel: CornerBasedShape = RoundedCornerShape(RaioPcFlow.painel),
    val pilula: CornerBasedShape = RoundedCornerShape(RaioPcFlow.pilula)
)

val FormasPadraoPcFlow = FormasPcFlow()

val LocalFormasPcFlow = staticCompositionLocalOf { FormasPadraoPcFlow }

/** Escala de espaçamento: 4 / 8 / 12 / 16 / 24 / 32. Nada fora dela. */
object EspacoPcFlow {
    val x4 = 4.dp
    val x8 = 8.dp
    val x12 = 12.dp
    val x16 = 16.dp
    val x24 = 24.dp
    val x32 = 32.dp
}

object DimensaoPcFlow {
    /** Nada clicável abaixo disso — regra 5 do contrato. */
    val alvoMinimo = 48.dp
    val borda = 1.dp
    /** Folga interna de painel e diálogo. */
    val folgaPainel = EspacoPcFlow.x24
    /** Folga interna de item de lista. */
    val folgaItem = EspacoPcFlow.x16
}
