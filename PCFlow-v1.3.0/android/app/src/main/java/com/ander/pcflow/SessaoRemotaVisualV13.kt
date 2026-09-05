package com.ander.pcflow

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SRFundo = Color(0xFF05070A)
private val SRPainel = Color(0xE9151B22)
private val SRBorda = Color(0xFF303A45)
private val SROuro = Color(0xFFF3B13F)
private val SRTurquesa = Color(0xFF24D2C2)
private val SRTexto2 = Color(0xFF9EABB8)
private val SRPerigo = Color(0xFFFF716A)

private enum class ModoSessaoVisualV13(val titulo: String) { TOQUE("Toque"), TOUCHPAD("Touchpad"), VER("Ver") }
private enum class DialogoSessaoV13 { NENHUM, TECLADO, MONITOR, FERRAMENTAS, ACOES, CHAT, CLIPBOARD, ARQUIVOS, ENCERRAR }

@Composable
fun PcFlowEntradaFinalV13() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    if (estado.estado == EstadoConexao.CONECTADO) SessaoRemotaVisualV13(estado)
    else PcFlowV13CompletoApp()
}

@Composable
fun SessaoRemotaVisualV13(estado: EstadoSessao) {
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    val clipboard by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    var modo by remember { mutableStateOf(ModoSessaoVisualV13.TOQUE) }
    var menuAberto by remember { mutableStateOf(false) }
    var dialogo by remember { mutableStateOf(DialogoSessaoV13.NENHUM) }
    val pc = estado.pc ?: return

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            TelaRemotaInterativaV13(quadro, monitor, estado, modo)

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                color = Color(0xE911171E),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0x66404D59))
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(SRTurquesa, CircleShape))
                    Column(Modifier.padding(start = 7.dp)) {
                        Text(pc.nome, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("Conectado · Monitor ${monitor + 1}/${estado.quantidadeMonitores.coerceAtLeast(1)}", color = SRTexto2, fontSize = 8.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Alta qualidade", color = SRTurquesa, fontSize = 8.sp)
                }
            }

            MenuCurvoV13(
                aberto = menuAberto,
                alternar = { menuAberto = !menuAberto },
                abrir = { alvo -> dialogo = alvo; menuAberto = false }
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
                color = SRPainel,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SRBorda)
            ) {
                Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ChipModoSessaoV13("☝  Toque", modo == ModoSessaoVisualV13.TOQUE) { modo = ModoSessaoVisualV13.TOQUE }
                    ChipModoSessaoV13("▣  Touchpad", modo == ModoSessaoVisualV13.TOUCHPAD) { modo = ModoSessaoVisualV13.TOUCHPAD }
                    ChipModoSessaoV13("◉  Ver", modo == ModoSessaoVisualV13.VER) { modo = ModoSessaoVisualV13.VER }
                }
            }
        }
    }

    when (dialogo) {
        DialogoSessaoV13.TECLADO -> DialogoTecladoSessaoV13 { dialogo = DialogoSessaoV13.NENHUM }
        DialogoSessaoV13.MONITOR -> DialogoMonitoresSessaoV13(estado.quantidadeMonitores, monitor, { dialogo = DialogoSessaoV13.NENHUM }) {
            SessaoPcFlow.alterarMonitor(it); dialogo = DialogoSessaoV13.NENHUM
        }
        DialogoSessaoV13.FERRAMENTAS -> DialogoFerramentasSessaoV13(
            fechar = { dialogo = DialogoSessaoV13.NENHUM },
            arquivos = { dialogo = DialogoSessaoV13.ARQUIVOS },
            clipboard = { dialogo = DialogoSessaoV13.CLIPBOARD }
        )
        DialogoSessaoV13.ACOES -> DialogoAcoesSessaoV13 { dialogo = DialogoSessaoV13.NENHUM }
        DialogoSessaoV13.CHAT -> DialogoChatSessaoV13(pc.nome) { dialogo = DialogoSessaoV13.NENHUM }
        DialogoSessaoV13.CLIPBOARD -> DialogoClipboardSessaoV13(clipboard) { dialogo = DialogoSessaoV13.NENHUM }
        DialogoSessaoV13.ARQUIVOS -> DialogoArquivosRemotos { dialogo = DialogoSessaoV13.NENHUM }
        DialogoSessaoV13.ENCERRAR -> AlertDialog(
            onDismissRequest = { dialogo = DialogoSessaoV13.NENHUM },
            containerColor = Color(0xFF151B22),
            title = { Text("Encerrar sessão?") },
            text = { Text("A conexão com ${pc.nome} será encerrada.", color = SRTexto2) },
            confirmButton = { Button(onClick = { SessaoPcFlow.desconectar(); dialogo = DialogoSessaoV13.NENHUM }, colors = ButtonDefaults.buttonColors(containerColor = SRPerigo)) { Text("Encerrar") } },
            dismissButton = { TextButton(onClick = { dialogo = DialogoSessaoV13.NENHUM }) { Text("Cancelar") } }
        )
        else -> Unit
    }
}

@Composable
private fun TelaRemotaInterativaV13(bitmap: Bitmap?, monitor: Int, estado: EstadoSessao, modo: ModoSessaoVisualV13) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var ponto by remember { mutableStateOf<Offset?>(null) }
    var pulso by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pulso) {
        if (pulso > 0) {
            alpha.snapTo(1f)
            alpha.animateTo(0f, tween(380))
        }
    }
    LaunchedEffect(monitor) { zoom = 1f; pan = Offset.Zero; ponto = null }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { area = it }
            .pointerInput(bitmap, area, monitor, modo, estado.permissoes.entrada) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoSessaoVisualV13.VER) return@pointerInput
                detectTapGestures(
                    onPress = { pos -> ponto = pos; pulso++ },
                    onTap = { pos ->
                        if (modo == ModoSessaoVisualV13.TOQUE) moverAbsSessaoV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueSessaoV13("left")
                    },
                    onDoubleTap = { pos ->
                        if (modo == ModoSessaoVisualV13.TOQUE) moverAbsSessaoV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueSessaoV13("left"); cliqueSessaoV13("left")
                    },
                    onLongPress = { pos ->
                        if (modo == ModoSessaoVisualV13.TOQUE) moverAbsSessaoV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueSessaoV13("right")
                    }
                )
            }
            .pointerInput(bitmap, area, monitor, modo, estado.permissoes.entrada) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoSessaoVisualV13.VER) return@pointerInput
                if (modo == ModoSessaoVisualV13.TOQUE) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            ponto = pos; pulso++
                            if (moverAbsSessaoV13(pos, area, bitmap.width, bitmap.height, monitor)) SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                        },
                        onDragEnd = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; ponto = null },
                        onDragCancel = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; ponto = null },
                        onDrag = { change, _ -> change.consume(); ponto = change.position; moverAbsSessaoV13(change.position, area, bitmap.width, bitmap.height, monitor) }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { pos -> ponto = pos; pulso++ },
                        onDragEnd = { ponto = null },
                        onDragCancel = { ponto = null },
                        onDrag = { change, delta ->
                            change.consume(); ponto = change.position
                            SessaoPcFlow.enviar("mouse_move") { put("x", delta.x * 1.15); put("y", delta.y * 1.15) }
                        }
                    )
                }
            }
            .pointerInput(bitmap, modo) {
                if (bitmap == null || modo != ModoSessaoVisualV13.VER) return@pointerInput
                detectTransformGestures { _, deslocamento, escala, _ ->
                    zoom = (zoom * escala).coerceIn(1f, 4f)
                    pan += deslocamento
                    if (zoom <= 1.01f) pan = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SROuro)
                Text("Recebendo tela…", color = SRTexto2, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Image(bitmap.asImageBitmap(), "Tela remota", Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
        }

        ponto?.let { p ->
            if (alpha.value > 0f) Canvas(Modifier.fillMaxSize()) {
                drawCircle(SRTurquesa.copy(alpha = alpha.value * .24f), 30.dp.toPx(), p)
                drawCircle(SRTurquesa.copy(alpha = alpha.value), 10.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
        }

        if (!estado.permissoes.entrada) {
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = Color(0xE52A1719), shape = RoundedCornerShape(18.dp)) {
                Text("O computador desativou o controle de entrada. A visualização continua disponível.", color = SRPerigo, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun MenuCurvoV13(aberto: Boolean, alternar: () -> Unit, abrir: (DialogoSessaoV13) -> Unit) {
    val rotacao by animateFloatAsState(if (aberto) 45f else 0f, label = "rotacaoMenuCurvo")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = aberto,
            enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = .82f),
            exit = fadeOut(tween(130)) + scaleOut(tween(170), targetScale = .88f),
            modifier = Modifier.padding(end = 14.dp)
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SegmentoCurvoV13("🔧", "Ferramentas", 36, false) { abrir(DialogoSessaoV13.FERRAMENTAS) }
                SegmentoCurvoV13("⌨", "Teclado", 20, false) { abrir(DialogoSessaoV13.TECLADO) }
                SegmentoCurvoV13("▣", "Tela", 10, false) { abrir(DialogoSessaoV13.MONITOR) }
                SegmentoCurvoV13("➤", "Ações", 4, false) { abrir(DialogoSessaoV13.ACOES) }
                SegmentoCurvoV13("▤", "Chat", 10, false) { abrir(DialogoSessaoV13.CHAT) }
                SegmentoCurvoV13("•••", "Mais", 20, false) { abrir(DialogoSessaoV13.ARQUIVOS) }
                SegmentoCurvoV13("×", "Encerrar", 36, true) { abrir(DialogoSessaoV13.ENCERRAR) }
            }
        }

        Surface(
            modifier = Modifier.padding(end = 10.dp).size(58.dp).clickable(onClick = alternar),
            color = if (aberto) SROuro else Color(0xF01A222B),
            shape = CircleShape,
            border = BorderStroke(1.dp, if (aberto) SROuro else SRBorda)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("◆", color = if (aberto) Color.Black else SROuro, fontSize = 22.sp, modifier = Modifier.graphicsLayer(rotationZ = rotacao))
            }
        }
    }
}

@Composable
private fun SegmentoCurvoV13(simbolo: String, texto: String, recuo: Int, perigo: Boolean, click: () -> Unit) {
    Surface(
        modifier = Modifier.padding(end = recuo.dp).width(132.dp).height(48.dp).clickable(onClick = click),
        color = if (perigo) Color(0xF0D7553D) else Color(0xEE2393B8),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(simbolo, color = Color.White, fontSize = 17.sp)
            Text(texto, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ChipModoSessaoV13(texto: String, ativo: Boolean, click: () -> Unit) {
    Surface(Modifier.clickable(onClick = click), color = if (ativo) SROuro else Color.Transparent, shape = RoundedCornerShape(18.dp)) {
        Text(texto, color = if (ativo) Color.Black else Color.White, fontSize = 9.sp, fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
    }
}

@Composable
private fun DialogoTecladoSessaoV13(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    Dialog(onDismissRequest = fechar) {
        Surface(color = Color(0xFF151B22), shape = RoundedCornerShape(23.dp), border = BorderStroke(1.dp, SRBorda)) {
            Column(Modifier.padding(17.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Teclado remoto", fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = fechar) { Text("Fechar") } }
                OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Digite aqui") })
                Button(onClick = { if (texto.isNotEmpty()) SessaoPcFlow.enviar("texto") { put("texto", texto) }; texto = "" }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Enviar texto") }
                listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT", "HOME", "END", "F5", "ALT_TAB", "SHOW_DESKTOP", "WIN_E", "WIN_R").chunked(4).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        linha.forEach { tecla -> OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp)) { Text(tecla.replace("_", " "), fontSize = 7.sp) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoMonitoresSessaoV13(total: Int, atual: Int, fechar: () -> Unit, selecionar: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = Color(0xFF151B22),
        title = { Text("Monitores") },
        text = {
            Column { repeat(total.coerceAtLeast(1)) { i ->
                Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selecionar(i) }, color = if (i == atual) Color(0x3324D2C2) else Color(0xFF0E1319), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (i == atual) SRTurquesa else SRBorda)) {
                    Text("▣  Monitor ${i + 1}${if (i == 0) " (Principal)" else ""}", modifier = Modifier.padding(12.dp), color = if (i == atual) SRTurquesa else Color.White)
                }
            } }
        },
        confirmButton = { TextButton(onClick = fechar) { Text("Fechar") } }
    )
}

@Composable
private fun DialogoFerramentasSessaoV13(fechar: () -> Unit, arquivos: () -> Unit, clipboard: () -> Unit) {
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = Color(0xFF151B22),
        title = { Text("Ferramentas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BotaoFerramentaV13("📁 Arquivos") { arquivos() }
                BotaoFerramentaV13("▤ Área de transferência") { clipboard() }
                BotaoFerramentaV13("▣ Mostrar área de trabalho") { SessaoPcFlow.enviar("tecla") { put("tecla", "SHOW_DESKTOP") }; fechar() }
                BotaoFerramentaV13("🔒 Bloquear PC") { SessaoPcFlow.enviar("power") { put("acao", "lock") }; fechar() }
                BotaoFerramentaV13("⏻ Desligar monitor") { SessaoPcFlow.enviar("power") { put("acao", "monitoroff") }; fechar() }
                BotaoFerramentaV13("▶ Mídia: Play/Pause") { SessaoPcFlow.enviar("media") { put("acao", "playpause") }; fechar() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(onClick = { SessaoPcFlow.enviar("media") { put("acao", "volumedown") } }, modifier = Modifier.weight(1f)) { Text("Vol −") }
                    OutlinedButton(onClick = { SessaoPcFlow.enviar("media") { put("acao", "volumeup") } }, modifier = Modifier.weight(1f)) { Text("Vol +") }
                }
            }
        },
        confirmButton = { TextButton(onClick = fechar) { Text("Fechar") } }
    )
}

@Composable
private fun BotaoFerramentaV13(texto: String, click: () -> Unit) {
    OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, SRBorda)) { Text(texto, fontSize = 10.sp) }
}

@Composable
private fun DialogoAcoesSessaoV13(fechar: () -> Unit) {
    val acoes = listOf(
        "Nova guia" to "NEW_TAB", "Fechar guia" to "CLOSE_TAB", "Reabrir guia" to "REOPEN_TAB",
        "Voltar navegador" to "BROWSER_BACK", "Avançar navegador" to "BROWSER_FORWARD", "Atualizar navegador" to "BROWSER_REFRESH",
        "Alt + Tab" to "ALT_TAB", "Explorador" to "WIN_E", "Executar" to "WIN_R", "Gerenciador de tarefas" to "TASK_MANAGER"
    )
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = Color(0xFF151B22),
        title = { Text("Ações rápidas") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { acoes.forEach { (nome, tecla) -> BotaoFerramentaV13(nome) { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } } } } },
        confirmButton = { TextButton(onClick = fechar) { Text("Fechar") } }
    )
}

@Composable
private fun DialogoChatSessaoV13(nomePc: String, fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = Color(0xFF151B22),
        title = { Text("Chat com $nomePc") },
        text = { Column { Text("Envie uma mensagem que aparecerá no PC conectado.", color = SRTexto2, fontSize = 10.sp); OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Mensagem") }, minLines = 2) } },
        confirmButton = { Button(onClick = { SessaoPcFlow.enviar("chat") { put("texto", texto) }; texto = "" }, enabled = texto.isNotBlank()) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Fechar") } }
    )
}

@Composable
private fun DialogoClipboardSessaoV13(remoto: String, fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { SessaoPcFlow.solicitarClipboard() }
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = Color(0xFF151B22),
        title = { Text("Área de transferência") },
        text = {
            Column {
                Text("No PC", color = SRTexto2, fontSize = 9.sp)
                Surface(Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 130.dp), color = Color(0xFF0E1319), shape = RoundedCornerShape(12.dp)) { Text(remoto.ifBlank { "Nenhum texto recebido." }, modifier = Modifier.padding(10.dp), fontSize = 10.sp) }
                OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Enviar para o PC") })
            }
        },
        confirmButton = { Button(onClick = { SessaoPcFlow.enviarClipboard(texto) }, enabled = texto.isNotBlank()) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = { SessaoPcFlow.solicitarClipboard() }) { Text("Atualizar") } }
    )
}

private fun cliqueSessaoV13(botao: String) = SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }

private fun moverAbsSessaoV13(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int, monitor: Int): Boolean {
    val coordenada = mapearSessaoV13(pos, area, larguraImagem, alturaImagem) ?: return false
    SessaoPcFlow.enviar("mouse_abs") { put("x", coordenada.first); put("y", coordenada.second); put("monitor", monitor) }
    return true
}

private fun mapearSessaoV13(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int): Pair<Double, Double>? {
    if (area.width <= 0 || area.height <= 0 || larguraImagem <= 0 || alturaImagem <= 0) return null
    val escala = minOf(area.width.toFloat() / larguraImagem, area.height.toFloat() / alturaImagem)
    val larguraRender = larguraImagem * escala
    val alturaRender = alturaImagem * escala
    val esquerda = (area.width - larguraRender) / 2f
    val topo = (area.height - alturaRender) / 2f
    if (pos.x < esquerda || pos.x > esquerda + larguraRender || pos.y < topo || pos.y > topo + alturaRender) return null
    return ((pos.x - esquerda) / larguraRender).coerceIn(0f, 1f).toDouble() to ((pos.y - topo) / alturaRender).coerceIn(0f, 1f).toDouble()
}
