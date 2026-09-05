package com.ander.pcflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val FundoV13 = Color(0xFF080B0F)
private val Fundo2V13 = Color(0xFF0E1319)
private val PainelV13 = Color(0xFF151B22)
private val BordaV13 = Color(0xFF303A45)
private val OuroV13 = Color(0xFFF3B13F)
private val TurquesaV13 = Color(0xFF24D2C2)
private val Texto2V13 = Color(0xFF9EABB8)
private val PerigoV13 = Color(0xFFFF716A)

private enum class ModoEntradaV13(val titulo: String) {
    TOQUE("Toque"), TOUCHPAD("Touchpad"), VISUALIZAR("Visualizar")
}

@Composable
fun PcFlowV13App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = FundoV13,
            surface = PainelV13,
            primary = OuroV13,
            secondary = TurquesaV13,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        RaizV13()
    }
}

@Composable
private fun RaizV13() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var pcSelecionado by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var painelCompleto by remember { mutableStateOf(false) }

    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        resultado.contents?.let { texto ->
            lerQrV13(texto)?.let { (pc, codigo) ->
                pcSelecionado = pc
                pin = codigo.orEmpty()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    if (painelCompleto) {
        Box(Modifier.fillMaxSize()) {
            PcFlowApp()
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clickable { painelCompleto = false },
                color = Color(0xE6151B22),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BordaV13)
            ) {
                Text("Voltar à V1.3", color = OuroV13, fontSize = 11.sp, modifier = Modifier.padding(13.dp, 9.dp))
            }
        }
    } else if (estado.estado == EstadoConexao.CONECTADO) {
        SessaoV13(estado, abrirPainel = { painelCompleto = true })
    } else {
        HomeV13(
            pcs = pcs,
            estado = estado,
            atualizar = { SessaoPcFlow.descobrir() },
            selecionar = { pcSelecionado = it; pin = ""; senha = "" },
            escanear = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Aponte para o QR Code do PCFlow")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            },
            abrirPainel = { painelCompleto = true }
        )
    }

    pcSelecionado?.let { pc ->
        ConectarDialogV13(
            pc = pc,
            pin = pin,
            senha = senha,
            onPin = { pin = it.filter(Char::isDigit).take(6) },
            onSenha = { senha = it },
            fechar = { pcSelecionado = null },
            conectar = {
                SessaoPcFlow.conectar(pc, pin.ifBlank { null }, senha.ifBlank { null })
                pcSelecionado = null
            }
        )
    }
}

@Composable
private fun HomeV13(
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    atualizar: () -> Unit,
    selecionar: (PcEncontrado) -> Unit,
    escanear: () -> Unit,
    abrirPainel: () -> Unit
) {
    var id by remember { mutableStateOf("") }
    val idLimpo = id.filter(Char::isDigit)
    val encontrados = if (idLimpo.isBlank()) pcs else pcs.filter { it.maquinaId.contains(idLimpo) }
    val exato = pcs.firstOrNull { it.maquinaId == idLimpo }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarcaV13()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("PCFlow", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Controle remoto profissional", color = Texto2V13, fontSize = 11.sp)
            }
            Text("V1.3", color = TurquesaV13, fontSize = 11.sp)
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).animateContentSize(),
            color = PainelV13,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, BordaV13)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Conectar ao computador", color = Texto2V13, fontSize = 12.sp)
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("000 000 000") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exato?.let(selecionar) }, enabled = exato != null, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Conectar")
                    }
                    OutlinedButton(onClick = escanear, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BordaV13)) {
                        Text("Escanear QR", color = OuroV13)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 19.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Computadores na rede", fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = atualizar) { Text("Atualizar", color = OuroV13) }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (encontrados.isEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), color = Fundo2V13, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, BordaV13)) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (idLimpo.isBlank()) "Procurando computadores PCFlow…" else "Nenhum computador com esse ID apareceu na rede.", textAlign = TextAlign.Center)
                            Text("Deixe o PCFlow aberto ou na bandeja e confirme que os aparelhos estão na mesma rede.", color = Texto2V13, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            } else {
                items(encontrados, key = { it.maquinaId.ifBlank { it.host } }) { pc ->
                    PcCardV13(pc) { selecionar(pc) }
                }
            }
        }

        AnimatedVisibility(visible = estado.estado == EstadoConexao.CONECTANDO, enter = fadeIn(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF17241F), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TurquesaV13)
                    Text(estado.mensagem.ifBlank { "Conectando…" }, color = TurquesaV13, fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
                }
            }
        }
        AnimatedVisibility(visible = estado.estado == EstadoConexao.ERRO, enter = fadeIn(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF2A1719), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text("Não foi possível conectar", color = PerigoV13, fontWeight = FontWeight.Medium)
                    Text(estado.mensagem, color = Color(0xFFFFB0AB), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        TextButton(onClick = abrirPainel, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
            Text("Abrir recursos completos", color = Texto2V13, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PcCardV13(pc: PcEncontrado, abrir: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = abrir), color = PainelV13, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, BordaV13)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF212A34), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text("PC", color = OuroV13, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(pc.nome, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatarIdV13(pc.maquinaId), color = OuroV13, fontSize = 12.sp)
                Text("${pc.host} · ${pc.monitores} monitor(es)", color = Texto2V13, fontSize = 9.sp)
            }
            Text("Abrir", color = TurquesaV13, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConectarDialogV13(
    pc: PcEncontrado,
    pin: String,
    senha: String,
    onPin: (String) -> Unit,
    onSenha: (String) -> Unit,
    fechar: () -> Unit,
    conectar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = PainelV13,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(pc.nome, fontSize = 20.sp)
                Text(formatarIdV13(pc.maquinaId), color = OuroV13, fontSize = 12.sp)
            }
        },
        text = {
            Column {
                Text("Sem credencial, o computador pedirá aprovação. Use o código exibido no PC ou a senha não supervisionada quando configurada.", color = Texto2V13, fontSize = 10.sp)
                OutlinedTextField(pin, onPin, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Código de 6 dígitos") }, singleLine = true)
                OutlinedTextField(senha, onSenha, Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Senha não supervisionada") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { Button(onClick = conectar) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar", color = Texto2V13) } }
    )
}

@Composable
private fun SessaoV13(estado: EstadoSessao, abrirPainel: () -> Unit) {
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    var modo by remember { mutableStateOf(ModoEntradaV13.TOQUE) }
    var menu by remember { mutableStateOf(false) }
    var teclado by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf(false) }
    val pc = estado.pc ?: return

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (estado.permissoes.tela) {
            TelaRemotaV13(quadro, monitor, estado, modo)
        } else {
            TouchpadV13(estado.permissoes.entrada)
        }

        Surface(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), color = Color(0xDD10151B), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0x663B4652))) {
            Row(Modifier.padding(11.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(TurquesaV13, CircleShape))
                Text(pc.nome, fontSize = 10.sp, modifier = Modifier.padding(start = 7.dp))
                Text(" · M${monitor + 1}/${estado.quantidadeMonitores.coerceAtLeast(1)}", color = Texto2V13, fontSize = 9.sp)
                Text(" · ${modo.titulo}", color = OuroV13, fontSize = 9.sp)
            }
        }

        MenuV13(
            aberto = menu,
            alternar = { menu = !menu },
            entrada = {
                modo = when (modo) {
                    ModoEntradaV13.TOQUE -> ModoEntradaV13.TOUCHPAD
                    ModoEntradaV13.TOUCHPAD -> ModoEntradaV13.VISUALIZAR
                    ModoEntradaV13.VISUALIZAR -> ModoEntradaV13.TOQUE
                }
                menu = false
            },
            teclado = { teclado = true; menu = false },
            monitor = {
                val total = estado.quantidadeMonitores.coerceAtLeast(1)
                SessaoPcFlow.alterarMonitor((monitor + 1) % total)
                menu = false
            },
            clipboard = { clipboard = true; menu = false },
            ferramentas = { abrirPainel(); menu = false },
            encerrar = { SessaoPcFlow.desconectar() }
        )

        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), color = Color(0xE9151B22), shape = RoundedCornerShape(23.dp), border = BorderStroke(1.dp, BordaV13)) {
            Row(Modifier.padding(5.dp)) {
                ChipModoV13("Toque", modo == ModoEntradaV13.TOQUE) { modo = ModoEntradaV13.TOQUE }
                ChipModoV13("Touchpad", modo == ModoEntradaV13.TOUCHPAD) { modo = ModoEntradaV13.TOUCHPAD }
                ChipModoV13("Ver", modo == ModoEntradaV13.VISUALIZAR) { modo = ModoEntradaV13.VISUALIZAR }
            }
        }
    }

    if (teclado) TecladoDialogV13 { teclado = false }
    if (clipboard) ClipboardDialogV13 { clipboard = false }
}

@Composable
private fun TelaRemotaV13(
    bitmap: android.graphics.Bitmap?,
    monitor: Int,
    estado: EstadoSessao,
    modo: ModoEntradaV13
) {
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
    LaunchedEffect(monitor) {
        zoom = 1f
        pan = Offset.Zero
        ponto = null
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { area = it }
            .pointerInput(bitmap, area, monitor, modo, estado.permissoes.entrada) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoEntradaV13.VISUALIZAR) return@pointerInput
                detectTapGestures(
                    onPress = { pos -> ponto = pos; pulso++ },
                    onTap = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueV13("left")
                    },
                    onDoubleTap = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueV13("left"); cliqueV13("left")
                    },
                    onLongPress = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueV13("right")
                    }
                )
            }
            .pointerInput(bitmap, area, monitor, modo, estado.permissoes.entrada) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoEntradaV13.VISUALIZAR) return@pointerInput
                if (modo == ModoEntradaV13.TOQUE) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            ponto = pos; pulso++
                            if (moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)) SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                        },
                        onDragEnd = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; ponto = null },
                        onDragCancel = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; ponto = null },
                        onDrag = { change, _ ->
                            change.consume()
                            ponto = change.position
                            moverAbsV13(change.position, area, bitmap.width, bitmap.height, monitor)
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { pos -> ponto = pos; pulso++ },
                        onDragEnd = { ponto = null },
                        onDragCancel = { ponto = null },
                        onDrag = { change, delta ->
                            change.consume()
                            ponto = change.position
                            SessaoPcFlow.enviar("mouse_move") { put("x", delta.x * 1.15); put("y", delta.y * 1.15) }
                        }
                    )
                }
            }
            .pointerInput(bitmap, modo) {
                if (bitmap == null || modo != ModoEntradaV13.VISUALIZAR) return@pointerInput
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
                CircularProgressIndicator(color = OuroV13)
                Text("Recebendo tela…", color = Texto2V13, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Image(bitmap.asImageBitmap(), "Tela remota do computador", Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
        }

        val p = ponto
        if (p != null && alpha.value > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(OuroV13.copy(alpha = alpha.value * .22f), 28.dp.toPx(), p)
                drawCircle(OuroV13.copy(alpha = alpha.value), 9.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
        }

        if (!estado.permissoes.entrada) {
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = Color(0xE52A1719), shape = RoundedCornerShape(17.dp)) {
                Text("O computador pausou o controle de entrada. A visualização continua disponível.", color = PerigoV13, textAlign = TextAlign.Center, modifier = Modifier.padding(15.dp))
            }
        }
    }
}

@Composable
private fun TouchpadV13(entrada: Boolean) {
    Box(
        Modifier.fillMaxSize().background(FundoV13)
            .pointerInput(entrada) {
                if (!entrada) return@pointerInput
                detectTapGestures(onTap = { cliqueV13("left") }, onLongPress = { cliqueV13("right") })
            }
            .pointerInput(entrada) {
                if (!entrada) return@pointerInput
                detectDragGestures { change, delta ->
                    change.consume()
                    SessaoPcFlow.enviar("mouse_move") { put("x", delta.x * 1.2); put("y", delta.y * 1.2) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Touchpad remoto", color = OuroV13, fontSize = 19.sp)
            Text("Arraste para mover · toque para clicar · segure para clique direito", color = Texto2V13, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp, 6.dp))
        }
    }
}

@Composable
private fun MenuV13(
    aberto: Boolean,
    alternar: () -> Unit,
    entrada: () -> Unit,
    teclado: () -> Unit,
    monitor: () -> Unit,
    clipboard: () -> Unit,
    ferramentas: () -> Unit,
    encerrar: () -> Unit
) {
    val rotacao by animateFloatAsState(if (aberto) 45f else 0f, label = "menu")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(visible = aberto, enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = .86f), exit = fadeOut(tween(130)) + scaleOut(tween(160), targetScale = .9f), modifier = Modifier.padding(end = 28.dp)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AcaoMenuV13("Modo de entrada", "IN", false, entrada)
                AcaoMenuV13("Teclado", "KB", false, teclado)
                AcaoMenuV13("Próximo monitor", "MN", false, monitor)
                AcaoMenuV13("Clipboard", "CP", false, clipboard)
                AcaoMenuV13("Mais ferramentas", "FX", false, ferramentas)
                AcaoMenuV13("Encerrar", "X", true, encerrar)
            }
        }
        Surface(Modifier.padding(end = 10.dp).size(54.dp).clickable(onClick = alternar), color = if (aberto) OuroV13 else Color(0xEE1A222B), shape = CircleShape, border = BorderStroke(1.dp, if (aberto) OuroV13 else BordaV13)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = if (aberto) Color.Black else OuroV13, fontSize = 28.sp, modifier = Modifier.graphicsLayer(rotationZ = rotacao))
            }
        }
    }
}

@Composable
private fun AcaoMenuV13(titulo: String, sigla: String, perigo: Boolean, click: () -> Unit) {
    Surface(Modifier.clickable(onClick = click), color = if (perigo) Color(0xE5361D20) else Color(0xEE1A222B), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, if (perigo) Color(0xFF744044) else BordaV13)) {
        Row(Modifier.padding(10.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, color = if (perigo) PerigoV13 else Color.White, fontSize = 10.sp)
            Box(Modifier.padding(start = 8.dp).size(32.dp).background(if (perigo) Color(0xFF44262A) else Color(0xFF26313D), CircleShape), contentAlignment = Alignment.Center) {
                Text(sigla, color = if (perigo) PerigoV13 else OuroV13, fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun ChipModoV13(texto: String, ativo: Boolean, click: () -> Unit) {
    Surface(Modifier.clickable(onClick = click), color = if (ativo) OuroV13 else Color.Transparent, shape = RoundedCornerShape(17.dp)) {
        Text(texto, color = if (ativo) Color.Black else Color.White, fontSize = 9.sp, fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(11.dp, 7.dp))
    }
}

@Composable
private fun TecladoDialogV13(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    Dialog(onDismissRequest = fechar) {
        Surface(color = PainelV13, shape = RoundedCornerShape(23.dp), border = BorderStroke(1.dp, BordaV13)) {
            Column(Modifier.padding(17.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Teclado remoto", fontSize = 19.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = fechar) { Text("Fechar", color = Texto2V13) }
                }
                OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Texto") })
                Button(onClick = { if (texto.isNotEmpty()) SessaoPcFlow.enviar("texto") { put("texto", texto) }; texto = "" }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Enviar texto") }
                listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT", "HOME", "END", "F5").chunked(4).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        linha.forEach { tecla ->
                            OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp)) { Text(tecla, fontSize = 7.sp) }
                        }
                        repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardDialogV13(fechar: () -> Unit) {
    val contexto = LocalContext.current
    val remoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    var local by remember { mutableStateOf("") }
    val clipboard = remember { contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    LaunchedEffect(Unit) { SessaoPcFlow.solicitarClipboard() }

    Dialog(onDismissRequest = fechar) {
        Surface(color = PainelV13, shape = RoundedCornerShape(23.dp), border = BorderStroke(1.dp, BordaV13)) {
            Column(Modifier.padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clipboard", fontSize = 19.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = fechar) { Text("Fechar", color = Texto2V13) }
                }
                Surface(Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 140.dp), color = Fundo2V13, shape = RoundedCornerShape(13.dp)) {
                    Text(remoto.ifBlank { "Nenhum texto recebido do PC." }, color = if (remoto.isBlank()) Texto2V13 else Color.White, fontSize = 10.sp, modifier = Modifier.padding(11.dp).verticalScroll(rememberScrollState()))
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("PCFlow", remoto)) }, modifier = Modifier.weight(1f)) { Text("Copiar do PC", fontSize = 9.sp) }
                    OutlinedButton(onClick = { SessaoPcFlow.solicitarClipboard() }, modifier = Modifier.weight(1f)) { Text("Atualizar", fontSize = 9.sp) }
                }
                OutlinedTextField(local, { local = it }, Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Enviar ao PC") })
                Button(onClick = { if (local.isNotBlank()) SessaoPcFlow.enviarClipboard(local) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Enviar clipboard") }
            }
        }
    }
}

@Composable
private fun MarcaV13() {
    Box(Modifier.size(46.dp).background(Color(0xFF202933), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(27.dp)) {
            val s = size.minDimension
            val e = 2.dp.toPx()
            drawLine(OuroV13, Offset(s * .12f, s * .52f), Offset(s * .43f, s * .20f), e)
            drawLine(OuroV13, Offset(s * .43f, s * .20f), Offset(s * .73f, s * .50f), e)
            drawLine(TurquesaV13, Offset(s * .28f, s * .70f), Offset(s * .55f, s * .43f), e)
            drawLine(TurquesaV13, Offset(s * .55f, s * .43f), Offset(s * .86f, s * .72f), e)
        }
    }
}

private fun cliqueV13(botao: String) {
    SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }
}

private fun moverAbsV13(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int, monitor: Int): Boolean {
    val coordenada = mapearV13(pos, area, larguraImagem, alturaImagem) ?: return false
    SessaoPcFlow.enviar("mouse_abs") {
        put("x", coordenada.first)
        put("y", coordenada.second)
        put("monitor", monitor)
    }
    return true
}

private fun mapearV13(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int): Pair<Double, Double>? {
    if (area.width <= 0 || area.height <= 0 || larguraImagem <= 0 || alturaImagem <= 0) return null
    val escala = minOf(area.width.toFloat() / larguraImagem, area.height.toFloat() / alturaImagem)
    val larguraRender = larguraImagem * escala
    val alturaRender = alturaImagem * escala
    val esquerda = (area.width - larguraRender) / 2f
    val topo = (area.height - alturaRender) / 2f
    if (pos.x < esquerda || pos.x > esquerda + larguraRender || pos.y < topo || pos.y > topo + alturaRender) return null
    val x = ((pos.x - esquerda) / larguraRender).coerceIn(0f, 1f).toDouble()
    val y = ((pos.y - topo) / alturaRender).coerceIn(0f, 1f).toDouble()
    return x to y
}

private fun lerQrV13(texto: String): Pair<PcEncontrado, String?>? {
    return try {
        val uri = Uri.parse(texto)
        if (!uri.scheme.equals("pcflow", ignoreCase = true)) {
            null
        } else {
            val host = uri.getQueryParameter("host")
            if (host.isNullOrBlank()) {
                null
            } else {
                val porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456
                val maquinaId = uri.getQueryParameter("id").orEmpty()
                val tls = uri.getQueryParameter("tls").orEmpty()
                val pin = uri.getQueryParameter("pin")
                PcEncontrado("PCFlow", host, porta, 45457, 45458, maquinaId, tls, 1) to pin
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatarIdV13(id: String): String {
    return if (id.length == 9) "${id.take(3)} ${id.substring(3, 6)} ${id.takeLast(3)}" else id
}
