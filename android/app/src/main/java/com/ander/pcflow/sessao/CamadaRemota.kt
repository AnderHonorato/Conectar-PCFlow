package com.ander.pcflow.sessao

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.IconePcFlow
import com.ander.pcflow.design.LocalAnimacoesReduzidasPcFlow
import com.ander.pcflow.design.PcFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Prazo máximo do sinal de toque depois que o dedo sai, como manda o contrato. */
private const val SUMICO_TOQUE_MS = 450L

/**
 * A tela do PC e tudo que se faz com o dedo em cima dela.
 *
 * A regra de cada gesto mora em `MaquinaGesto`, e a conta de coordenada em
 * `MapeamentoTela`. Aqui só acontece a ligação entre o dedo, essas duas peças e
 * a sessão — é o que permite testar gesto e mapeamento sem aparelho.
 */
@Composable
fun CamadaRemota(
    quadro: Bitmap?,
    monitor: Int,
    permitirEntrada: Boolean,
    maquina: MaquinaGesto,
    visual: Visualizacao,
    aoMudarVisual: (Visualizacao) -> Unit,
    chaveLimpeza: Any?,
    mensagemSemImagem: String,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    val densidade = LocalDensity.current
    val reduzirAnimacoes = LocalAnimacoesReduzidasPcFlow.current

    var area by remember { mutableStateOf(IntSize.Zero) }
    val quadroAtual by rememberUpdatedState(quadro)
    val monitorAtual by rememberUpdatedState(monitor)
    val visualAtual by rememberUpdatedState(visual)
    val mudarVisual by rememberUpdatedState(aoMudarVisual)

    val config = remember(densidade) {
        with(densidade) {
            ConfigGesto(
                folgaToque = 10.dp.toPx(),
                tempoPressaoLongaMs = 420L,
                tempoDuploToqueMs = 300L,
                raioDuploToque = 40.dp.toPx()
            )
        }
    }
    val limiarEixo = remember(densidade) { with(densidade) { 12.dp.toPx() } }
    val pixelsPorEntalhe = remember(densidade) { with(densidade) { 16.dp.toPx() } }

    val ciclo = remember(reduzirAnimacoes) {
        CicloFeedbackToque(if (reduzirAnimacoes) 0L else SUMICO_TOQUE_MS)
    }
    var marca by remember { mutableStateOf<MarcaToque?>(null) }
    var relogio by remember { mutableLongStateOf(0L) }

    fun sincronizarMarca() { marca = ciclo.marca }

    fun aplicar(comandos: List<ComandoEntrada>) {
        if (comandos.isEmpty()) return
        val bitmap = quadroAtual
        for (comando in comandos) {
            when (comando) {
                is ComandoEntrada.PosicionarEm -> {
                    if (bitmap != null) {
                        val ponto = mapearToqueParaMonitor(
                            comando.x, comando.y,
                            area.width, area.height,
                            bitmap.width, bitmap.height,
                            visualAtual
                        )
                        // Fora da imagem é barra preta: ali não existe pixel do
                        // PC, então mover o ponteiro seria inventar posição.
                        if (ponto != null) SessaoPcFlow.posicionar(ponto.x, ponto.y, monitorAtual)
                    }
                }
                is ComandoEntrada.Mover -> SessaoPcFlow.mover(comando.dx.toDouble(), comando.dy.toDouble())
                is ComandoEntrada.Clicar -> SessaoPcFlow.clicar(comando.botao.nome, comando.cliques)
                is ComandoEntrada.Pressionar -> SessaoPcFlow.pressionar(comando.botao.nome)
                is ComandoEntrada.Soltar -> SessaoPcFlow.soltar(comando.botao.nome)
                is ComandoEntrada.Rolar -> SessaoPcFlow.rolar(comando.delta, comando.horizontal)
            }
        }
    }

    // Girar a tela, trocar de monitor ou perder a sessão apaga o sinal de toque
    // e devolve qualquer botão preso: nada de marca parada no meio da tela.
    LaunchedEffect(chaveLimpeza) {
        aplicar(maquina.cancelar())
        ciclo.limpar()
        sincronizarMarca()
    }

    DisposableEffect(Unit) {
        onDispose { aplicar(maquina.encerrar()) }
    }

    // O sumiço tem que acontecer sozinho, sem depender de novo toque.
    LaunchedEffect(marca?.instanteSolta ?: -1L) {
        val atual = marca ?: return@LaunchedEffect
        if (atual.pressionada) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            val agora = System.currentTimeMillis()
            relogio = agora
            ciclo.envelhecer(agora)
            if (ciclo.marca == null) { marca = null; break }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(cores.fundo)
            .onSizeChanged { area = it }
            .pointerInput(permitirEntrada) {
                if (!permitirEntrada) return@pointerInput
                awaitEachGesture {
                    val primeiro = awaitFirstDown(requireUnconsumed = false)
                    val instanteDescida = System.currentTimeMillis()
                    var terminouComUp = false
                    var multitoque = false
                    var longaDisparada = false
                    var dedosAnteriores = 1
                    var centroAnterior = primeiro.position
                    var distanciaAnterior = 0f
                    var visualLocal = visualAtual
                    val decisor = DecisorEixo(limiar = limiarEixo)
                    val rolagemVertical = AcumuladorRolagem(pixelsPorEntalhe)
                    val rolagemHorizontal = AcumuladorRolagem(pixelsPorEntalhe)

                    try {
                        maquina.escalaMovimento = 1f / visualLocal.zoom
                        aplicar(maquina.descer(primeiro.position.x, primeiro.position.y, instanteDescida))
                        ciclo.tocar(primeiro.position.x, primeiro.position.y)
                        sincronizarMarca()

                        while (true) {
                            val esperandoLonga = !longaDisparada && maquina.esperandoPressaoLonga
                            val evento = if (esperandoLonga) {
                                val restante = instanteDescida + config.tempoPressaoLongaMs - System.currentTimeMillis()
                                withTimeoutOrNull(restante.coerceAtLeast(1L)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }

                            if (evento == null) {
                                longaDisparada = true
                                aplicar(maquina.pressaoLonga(System.currentTimeMillis()))
                                continue
                            }

                            val ativos = evento.changes.filter { it.pressed }
                            if (ativos.isEmpty()) break

                            if (ativos.size >= 2) {
                                if (!multitoque) {
                                    // Dois dedos nunca clicam: o gesto de um dedo acaba aqui.
                                    aplicar(maquina.entrarMultitoque())
                                    ciclo.limpar()
                                    sincronizarMarca()
                                    multitoque = true
                                }
                                val centro = ativos.fold(Offset.Zero) { soma, ponto -> soma + ponto.position } /
                                    ativos.size.toFloat()
                                val distancia = (ativos[0].position - ativos[1].position).getDistance()
                                if (dedosAnteriores != ativos.size) {
                                    centroAnterior = centro
                                    distanciaAnterior = distancia
                                    decisor.reiniciar()
                                    rolagemVertical.reiniciar()
                                    rolagemHorizontal.reiniciar()
                                }
                                val deslocamento = centro - centroAnterior
                                centroAnterior = centro

                                val fator =
                                    if (distanciaAnterior > 12f && distancia > 12f) distancia / distanciaAnterior
                                    else 1f
                                distanciaAnterior = distancia

                                var novoVisual = visualLocal
                                if (abs(fator - 1f) > 0.008f) {
                                    novoVisual = aplicarPinca(
                                        novoVisual, fator, centro.x, centro.y, area.width, area.height
                                    )
                                }
                                if (novoVisual.ampliada) {
                                    // Ampliado, dois dedos passeiam pela imagem em vez de rolar.
                                    novoVisual = aplicarDeslocamento(
                                        novoVisual, deslocamento.x, deslocamento.y, area.width, area.height
                                    )
                                } else {
                                    when (decisor.atualizar(deslocamento.x, deslocamento.y)) {
                                        Eixo.VERTICAL -> rolagemVertical.acumular(deslocamento.y)
                                            .takeIf { it != 0 }?.let { SessaoPcFlow.rolar(it, false) }
                                        // A roda horizontal do Windows anda para
                                        // o lado oposto ao dedo, como no touchpad.
                                        Eixo.HORIZONTAL -> rolagemHorizontal.acumular(-deslocamento.x)
                                            .takeIf { it != 0 }?.let { SessaoPcFlow.rolar(it, true) }
                                        Eixo.INDEFINIDO -> Unit
                                    }
                                }
                                if (novoVisual != visualLocal) {
                                    visualLocal = novoVisual
                                    maquina.escalaMovimento = 1f / visualLocal.zoom
                                    mudarVisual(novoVisual)
                                }
                            } else if (!multitoque) {
                                val ponto = ativos[0].position
                                aplicar(maquina.mover(ponto.x, ponto.y, System.currentTimeMillis()))
                                ciclo.arrastar(ponto.x, ponto.y)
                                sincronizarMarca()
                            }

                            dedosAnteriores = ativos.size
                            evento.changes.forEach { it.consume() }
                        }

                        if (multitoque) maquina.sairMultitoque()
                        else aplicar(maquina.subir(System.currentTimeMillis()))
                        terminouComUp = true
                    } finally {
                        // Gesto interrompido pelo sistema (notificação, gesto de
                        // navegação) também precisa devolver o botão pressionado.
                        if (!terminouComUp) aplicar(maquina.cancelar())
                        ciclo.soltar(System.currentTimeMillis())
                        sincronizarMarca()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = quadroAtual
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Tela do computador",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = visual.zoom,
                        scaleY = visual.zoom,
                        translationX = visual.deslocX,
                        translationY = visual.deslocY
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(EspacoPcFlow.x24)
            ) {
                IconePcFlow(Icone.COMPUTADOR, cores.textoSecundario, tamanho = 40.dp)
                Text(
                    mensagemSemImagem,
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = EspacoPcFlow.x12)
                )
            }
        }

        val atual = marca
        if (atual != null) {
            val opacidade = ciclo.opacidade(if (atual.pressionada) relogio else maxOf(relogio, atual.instanteSolta))
            if (opacidade > 0f) {
                val raioBase = with(densidade) { 22.dp.toPx() }
                Canvas(Modifier.fillMaxSize()) {
                    val centro = Offset(atual.x, atual.y)
                    val crescimento = 1f + (1f - opacidade) * 0.7f
                    drawCircle(
                        color = cores.acao.copy(alpha = 0.16f * opacidade),
                        radius = raioBase * crescimento,
                        center = centro
                    )
                    drawCircle(
                        color = cores.acao.copy(alpha = 0.85f * opacidade),
                        radius = raioBase * crescimento,
                        center = centro,
                        style = Stroke(width = raioBase * 0.10f)
                    )
                }
            }
        }
    }
}
