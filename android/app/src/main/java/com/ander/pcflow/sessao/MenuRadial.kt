package com.ander.pcflow.sessao

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ander.pcflow.design.DURACAO_PAINEL_MS
import com.ander.pcflow.design.DimensaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.IconePcFlow
import com.ander.pcflow.design.OPACIDADE_VEU
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.duracaoPcFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Uma opção do menu radial ou da barra compacta. */
data class ItemRadial(
    val icone: Icone,
    val rotulo: String,
    val cor: Color? = null,
    val aoEscolher: () -> Unit
)

private const val PREFS_SESSAO = "pcflow_sessao"
private const val CHAVE_X = "bolha_fracao_x"
private const val CHAVE_Y = "bolha_fracao_y"

/**
 * Bolha flutuante com menu radial.
 *
 * Toque curto abre e fecha a barra compacta; pressão longa abre o radial. A
 * bolha é arrastável e encaixa na borda mais próxima, porque numa sessão em
 * tela cheia qualquer controle fixo acaba justamente em cima do que a pessoa
 * quer ver. A posição fica guardada entre sessões.
 */
@Composable
fun CamadaBolhaSessao(
    itens: List<ItemRadial>,
    acoesRapidas: List<ItemRadial>,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    val densidade = LocalDensity.current
    val contexto = LocalContext.current
    val prefs = remember(contexto) {
        contexto.getSharedPreferences(PREFS_SESSAO, Context.MODE_PRIVATE)
    }

    val diametro = with(densidade) { 56.dp.toPx() }
    val margem = with(densidade) { 12.dp.toPx() }
    val raioMenu = with(densidade) { 132.dp.toPx() }
    val folgaArco = with(densidade) { 150.dp.toPx() }
    val toleranciaArraste = with(densidade) { 8.dp.toPx() }

    var tamanho by remember { mutableStateOf(IntSize.Zero) }
    var x by remember { mutableFloatStateOf(-1f) }
    var y by remember { mutableFloatStateOf(-1f) }
    var barraAberta by remember { mutableStateOf(false) }
    var radialAberto by remember { mutableStateOf(false) }

    // Primeira posição: canto direito, na altura do polegar.
    LaunchedEffect(tamanho) {
        if (tamanho.width == 0 || tamanho.height == 0 || x >= 0f) return@LaunchedEffect
        val fracaoX = prefs.getFloat(CHAVE_X, 1f)
        val fracaoY = prefs.getFloat(CHAVE_Y, 0.62f)
        x = (tamanho.width - diametro - margem) * fracaoX
        y = ((tamanho.height - diametro - margem) * fracaoY).coerceAtLeast(margem)
    }

    fun encaixar() {
        if (tamanho.width == 0) return
        val limiteX = tamanho.width - diametro - margem
        val limiteY = tamanho.height - diametro - margem
        x = if (x + diametro / 2f < tamanho.width / 2f) margem else limiteX
        y = y.coerceIn(margem, limiteY.coerceAtLeast(margem))
        prefs.edit()
            .putFloat(CHAVE_X, if (limiteX > 0f) (x / limiteX).coerceIn(0f, 1f) else 0f)
            .putFloat(CHAVE_Y, if (limiteY > 0f) (y / limiteY).coerceIn(0f, 1f) else 0f)
            .apply()
    }

    Box(modifier.fillMaxSize().onSizeChanged { tamanho = it }) {
        if (x < 0f) return@Box

        val centroX = x + diametro / 2f
        val centroY = y + diametro / 2f
        val naEsquerda = centroX < tamanho.width / 2f

        if (radialAberto) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = OPACIDADE_VEU))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { radialAberto = false }
            )
            // O arco fica encostado na borda da bolha para deixar livre o meio
            // da tela, que é onde a pessoa está olhando.
            val centroArcoY = centroY.coerceIn(folgaArco, (tamanho.height - folgaArco).coerceAtLeast(folgaArco))
            val anguloBase = if (naEsquerda) 0.0 else 180.0
            val abertura = 180.0
            itens.forEachIndexed { indice, item ->
                val proporcao = if (itens.size == 1) 0.5 else indice.toDouble() / (itens.size - 1)
                val angulo = Math.toRadians(anguloBase - abertura / 2.0 + abertura * proporcao)
                val alvoX = centroX + raioMenu * cos(angulo).toFloat() * (if (naEsquerda) 1f else -1f)
                val alvoY = centroArcoY + raioMenu * sin(angulo).toFloat()
                ItemDoRadial(
                    item = item,
                    indice = indice,
                    centroX = alvoX,
                    centroY = alvoY,
                    aoEscolher = { radialAberto = false; item.aoEscolher() }
                )
            }
        }

        if (barraAberta && !radialAberto) {
            BarraCompacta(
                acoes = acoesRapidas,
                naEsquerda = naEsquerda,
                bolhaX = x,
                bolhaY = y,
                diametro = diametro,
                larguraTela = tamanho.width
            )
        }

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(56.dp)
                .clip(PcFlow.formas.pilula)
                .background(cores.superficie.copy(alpha = 0.94f))
                .border(
                    DimensaoPcFlow.borda,
                    if (radialAberto || barraAberta) cores.acao else cores.contornar(cores.acao, 0.45f),
                    PcFlow.formas.pilula
                )
                .pointerInput(tamanho) {
                    awaitEachGesture {
                        val descida = awaitFirstDown()
                        val instante = System.currentTimeMillis()
                        var arrastou = false
                        var abriuRadial = false
                        while (true) {
                            val restante = instante + 420L - System.currentTimeMillis()
                            val evento =
                                if (!arrastou && !abriuRadial)
                                    withTimeoutOrNull(restante.coerceAtLeast(1L)) { awaitPointerEvent() }
                                else awaitPointerEvent()

                            if (evento == null) {
                                abriuRadial = true
                                radialAberto = true
                                barraAberta = false
                                continue
                            }
                            val mudanca = evento.changes.firstOrNull { it.id == descida.id } ?: break
                            if (!mudanca.pressed) break
                            val delta = mudanca.positionChange()
                            if (!abriuRadial) {
                                if (!arrastou && delta.getDistance() > toleranciaArraste) arrastou = true
                                if (arrastou) {
                                    x = (x + delta.x).coerceIn(0f, (tamanho.width - diametro).coerceAtLeast(0f))
                                    y = (y + delta.y).coerceIn(0f, (tamanho.height - diametro).coerceAtLeast(0f))
                                    mudanca.consume()
                                }
                            }
                        }
                        when {
                            arrastou -> encaixar()
                            abriuRadial -> Unit
                            else -> barraAberta = !barraAberta
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            IconePcFlow(
                icone = if (radialAberto) Icone.FECHAR else Icone.AJUSTES,
                cor = cores.acao,
                tamanho = 24.dp,
                descricao = "Menu da sessão"
            )
        }
    }
}

@Composable
private fun ItemDoRadial(
    item: ItemRadial,
    indice: Int,
    centroX: Float,
    centroY: Float,
    aoEscolher: () -> Unit
) {
    val cores = PcFlow.cores
    val densidade = LocalDensity.current
    val largura = with(densidade) { 72.dp.toPx() }
    val altura = with(densidade) { 76.dp.toPx() }

    // Cada item entra com um atraso pequeno: dá a sensação de o menu abrir em
    // leque, dentro dos 180–260 ms do sistema visual.
    var entrou by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrou = true }
    val escala by animateFloatAsState(
        targetValue = if (entrou) 1f else 0.6f,
        animationSpec = tween(
            durationMillis = duracaoPcFlow(DURACAO_PAINEL_MS),
            delayMillis = duracaoPcFlow(indice * 12)
        ),
        label = "escalaRadial"
    )
    val opacidade by animateFloatAsState(
        targetValue = if (entrou) 1f else 0f,
        animationSpec = tween(durationMillis = duracaoPcFlow(DURACAO_PAINEL_MS)),
        label = "opacidadeRadial"
    )
    val tom = item.cor ?: cores.acao

    Column(
        Modifier
            .offset { IntOffset((centroX - largura / 2f).roundToInt(), (centroY - altura / 2f).roundToInt()) }
            .width(72.dp)
            .scale(escala)
            .alpha(opacidade),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(DimensaoPcFlow.alvoMinimo)
                .clip(PcFlow.formas.pilula)
                .background(cores.superficieElevada)
                .border(DimensaoPcFlow.borda, cores.contornar(tom, 0.5f), PcFlow.formas.pilula)
                .clickable(onClick = aoEscolher),
            contentAlignment = Alignment.Center
        ) {
            IconePcFlow(item.icone, tom, tamanho = 22.dp, descricao = item.rotulo)
        }
        Text(
            item.rotulo,
            color = cores.texto,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = EspacoPcFlow.x4)
        )
    }
}

/** Barra curta que sai da bolha com o que se usa o tempo todo. */
@Composable
private fun BarraCompacta(
    acoes: List<ItemRadial>,
    naEsquerda: Boolean,
    bolhaX: Float,
    bolhaY: Float,
    diametro: Float,
    larguraTela: Int
) {
    val cores = PcFlow.cores
    val densidade = LocalDensity.current
    val folga = with(densidade) { 8.dp.toPx() }
    val larguraBarra = with(densidade) { (acoes.size * 48 + 16).dp.toPx() }
    val deslocX = if (naEsquerda) bolhaX + diametro + folga
    else (bolhaX - larguraBarra - folga).coerceAtLeast(folga)

    Row(
        Modifier
            .offset {
                IntOffset(
                    deslocX.coerceIn(0f, (larguraTela - larguraBarra).coerceAtLeast(0f)).roundToInt(),
                    (bolhaY + diametro / 2f - with(densidade) { 24.dp.toPx() }).roundToInt()
                )
            }
            .clip(PcFlow.formas.pilula)
            .background(cores.superficie.copy(alpha = 0.94f))
            .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.pilula)
            .padding(horizontal = EspacoPcFlow.x8),
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (acao in acoes) {
            BotaoSessao(
                icone = acao.icone,
                descricao = acao.rotulo,
                cor = acao.cor,
                aoTocar = acao.aoEscolher
            )
        }
    }
}
