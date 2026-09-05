package com.ander.pcflow

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

private val V13Fundo = Color(0xFF080B0F)
private val V13FundoElevado = Color(0xFF0E1319)
private val V13Painel = Color(0xFF151B22)
private val V13PainelClaro = Color(0xFF1C242D)
private val V13Borda = Color(0xFF303A45)
private val V13Ouro = Color(0xFFF3B13F)
private val V13Turquesa = Color(0xFF24D2C2)
private val V13Texto2 = Color(0xFF9EABB8)
private val V13Perigo = Color(0xFFFF716A)

private enum class ModoEntradaV13(val titulo: String) {
    TOQUE("Toque"),
    TOUCHPAD("Touchpad"),
    VISUALIZAR("Visualizar")
}

private enum class AcaoSessaoV13(val titulo: String, val sigla: String) {
    ENTRADA("Modo de entrada", "IN"),
    TECLADO("Teclado", "KB"),
    MONITOR("Monitores", "MN"),
    CLIPBOARD("Clipboard", "CP"),
    FERRAMENTAS("Ferramentas", "FX"),
    ENCERRAR("Encerrar", "X")
}

@Composable
fun PcFlowV13App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = V13Fundo,
            surface = V13Painel,
            primary = V13Ouro,
            secondary = V13Turquesa,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        PcFlowV13Raiz()
    }
}

@Composable
private fun PcFlowV13Raiz() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var selecionado by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var painelCompleto by remember { mutableStateOf(false) }

    val permissaoNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val lido = resultado.contents?.let(::lerQrV13)
        if (lido != null) {
            selecionado = lido.first
            pin = lido.second.orEmpty()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    if (painelCompleto) {
        Box(Modifier.fillMaxSize()) {
            PcFlowApp()
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clickable { painelCompleto = false },
                color = Color(0xE8151B22),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V13Borda)
            ) {
                Text("Voltar ao controle V1.3", color = V13Ouro, fontSize = 12.sp, modifier = Modifier.padding(14.dp, 10.dp))
            }
        }
        return
    }

    Surface(Modifier.fillMaxSize(), color = V13Fundo) {
        when (estado.estado) {
            EstadoConexao.CONECTADO -> TelaSessaoV13(estado, abrirPainelCompleto = { painelCompleto = true })
            else -> TelaInicialV13(
                pcs = pcs,
                estado = estado,
                atualizar = SessaoPcFlow::descobrir,
                abrirPc = { selecionado = it; pin = ""; senha = "" },
                escanear = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Aponte para o QR Code do PCFlow")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
                abrirPainelCompleto = { painelCompleto = true }
            )
        }
    }

    selecionado?.let { pc ->
        DialogoConectarV13(
            pc = pc,
            pin = pin,
            senha = senha,
            alterarPin = { pin = it.filter(Char::isDigit).take(6) },
            alterarSenha = { senha = it },
            cancelar = { selecionado = null },
            conectar = {
                SessaoPcFlow.conectar(pc, pin.ifBlank { null }, senha.ifBlank { null })
                selecionado = null
            }
        )
    }
}

@Composable
private fun TelaInicialV13(
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    atualizar: () -> Unit,
    abrirPc: (PcEncontrado) -> Unit,
    escanear: () -> Unit,
    abrirPainelCompleto: () -> Unit
) {
    var idDigitado by remember { mutableStateOf("") }
    val filtro = idDigitado.filter(Char::isDigit)
    val filtrados = if (filtro.isBlank()) pcs else pcs.filter { it.maquinaId.contains(filtro) }
    val pcExato = pcs.firstOrNull { it.maquinaId == filtro }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarcaPcFlowV13()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PCFlow", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Acesso remoto simples, seguro e direto", color = V13Texto2, fontSize = 12.sp)
            }
            Text("V1.3", color = V13Turquesa, fontSize = 12.sp)
        }

        Surface(
            Modifier.fillMaxWidth().padding(top = 26.dp).animateContentSize(),
            color = V13Painel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, V13Borda)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Conectar ao computador", fontSize = 13.sp, color = V13Texto2)
                OutlinedTextField(
                    value = idDigitado,
                    onValueChange = { idDigitado = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("000 000 000") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = { pcExato?.let(abrirPc) },
                        enabled = pcExato != null,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Conectar") }
                    OutlinedButton(
                        onClick = escanear,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, V13Borda)
                    ) { Text("Escanear QR", color = V13Ouro) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Dispositivos na rede", fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = atualizar) { Text("Atualizar", color = V13Ouro) }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (filtrados.isEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), color = V13FundoElevado, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V13Borda)) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (filtro.isBlank()) "Procurando computadores PCFlow…" else "Nenhum computador com esse ID apareceu nesta rede.", textAlign = TextAlign.Center)
                            Text("Mantenha o PCFlow aberto ou na bandeja do Windows e confirme que os dois aparelhos estão na mesma rede.", color = V13Texto2, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            } else {
                items(filtrados, key = { it.maquinaId.ifBlank { it.host } }) { pc ->
                    DispositivoCardV13(pc) { abrirPc(pc) }
                }
            }
        }

        AnimatedVisibility(visible = estado.estado == EstadoConexao.CONECTANDO, enter = fadeIn(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF18251F), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = V13Turquesa)
                    Text(estado.mensagem.ifBlank { "Conectando…" }, color = V13Turquesa, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        AnimatedVisibility(visible = estado.estado == EstadoConexao.ERRO, enter = fadeIn(), exit = fadeOut()) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF2A1719), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Falha na conexão", color = V13Perigo, fontWeight = FontWeight.Medium)
                    Text(estado.mensagem, color = Color(0xFFFFAAA5), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = abrirPainelCompleto) { Text("Abrir painel completo", color = V13Texto2, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun DispositivoCardV13(pc: PcEncontrado, conectar: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = conectar),
        color = V13Painel,
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, V13Borda)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF212A34), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text("PC", color = V13Ouro, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(pc.nome, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatarIdV13(pc.maquinaId), color = V13Ouro, fontSize = 13.sp)
                Text("${pc.host} · ${pc.monitores} monitor(es)", color = V13Texto2, fontSize = 10.sp)
            }
            Text("Abrir", color = V13Turquesa, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DialogoConectarV13(
    pc: PcEncontrado,
    pin: String,
    senha: String,
    alterarPin: (String) -> Unit,
    alterarSenha: (String) -> Unit,
    cancelar: () -> Unit,
    conectar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = cancelar,
        containerColor = V13Painel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(pc.nome, fontSize = 21.sp)
                Text(formatarIdV13(pc.maquinaId), color = V13Ouro, fontSize = 13.sp)
            }
        },
        text = {
            Column {
                Text("Sem credencial, o computador pedirá aprovação. Você também pode usar o código exibido no PC ou uma senha de acesso não supervisionado.", color = V13Texto2, fontSize = 11.sp)
                OutlinedTextField(value = pin, onValueChange = alterarPin, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Código de 6 dígitos") }, singleLine = true)
                OutlinedTextField(value = senha, onValueChange = alterarSenha, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Senha não supervisionada") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { Button(onClick = conectar) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar", color = V13Texto2) } }
    )
}

@Composable
private fun TelaSessaoV13(estado: EstadoSessao, abrirPainelCompleto: () -> Unit) {
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    var modo by remember { mutableStateOf(ModoEntradaV13.TOQUE) }
    var menuAberto by remember { mutableStateOf(false) }
    var tecladoAberto by remember { mutableStateOf(false) }
    var clipboardAberto by remember { mutableStateOf(false) }
    var seletorEntrada by remember { mutableStateOf(false) }
    val pc = estado.pc ?: return

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (estado.permissoes.tela) {
            TelaRemotaV13(quadro, monitor, estado, modo)
        } else {
            TouchpadSemTelaV13(estado.permissoes.entrada)
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            color = Color(0xD910151B),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0x663B4652))
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(V13Turquesa, CircleShape))
                Text(pc.nome, fontSize = 11.sp, modifier = Modifier.padding(start = 7.dp))
                Text(" · M${monitor + 1}/${estado.quantidadeMonitores}", color = V13Texto2, fontSize = 10.sp)
                Text(" · ${modo.titulo}", color = V13Ouro, fontSize = 10.sp)
            }
        }

        MenuSessaoV13(
            aberto = menuAberto,
            alternar = { menuAberto = !menuAberto },
            executar = { acao ->
                when (acao) {
                    AcaoSessaoV13.ENTRADA -> seletorEntrada = true
                    AcaoSessaoV13.TECLADO -> tecladoAberto = true
                    AcaoSessaoV13.MONITOR -> SessaoPcFlow.alterarMonitor((monitor + 1) % estado.quantidadeMonitores.coerceAtLeast(1))
                    AcaoSessaoV13.CLIPBOARD -> clipboardAberto = true
                    AcaoSessaoV13.FERRAMENTAS -> abrirPainelCompleto()
                    AcaoSessaoV13.ENCERRAR -> SessaoPcFlow.desconectar()
                }
                menuAberto = false
            }
        )

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            color = Color(0xE9151B22),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, V13Borda)
        ) {
            Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ModoChipV13("Toque", modo == ModoEntradaV13.TOQUE) { modo = ModoEntradaV13.TOQUE }
                ModoChipV13("Touchpad", modo == ModoEntradaV13.TOUCHPAD) { modo = ModoEntradaV13.TOUCHPAD }
                ModoChipV13("Ver", modo == ModoEntradaV13.VISUALIZAR) { modo = ModoEntradaV13.VISUALIZAR }
            }
        }
    }

    if (tecladoAberto) DialogoTecladoV13 { tecladoAberto = false }
    if (clipboardAberto) DialogoClipboardV13 { clipboardAberto = false }
    if (seletorEntrada) {
        AlertDialog(
            onDismissRequest = { seletorEntrada = false },
            containerColor = V13Painel,
            title = { Text("Modo de entrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModoLinhaV13("Toque direto", "O ponto tocado no celular corresponde ao ponto da tela do PC.", modo == ModoEntradaV13.TOQUE) { modo = ModoEntradaV13.TOQUE; seletorEntrada = false }
                    ModoLinhaV13("Touchpad", "Arraste em qualquer lugar para mover o cursor; toque para clicar.", modo == ModoEntradaV13.TOUCHPAD) { modo = ModoEntradaV13.TOUCHPAD; seletorEntrada = false }
                    ModoLinhaV13("Visualizar", "Pan e zoom locais sem enviar cliques ao PC.", modo == ModoEntradaV13.VISUALIZAR) { modo = ModoEntradaV13.VISUALIZAR; seletorEntrada = false }
                }
            },
            confirmButton = { TextButton(onClick = { seletorEntrada = false }) { Text("Fechar", color = V13Texto2) } }
        )
    }
}

@Composable
private fun TelaRemotaV13(
    bitmap: android.graphics.Bitmap?,
    monitor: Int,
    estado: EstadoSessao,
    modo: ModoEntradaV13
) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var feedback by remember { mutableStateOf<Offset?>(null) }
    var feedbackId by remember { mutableIntStateOf(0) }
    val alphaFeedback = remember { Animatable(0f) }
    var zoom by remember { mutableStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(feedbackId) {
        if (feedbackId == 0) return@LaunchedEffect
        alphaFeedback.snapTo(0.95f)
        alphaFeedback.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(monitor) {
        zoom = 1f
        deslocamento = Offset.Zero
        feedback = null
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { area = it }
            .pointerInput(bitmap, area, monitor, estado.permissoes.entrada, modo) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoEntradaV13.VISUALIZAR) return@pointerInput
                detectTapGestures(
                    onPress = { pos -> feedback = pos; feedbackId++ },
                    onTap = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) enviarPosicaoV13(pos, area, bitmap.width, bitmap.height, monitor, "left")
                        else cliqueV13("left")
                    },
                    onDoubleTap = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) {
                            moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)
                        }
                        cliqueV13("left"); cliqueV13("left")
                    },
                    onLongPress = { pos ->
                        if (modo == ModoEntradaV13.TOQUE) moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)
                        cliqueV13("right")
                    }
                )
            }
            .pointerInput(bitmap, area, monitor, estado.permissoes.entrada, modo) {
                if (bitmap == null || !estado.permissoes.entrada || modo == ModoEntradaV13.VISUALIZAR) return@pointerInput
                if (modo == ModoEntradaV13.TOQUE) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            feedback = pos; feedbackId++
                            if (moverAbsV13(pos, area, bitmap.width, bitmap.height, monitor)) {
                                SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                            }
                        },
                        onDragEnd = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; feedback = null },
                        onDragCancel = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }; feedback = null },
                        onDrag = { change, _ ->
                            change.consume()
                            feedback = change.position
                            moverAbsV13(change.position, area, bitmap.width, bitmap.height, monitor)
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { pos -> feedback = pos; feedbackId++ },
                        onDragEnd = { feedback = null },
                        onDragCancel = { feedback = null },
                        onDrag = { change, drag ->
                            change.consume()
                            feedback = change.position
                            SessaoPcFlow.enviar("mouse_move") { put("x", drag.x * 1.15); put("y", drag.y * 1.15) }
                        }
                    )
                }
            }
            .pointerInput(bitmap, modo) {
                if (bitmap == null || modo != ModoEntradaV13.VISUALIZAR) return@pointerInput
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                    deslocamento += pan
                    if (zoom <= 1.01f) deslocamento = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = V13Ouro)
                Text("Recebendo tela…", color = V13Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            }
        } else {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Tela remota do computador",
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = deslocamento.x,
                    translationY = deslocamento.y
                ),
                contentScale = ContentScale.Fit
            )
        }

        val ponto = feedback
        if (ponto != null && alphaFeedback.value > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(V13Ouro.copy(alpha = alphaFeedback.value * .22f), radius = 28.dp.toPx(), center = ponto)
                drawCircle(V13Ouro.copy(alpha = alphaFeedback.value), radius = 9.dp.toPx(), center = ponto, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }

        if (!estado.permissoes.entrada) {
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = Color(0xE52A1719), shape = RoundedCornerShape(18.dp)) {
                Text("O computador bloqueou o controle de entrada. A visualização continua disponível.", color = V13Perigo, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun TouchpadSemTelaV13(entrada: Boolean) {
    Box(
        Modifier.fillMaxSize().background(V13Fundo)
            .pointerInput(entrada) {
                if (!entrada) return@pointerInput
                detectTapGestures(onTap = { cliqueV13("left") }, onLongPress = { cliqueV13("right") })
            }
            .pointerInput(entrada) {
                if (!entrada) return@pointerInput
                detectDragGestures { change, drag ->
                    change.consume()
                    SessaoPcFlow.enviar("mouse_move") { put("x", drag.x * 1.2); put("y", drag.y * 1.2) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Touchpad remoto", color = V13Ouro, fontSize = 20.sp)
            Text("Arraste para mover · toque para clicar · segure para clique direito", color = V13Texto2, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
        }
    }
}

@Composable
private fun MenuSessaoV13(aberto: Boolean, alternar: () -> Unit, executar: (AcaoSessaoV13) -> Unit) {
    val rotacao by animateFloatAsState(if (aberto) 45f else 0f, label = "rotacaoMenu")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = aberto,
            enter = fadeIn(tween(170)) + scaleIn(tween(210), initialScale = .86f),
            exit = fadeOut(tween(140)) + scaleOut(tween(170), targetScale = .9f),
            modifier = Modifier.padding(end = 28.dp)
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AcaoSessaoV13.entries.forEach { acao ->
                    Surface(
                        modifier = Modifier.clickable { executar(acao) },
                        color = if (acao == AcaoSessaoV13.ENCERRAR) Color(0xE5361D20) else Color(0xEE1A222B),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (acao == AcaoSessaoV13.ENCERRAR) Color(0xFF744044) else V13Borda)
                    ) {
                        Row(Modifier.padding(11.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(acao.titulo, color = if (acao == AcaoSessaoV13.ENCERRAR) V13Perigo else Color.White, fontSize = 11.sp)
                            Box(Modifier.padding(start = 9.dp).size(34.dp).background(if (acao == AcaoSessaoV13.ENCERRAR) Color(0xFF44262A) else Color(0xFF26313D), CircleShape), contentAlignment = Alignment.Center) {
                                Text(acao.sigla, color = if (acao == AcaoSessaoV13.ENCERRAR) V13Perigo else V13Ouro, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.padding(end = 10.dp).size(54.dp).clickable(onClick = alternar),
            color = if (aberto) V13Ouro else Color(0xEE1A222B),
            shape = CircleShape,
            border = BorderStroke(1.dp, if (aberto) V13Ouro else V13Borda)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = if (aberto) Color.Black else V13Ouro, fontSize = 28.sp, modifier = Modifier.graphicsLayer(rotationZ = rotacao))
            }
        }
    }
}

@Composable
private fun ModoChipV13(texto: String, ativo: Boolean, click: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = click),
        color = if (ativo) V13Ouro else Color.Transparent,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(texto, color = if (ativo) Color.Black else Color.White, fontSize = 10.sp, fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(12.dp, 7.dp))
    }
}

@Composable
private fun ModoLinhaV13(titulo: String, descricao: String, selecionado: Boolean, click: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = click), color = if (selecionado) Color(0xFF202D2B) else V13FundoElevado, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if (selecionado) V13Turquesa else V13Borda)) {
        Column(Modifier.padding(13.dp)) {
            Text(titulo, color = if (selecionado) V13Turquesa else Color.White, fontWeight = FontWeight.Medium)
            Text(descricao, color = V13Texto2, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun DialogoTecladoV13(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    Dialog(onDismissRequest = fechar) {
        Surface(color = V13Painel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, V13Borda)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Teclado remoto", fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = fechar) { Text("Fechar", color = V13Texto2) }
                }
                Text("Digite no celular e envie para o campo ativo no Windows.", color = V13Texto2, fontSize = 11.sp)
                OutlinedTextField(value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 3, label = { Text("Texto") })
                Button(onClick = { if (texto.isNotEmpty()) SessaoPcFlow.enviar("texto") { put("texto", texto) }; texto = "" }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Enviar texto") }
                val teclas = listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT", "HOME", "END", "F5")
                teclas.chunked(4).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        linha.forEach { tecla ->
                            OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                                Text(tecla, fontSize = 8.sp)
                            }
                        }
                        repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoClipboardV13(fechar: () -> Unit) {
    val contexto = LocalContext.current
    val remoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    var textoLocal by remember { mutableStateOf("") }
    val gerenciador = remember { contexto.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

    LaunchedEffect(Unit) { SessaoPcFlow.solicitarClipboard() }

    Dialog(onDismissRequest = fechar) {
        Surface(color = V13Painel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, V13Borda)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Clipboard", fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = fechar) { Text("Fechar", color = V13Texto2) }
                }
                Surface(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 150.dp), color = V13FundoElevado, shape = RoundedCornerShape(14.dp)) {
                    Text(remoto.ifBlank { "Nenhum texto recebido do PC." }, color = if (remoto.isBlank()) V13Texto2 else Color.White, fontSize = 11.sp, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { gerenciador.setPrimaryClip(android.content.ClipData.newPlainText("PCFlow", remoto)) }, modifier = Modifier.weight(1f)) { Text("Copiar do PC", fontSize = 10.sp) }
                    OutlinedButton(onClick = { SessaoPcFlow.solicitarClipboard() }, modifier = Modifier.weight(1f)) { Text("Atualizar", fontSize = 10.sp) }
                }
                OutlinedTextField(value = textoLocal, onValueChange = { textoLocal = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Enviar ao PC") })
                Button(onClick = { if (textoLocal.isNotBlank()) SessaoPcFlow.enviarClipboard(textoLocal) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Enviar clipboard") }
            }
        }
    }
}

@Composable
private fun MarcaPcFlowV13() {
    Box(Modifier.size(46.dp).background(Color(0xFF202933), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(27.dp)) {
            val s = size.minDimension
            val esp = 2.dp.toPx()
            drawLine(V13Ouro, Offset(s * .12f, s * .52f), Offset(s * .43f, s * .20f), strokeWidth = esp)
            drawLine(V13Ouro, Offset(s * .43f, s * .20f), Offset(s * .73f, s * .50f), strokeWidth = esp)
            drawLine(V13Turquesa, Offset(s * .28f, s * .70f), Offset(s * .55f, s * .43f), strokeWidth = esp)
            drawLine(V13Turquesa, Offset(s * .55f, s * .43f), Offset(s * .86f, s * .72f), strokeWidth = esp)
        }
    }
}

private fun cliqueV13(botao: String) = SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }

private fun moverAbsV13(pos: Offset, area: IntSize, w: Int, h: Int, monitor: Int): Boolean {
    val p = mapearV13(pos, area, w, h) ?: return false
    SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
    return true
}

private fun enviarPosicaoV13(pos: Offset, area: IntSize, w: Int, h: Int, monitor: Int, botao: String) {
    if (moverAbsV13(pos, area, w, h, monitor)) cliqueV13(botao)
}

private fun mapearV13(pos: Offset, area: IntSize, w: Int, h: Int): Pair<Double, Double>? {
    if (area.width <= 0 || area.height <= 0 || w <= 0 || h <= 0) return null
    val escala = minOf(area.width.toFloat() / w, area.height.toFloat() / h)
    val rw = w * escala
    val rh = h * escala
    val esquerda = (area.width - rw) / 2f
    val topo = (area.height - rh) / 2f
    if (pos.x < esquerda || pos.x > esquerda + rw || pos.y < topo || pos.y > topo + rh) return null
    return ((pos.x - esquerda) / rw).coerceIn(0f, 1f).toDouble() to ((pos.y - topo) / rh).coerceIn(0f, 1f).toDouble()
}

private fun lerQrV13(texto: String): Pair<PcEncontrado, String?>? = try {
    val uri = Uri.parse(texto)
    if (!uri.scheme.equals("pcflow", ignoreCase = true)) return null
    val host = uri.getQueryParameter("host") ?: return null
    val porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456
    val maquinaId = uri.getQueryParameter("id").orEmpty()
    val tls = uri.getQueryParameter("tls").orEmpty()
    val pin = uri.getQueryParameter("pin")
    PcEncontrado(
        nome = "PCFlow",
        host = host,
        porta = porta,
        portaTela = 45457,
        portaArquivos = 45458,
        maquinaId = maquinaId,
        tls = tls,
        monitores = 1
    ) to pin
} catch (_: Exception) { null }

private fun formatarIdV13(id: String): String = if (id.length == 9) "${id.take(3)} ${id.substring(3, 6)} ${id.takeLast(3)}" else id
