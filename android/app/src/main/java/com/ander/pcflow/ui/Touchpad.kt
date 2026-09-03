package com.ander.pcflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.pcflow.rede.SessaoPcFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private val FundoTouchpad = Color(0xFF12171D)

/** Ajustes do touchpad, persistidos pelo chamador. */
data class AjustesTouchpad(
    val sensibilidade: Float = 1.6f,
    val velocidadeScroll: Float = 1.0f,
    val inverterScroll: Boolean = false,
    val vibrar: Boolean = true,
    val aceleracao: Boolean = true
)

/**
 * Área de toque que vira mouse.
 *
 * A versão anterior encadeava detectTapGestures + detectDragGestures, o que
 * perdia eventos e não tinha scroll, arraste nem botão do meio. Aqui o gesto é
 * tratado num único laço, garantindo:
 *   1 dedo movendo ......... move o ponteiro (com aceleração opcional)
 *   1 toque curto .......... clique esquerdo
 *   2 toques curtos ........ duplo clique (o Windows junta os dois cliques)
 *   2 dedos toque .......... clique direito
 *   3 dedos toque .......... clique do meio
 *   2 dedos movendo ........ rolagem vertical e horizontal
 *   toque longo + arrastar . arrasta segurando o botão esquerdo
 */
@Composable
fun Touchpad(
    ajustes: AjustesTouchpad,
    modifier: Modifier = Modifier,
    rodape: String = "Toque para clicar · 2 dedos rolam · segure para arrastar"
) {
    val haptico = LocalHapticFeedback.current
    val ajustesAtuais by rememberUpdatedState(ajustes)
    val arrastando = remember { mutableStateOf(false) }

    Box(
        modifier
            .background(FundoTouchpad, RoundedCornerShape(24.dp))
            .border(1.dp, DouradoFraco, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val primeiro = awaitFirstDown(requireUnconsumed = false)
                    val cfg = ajustesAtuais

                    var maxDedos = 1
                    var moveu = false
                    var segurando = false
                    var posUmDedo = primeiro.position
                    var centroAnterior: Offset? = null
                    var dedosAnterior = 1
                    var sobraScrollY = 0f
                    var sobraScrollX = 0f
                    val inicio = System.currentTimeMillis()

                    while (true) {
                        // O timeout serve para detectar toque longo sem sair do laço.
                        val evento = withTimeoutOrNull(if (!segurando && !moveu) 420L else Long.MAX_VALUE) {
                            awaitPointerEvent()
                        }

                        if (evento == null) {
                            // Toque parado por 420 ms: entra em modo arraste.
                            segurando = true
                            arrastando.value = true
                            SessaoPcFlow.clicar("left", "down")
                            if (cfg.vibrar) haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue
                        }

                        val ativos: List<PointerInputChange> = evento.changes.filter { it.pressed }
                        if (ativos.isEmpty()) break
                        if (ativos.size > maxDedos) maxDedos = ativos.size

                        if (ativos.size >= 2) {
                            val centro = ativos.fold(Offset.Zero) { acc, c -> acc + c.position } /
                                ativos.size.toFloat()
                            if (dedosAnterior != ativos.size) centroAnterior = centro
                            val anterior = centroAnterior ?: centro
                            val delta = centro - anterior
                            centroAnterior = centro

                            if (abs(delta.x) > 0.01f || abs(delta.y) > 0.01f) moveu = true

                            // 1 "entalhe" da roda do Windows = 120.
                            val sinal = if (cfg.inverterScroll) -1f else 1f
                            sobraScrollY += -delta.y * sinal * cfg.velocidadeScroll * 2.2f
                            sobraScrollX += delta.x * cfg.velocidadeScroll * 2.2f
                            val passosY = (sobraScrollY / 12f).toInt()
                            val passosX = (sobraScrollX / 12f).toInt()
                            if (passosY != 0 || passosX != 0) {
                                sobraScrollY -= passosY * 12f
                                sobraScrollX -= passosX * 12f
                                SessaoPcFlow.rolar(passosX * 120, passosY * 120)
                            }
                        } else {
                            val atual = ativos[0]
                            if (dedosAnterior != 1) posUmDedo = atual.position
                            val delta = atual.position - posUmDedo
                            posUmDedo = atual.position

                            if (abs(delta.x) > 0.01f || abs(delta.y) > 0.01f) {
                                moveu = true
                                var dx = delta.x * cfg.sensibilidade
                                var dy = delta.y * cfg.sensibilidade
                                if (cfg.aceleracao) {
                                    // Movimentos rápidos andam mais: mesma ideia do mouse do Windows.
                                    val velocidade = (abs(dx) + abs(dy)) / 12f
                                    val fator = 1f + velocidade.coerceAtMost(2.2f)
                                    dx *= fator
                                    dy *= fator
                                }
                                SessaoPcFlow.mover(dx, dy)
                            }
                        }

                        dedosAnterior = ativos.size
                        evento.changes.forEach { it.consume() }
                    }

                    val duracao = System.currentTimeMillis() - inicio
                    if (segurando) {
                        SessaoPcFlow.clicar("left", "up")
                        arrastando.value = false
                    } else if (!moveu && duracao < 300) {
                        when (maxDedos) {
                            1 -> SessaoPcFlow.clicar("left")
                            2 -> SessaoPcFlow.clicar("right")
                            else -> SessaoPcFlow.clicar("middle")
                        }
                        if (cfg.vibrar) haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = if (arrastando.value) "Arrastando…" else rodape,
            color = if (arrastando.value) Turquesa else TextoSecundario,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

/** Linha com os três botões do mouse. */
@Composable
fun BotoesMouse(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BotaoMouse("Esquerdo", Modifier.weight(1f)) { SessaoPcFlow.clicar("left") }
        BotaoMouse("Meio", Modifier.weight(1f)) { SessaoPcFlow.clicar("middle") }
        BotaoMouse("Direito", Modifier.weight(1f)) { SessaoPcFlow.clicar("right") }
    }
}

@Composable
private fun BotaoMouse(rotulo: String, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    val haptico = LocalHapticFeedback.current
    Box(
        modifier
            .height(52.dp)
            .background(PainelClaro, RoundedCornerShape(16.dp))
            .border(1.dp, Borda, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    aoTocar()
                    // Consome o resto do gesto para não virar movimento do ponteiro.
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        if (e.changes.none { it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = TextoPrimario, fontSize = 13.sp)
    }
}

@Composable
fun AreaTouchpadCompleta(ajustes: AjustesTouchpad, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Touchpad(ajustes, Modifier.weight(1f).fillMaxWidth())
        Box(Modifier.height(12.dp))
        BotoesMouse()
    }
}
