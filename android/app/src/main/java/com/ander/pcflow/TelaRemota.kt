package com.ander.pcflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val FundoRemoto = Color(0xFF05070A)
private val PainelRemoto = Color(0xE6151A22)
private val BordaRemoto = Color(0xFF363D48)
private val OuroRemoto = Color(0xFFF4AD2F)
private val TextoRemoto = Color(0xFF9DA5B0)
private val PerigoRemoto = Color(0xFFFF7A70)

/** Como o dedo controla o ponteiro do PC. */
enum class ModoPonteiro {
    /** O dedo é o ponteiro: onde toca, o cursor vai. Direto e imediato. */
    DIRETO,

    /** A tela vira um trackpad: arrastar move o cursor a partir de onde ele está. */
    TRACKPAD
}

/**
 * Tela remota do PC com controle visual completo.
 *
 * O que existia antes cobria só toque simples, duplo toque e arraste em posição
 * absoluta. Faltava o essencial para usar de verdade num celular:
 *
 *  - zoom com pinça e arraste da imagem quando ampliada (numa tela de 6" o
 *    Windows inteiro fica pequeno demais para acertar um botão);
 *  - modo trackpad, que é como se controla um desktop remoto com precisão —
 *    o ponteiro continua de onde estava em vez de saltar para o dedo;
 *  - rolagem com dois dedos;
 *  - teclado físico do sistema enviando texto e teclas para o PC;
 *  - botões de mouse sempre à mão, inclusive o do meio;
 *  - Ctrl+Alt+Del e Alt+Tab, que não dá para fazer com gesto.
 */
@Composable
fun TelaRemotaPcFlow(estado: EstadoSessao, fechar: () -> Unit) {
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()

    var area by remember { mutableStateOf(IntSize.Zero) }
    var modo by remember { mutableStateOf(ModoPonteiro.DIRETO) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var deslocX by remember { mutableFloatStateOf(0f) }
    var deslocY by remember { mutableFloatStateOf(0f) }
    var mostrarTeclado by remember { mutableStateOf(false) }
    var barraVisivel by remember { mutableStateOf(true) }

    val bitmap = quadro
    val podeControlar = estado.permissoes.entrada
    val modoAtual by rememberUpdatedState(modo)
    val zoomAtual by rememberUpdatedState(zoom)

    // Ao ampliar, mantém o deslocamento dentro de limites que não deixam a
    // imagem "fugir" da tela.
    fun limitarDeslocamento() {
        val limiteX = max(0f, area.width * (zoom - 1f) / 2f)
        val limiteY = max(0f, area.height * (zoom - 1f) / 2f)
        deslocX = deslocX.coerceIn(-limiteX, limiteX)
        deslocY = deslocY.coerceIn(-limiteY, limiteY)
    }

    Box(Modifier.fillMaxSize().background(FundoRemoto)) {

        // ---------------- imagem + gestos ----------------
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { area = it }
                .pointerInput(bitmap != null, podeControlar) {
                    if (bitmap == null || !podeControlar) return@pointerInput
                    awaitEachGesture {
                        val primeiro = awaitFirstDown(requireUnconsumed = false)
                        var maxDedos = 1
                        var moveu = false
                        var arrastando = false
                        var posicaoAnterior = primeiro.position
                        var centroAnterior: Offset? = null
                        var distanciaAnterior = 0f
                        var dedosAnterior = 1
                        var restoScroll = 0f
                        val inicio = System.currentTimeMillis()

                        while (true) {
                            // Timeout serve para detectar o toque longo (arraste) sem sair do laço.
                            val evento = withTimeoutOrNull(
                                if (!arrastando && !moveu) 420L else Long.MAX_VALUE
                            ) { awaitPointerEvent() }

                            if (evento == null) {
                                // Toque parado: segura o botão esquerdo para arrastar janelas/ícones.
                                arrastando = true
                                if (modoAtual == ModoPonteiro.DIRETO) {
                                    mapearParaTela(posicaoAnterior, area, bitmap.width, bitmap.height, zoomAtual, deslocX, deslocY)
                                        ?.let { (x, y) -> SessaoPcFlow.enviarPosicao(x, y, monitor) }
                                }
                                SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                                continue
                            }

                            val ativos: List<PointerInputChange> = evento.changes.filter { it.pressed }
                            if (ativos.isEmpty()) break
                            if (ativos.size > maxDedos) maxDedos = ativos.size

                            if (ativos.size >= 2) {
                                // Dois dedos: pinça = zoom, deslize = rolagem ou pan.
                                val centro = ativos.fold(Offset.Zero) { a, c -> a + c.position } /
                                    ativos.size.toFloat()
                                val distancia = (ativos[0].position - ativos[1].position).getDistance()

                                if (dedosAnterior != ativos.size) {
                                    centroAnterior = centro
                                    distanciaAnterior = distancia
                                }
                                val anterior = centroAnterior ?: centro
                                val delta = centro - anterior
                                centroAnterior = centro
                                moveu = true

                                val fatorPinca =
                                    if (distanciaAnterior > 10f && distancia > 10f) distancia / distanciaAnterior
                                    else 1f
                                distanciaAnterior = distancia

                                if (abs(fatorPinca - 1f) > 0.01f) {
                                    zoom = (zoom * fatorPinca).coerceIn(1f, 4f)
                                    limitarDeslocamento()
                                }

                                if (zoom > 1.02f) {
                                    // Com zoom, dois dedos arrastam a área visível.
                                    deslocX += delta.x
                                    deslocY += delta.y
                                    limitarDeslocamento()
                                } else {
                                    // Sem zoom, dois dedos rolam a janela do PC.
                                    restoScroll += -delta.y * 2.4f
                                    val passos = (restoScroll / 12f).toInt()
                                    if (passos != 0) {
                                        restoScroll -= passos * 12f
                                        SessaoPcFlow.enviar("scroll") { put("delta", passos * 120) }
                                    }
                                }
                            } else {
                                val atual = ativos[0]
                                if (dedosAnterior != 1) posicaoAnterior = atual.position
                                val delta = atual.position - posicaoAnterior
                                posicaoAnterior = atual.position

                                if (abs(delta.x) > 0.01f || abs(delta.y) > 0.01f) {
                                    moveu = true
                                    when (modoAtual) {
                                        ModoPonteiro.DIRETO ->
                                            mapearParaTela(atual.position, area, bitmap.width, bitmap.height, zoomAtual, deslocX, deslocY)
                                                ?.let { (x, y) -> SessaoPcFlow.enviarPosicao(x, y, monitor) }

                                        ModoPonteiro.TRACKPAD -> {
                                            // Movimento relativo: dividido pelo zoom para manter
                                            // a mesma sensação de precisão quando ampliado.
                                            val fator = 1.5f / zoomAtual
                                            SessaoPcFlow.enviar("mouse_move") {
                                                put("x", (delta.x * fator).toDouble())
                                                put("y", (delta.y * fator).toDouble())
                                            }
                                        }
                                    }
                                }
                            }

                            dedosAnterior = ativos.size
                            evento.changes.forEach { it.consume() }
                        }

                        val duracao = System.currentTimeMillis() - inicio
                        if (arrastando) {
                            SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }
                        } else if (!moveu && duracao < 300) {
                            if (modoAtual == ModoPonteiro.DIRETO) {
                                mapearParaTela(posicaoAnterior, area, bitmap.width, bitmap.height, zoomAtual, deslocX, deslocY)
                                    ?.let { (x, y) -> SessaoPcFlow.enviarPosicao(x, y, monitor) }
                            }
                            when (maxDedos) {
                                1 -> SessaoPcFlow.enviar("mouse_click") { put("botao", "left") }
                                2 -> SessaoPcFlow.enviar("mouse_click") { put("botao", "right") }
                                else -> SessaoPcFlow.enviar("mouse_click") { put("botao", "middle") }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Tela do computador",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom, scaleY = zoom,
                            translationX = deslocX, translationY = deslocY
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OuroRemoto)
                    Text(
                        if (podeControlar) "Recebendo a tela do computador…"
                        else "A exibição de tela está desativada no PC.",
                        color = TextoRemoto, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        // ---------------- cabeçalho ----------------
        Surface(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clickable { barraVisivel = !barraVisivel },
            color = PainelRemoto,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append(estado.pc?.nome ?: "PC")
                        if (estado.quantidadeMonitores > 1) append(" · Monitor ${monitor + 1}/${estado.quantidadeMonitores}")
                        if (zoom > 1.02f) append(" · ${"%.1f".format(zoom)}×")
                    },
                    fontSize = 11.sp, color = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (modo == ModoPonteiro.DIRETO) "Toque direto" else "Trackpad",
                    fontSize = 11.sp, color = OuroRemoto, fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ---------------- barra de ferramentas ----------------
        if (barraVisivel) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .background(PainelRemoto, RoundedCornerShape(26.dp))
                    .border(1.dp, BordaRemoto, RoundedCornerShape(26.dp))
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BotaoRemoto(
                    if (modo == ModoPonteiro.DIRETO) "TP" else "TD",
                    "Alternar modo do ponteiro",
                    OuroRemoto
                ) {
                    modo = if (modo == ModoPonteiro.DIRETO) ModoPonteiro.TRACKPAD else ModoPonteiro.DIRETO
                }
                BotaoRemoto("⌨", "Teclado") { mostrarTeclado = !mostrarTeclado }
                BotaoRemoto("L", "Clique esquerdo") { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } }
                BotaoRemoto("R", "Clique direito") { SessaoPcFlow.enviar("mouse_click") { put("botao", "right") } }
                BotaoRemoto("M", "Clique do meio") { SessaoPcFlow.enviar("mouse_click") { put("botao", "middle") } }
                BotaoRemoto("1:1", "Voltar ao tamanho normal") {
                    zoom = 1f; deslocX = 0f; deslocY = 0f
                }
                if (estado.quantidadeMonitores > 1) {
                    BotaoRemoto("⇄", "Trocar de monitor") {
                        SessaoPcFlow.alterarMonitor((monitor + 1) % estado.quantidadeMonitores.coerceAtLeast(1))
                        zoom = 1f; deslocX = 0f; deslocY = 0f
                    }
                }
                BotaoRemoto("✕", "Fechar a tela remota", PerigoRemoto) { fechar() }
            }
        }

        // ---------------- teclado ----------------
        if (mostrarTeclado) {
            TecladoRemoto(
                Modifier.align(Alignment.BottomCenter),
                fechar = { mostrarTeclado = false }
            )
        }
    }
}

@Composable
private fun BotaoRemoto(
    rotulo: String,
    descricao: String,
    cor: Color = Color.White,
    aoTocar: () -> Unit
) {
    Box(
        Modifier.size(46.dp).clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = cor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Teclado do sistema ligado ao PC.
 *
 * O campo é a ponte: cada caractere digitado vira um comando "texto" e o
 * Backspace vira a tecla correspondente, então acentos e "ç" funcionam.
 */
@Composable
private fun TecladoRemoto(modifier: Modifier = Modifier, fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    val tecladoDoSistema = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        foco.requestFocus()
        tecladoDoSistema?.show()
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(PainelRemoto, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .border(1.dp, BordaRemoto, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeclaRapida("Esc", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "ESC") } }
            TeclaRapida("Tab", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "TAB") } }
            TeclaRapida("Enter", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "ENTER") } }
            TeclaRapida("←", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "BACKSPACE") } }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeclaRapida("Alt+Tab", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "ALT_TAB") } }
            TeclaRapida("Win", Modifier.weight(1f)) { SessaoPcFlow.enviar("tecla") { put("tecla", "START_MENU") } }
            TeclaRapida("Ctrl+Alt+Del", Modifier.weight(1.4f)) {
                SessaoPcFlow.enviar("tecla") { put("tecla", "TASK_MANAGER") }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextField(
            value = texto,
            onValueChange = { novo ->
                when {
                    novo.length > texto.length -> {
                        val acrescentado = novo.substring(texto.length)
                        SessaoPcFlow.enviar("texto") { put("texto", acrescentado) }
                    }
                    novo.length < texto.length -> {
                        repeat(texto.length - novo.length) {
                            SessaoPcFlow.enviar("tecla") { put("tecla", "BACKSPACE") }
                        }
                    }
                }
                // Mantém o campo curto para não crescer sem fim durante a digitação.
                texto = if (novo.length > 60) novo.takeLast(30) else novo
            },
            modifier = Modifier.fillMaxWidth().focusRequester(foco),
            placeholder = { Text("Digite aqui — vai direto para o PC", color = TextoRemoto, fontSize = 12.sp) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF11161C),
                unfocusedContainerColor = Color(0xFF11161C),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Fechar teclado",
                color = OuroRemoto, fontSize = 12.sp,
                modifier = Modifier.clickable { fechar() }.padding(8.dp)
            )
        }
    }
}

@Composable
private fun TeclaRapida(rotulo: String, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .background(Color(0xFF1B212A), RoundedCornerShape(10.dp))
            .border(1.dp, BordaRemoto, RoundedCornerShape(10.dp))
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = Color.White, fontSize = 11.sp)
    }
}

/**
 * Converte um ponto do dedo em coordenada normalizada (0..1) da tela do PC,
 * desfazendo o zoom e o deslocamento aplicados na imagem.
 *
 * A imagem usa ContentScale.Fit dentro da área, então existem barras vazias em
 * cima/baixo ou nas laterais: tocar nelas não deve mover o ponteiro.
 */
internal fun mapearParaTela(
    posicao: Offset,
    area: IntSize,
    larguraQuadro: Int,
    alturaQuadro: Int,
    zoom: Float,
    deslocX: Float,
    deslocY: Float
): Pair<Double, Double>? {
    if (area.width <= 0 || area.height <= 0 || larguraQuadro <= 0 || alturaQuadro <= 0) return null

    // 1. Desfaz a transformação visual (escala em torno do centro + translação).
    val centroX = area.width / 2f
    val centroY = area.height / 2f
    val x = (posicao.x - centroX - deslocX) / zoom + centroX
    val y = (posicao.y - centroY - deslocY) / zoom + centroY

    // 2. Desconta as barras do ContentScale.Fit: a imagem é centralizada e não
    //    ocupa a área toda, então tocar na barra não pode mover o ponteiro.
    val escala = min(
        area.width.toFloat() / larguraQuadro,
        area.height.toFloat() / alturaQuadro
    )
    val larguraVisivel = larguraQuadro * escala
    val alturaVisivel = alturaQuadro * escala
    val margemX = (area.width - larguraVisivel) / 2f
    val margemY = (area.height - alturaVisivel) / 2f

    if (x < margemX || x > margemX + larguraVisivel) return null
    if (y < margemY || y > margemY + alturaVisivel) return null

    return ((x - margemX) / larguraVisivel).coerceIn(0f, 1f).toDouble() to
        ((y - margemY) / alturaVisivel).coerceIn(0f, 1f).toDouble()
}
