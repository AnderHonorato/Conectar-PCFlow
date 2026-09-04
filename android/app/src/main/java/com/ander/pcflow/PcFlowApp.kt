package com.ander.pcflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

private val Fundo = Color(0xFF080B0F)
private val Fundo2 = Color(0xFF10141A)
private val Painel = Color(0xFF151A22)
private val Painel2 = Color(0xFF1B212A)
private val Borda = Color(0xFF363D48)
private val Ouro = Color(0xFFF4AD2F)
private val Ouro20 = Color(0x33F4AD2F)
private val Ciano = Color(0xFF17D0C4)
private val Texto2 = Color(0xFF9DA5B0)
private val Perigo = Color(0xFFFF7A70)

private enum class Aba { TOUCHPAD, GAMES, LAYOUTS, UTILITIES, MORE }
private enum class PainelMenu { NONE, MEDIA, POWER, PPT, WEB, TECLADO, REMOTE, FILES, CLIPBOARD }
private enum class Ico { MONITOR, MOUSE, GAME, GRID, TOOLS, MORE, PLAY, POWER, PPT, WEB, KEYBOARD, FILE, CLIP, CAMERA, TASK, PHONE }

@Composable
fun PcFlowApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Fundo,
            surface = Painel,
            primary = Ouro,
            secondary = Ciano,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) { RootPcFlow() }
}

@Composable
private fun RootPcFlow() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var selecionado by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    val notificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val lido = r.contents?.let(::lerQrPcFlow)
        if (lido != null) {
            selecionado = lido.first
            pin = lido.second.orEmpty()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    Surface(Modifier.fillMaxSize(), color = Fundo) {
        if (estado.estado == EstadoConexao.CONECTADO && estado.pc != null) {
            TelaControle(estado)
        } else {
            TelaConectar(
                pcs = pcs,
                estado = estado,
                atualizar = SessaoPcFlow::descobrir,
                conectar = { selecionado = it; pin = ""; senha = "" },
                escanear = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Aponte para o QR Code do PCFlow no computador")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }
            )
        }
    }

    selecionado?.let { pc ->
        AlertDialog(
            onDismissRequest = { selecionado = null },
            containerColor = Painel,
            shape = RoundedCornerShape(22.dp),
            title = {
                Column {
                    Text("Conectar a ${pc.nome}")
                    Text(formatarId(pc.maquinaId), color = Ouro, fontSize = 14.sp)
                }
            },
            text = {
                Column {
                    Text("Sem código ou senha, a solicitação aparece no PC para ser aceita. Você também pode usar o QR/código ou a senha de acesso não supervisionado.", color = Texto2, fontSize = 12.sp)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Senha não supervisionada") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    SessaoPcFlow.conectar(pc, pin.ifBlank { null }, senha.ifBlank { null })
                    selecionado = null
                }) { Text("Conectar") }
            },
            dismissButton = { TextButton(onClick = { selecionado = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TelaConectar(
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    atualizar: () -> Unit,
    conectar: (PcEncontrado) -> Unit,
    escanear: () -> Unit
) {
    var busca by remember { mutableStateOf("") }
    val filtro = busca.filter(Char::isDigit)
    val encontrados = if (filtro.isBlank()) pcs else pcs.filter { it.maquinaId.contains(filtro) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(Ico.MONITOR, Ouro, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PCFlow", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Text("Controle remoto seguro na sua rede", color = Texto2, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it.filter(Char::isDigit).take(9) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ID do computador") },
            singleLine = true,
            shape = RoundedCornerShape(13.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = atualizar, modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(26.dp)) {
                Text("Buscar na rede", color = Ouro)
            }
            Button(onClick = escanear, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Escanear QR") }
        }

        Text("Computadores encontrados", fontSize = 20.sp, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (encontrados.isEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), color = Painel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Borda)) {
                        Column(Modifier.padding(18.dp)) {
                            Text(if (filtro.isBlank()) "Procurando PCFlow na rede…" else "Esse ID não apareceu nesta rede.")
                            Text("Deixe o aplicativo Windows aberto ou na bandeja e use a mesma rede Wi‑Fi/LAN.", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            } else {
                items(encontrados, key = { it.maquinaId.ifBlank { it.host } }) { pc ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { conectar(pc) },
                        color = Painel,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Borda)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icone(Ico.MONITOR, Ouro, 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pc.nome, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatarId(pc.maquinaId), color = Ouro, fontSize = 14.sp)
                                Text("${pc.host} · ${pc.monitores} monitor(es)", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            Text("Conectar", color = Ouro, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        when (estado.estado) {
            EstadoConexao.CONECTANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(estado.mensagem, color = Ouro, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }
            EstadoConexao.ERRO -> {
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF2B181B), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Não foi possível conectar", color = Perigo, fontWeight = FontWeight.Medium)
                        Text(estado.mensagem, color = Color(0xFFFFAAA2), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            else -> Unit
        }
        Text("TLS com pinagem · conexão restrita à LAN", color = Texto2, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
    }
}

@Composable
private fun TelaControle(estado: EstadoSessao) {
    var aba by remember { mutableStateOf(Aba.TOUCHPAD) }
    var painel by remember { mutableStateOf(PainelMenu.NONE) }
    val pc = requireNotNull(estado.pc)

    Column(Modifier.fillMaxSize().background(Fundo)) {
        Row(Modifier.fillMaxWidth().background(Fundo2).padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icone(Ico.MONITOR, Ouro, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pc.nome, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Ciano, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text("Conectado · ${formatarId(pc.maquinaId)}", color = Ciano, fontSize = 11.sp)
                }
            }
            TextButton(onClick = SessaoPcFlow::desconectar) { Text("Sair", color = Texto2) }
        }

        BarraTopo(estado) { painel = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (aba) {
                Aba.TOUCHPAD -> Touchpad(estado.permissoes.entrada)
                Aba.GAMES -> Games()
                Aba.LAYOUTS -> Layouts { p -> painel = p }
                Aba.UTILITIES -> Utilities(estado) { p -> painel = p }
                Aba.MORE -> Mais(estado) { p -> painel = p }
            }
        }
        BarraBaixo(aba) { aba = it }
    }

    when (painel) {
        PainelMenu.MEDIA -> DialogoMedia { painel = PainelMenu.NONE }
        PainelMenu.POWER -> DialogoPower(estado.permissoes.energia) { painel = PainelMenu.NONE }
        PainelMenu.PPT -> DialogoPpt { painel = PainelMenu.NONE }
        PainelMenu.WEB -> DialogoWeb { painel = PainelMenu.NONE }
        PainelMenu.TECLADO -> DialogoTeclado { painel = PainelMenu.NONE }
        PainelMenu.REMOTE -> RemoteDesktop(estado) { painel = PainelMenu.NONE }
        PainelMenu.FILES -> if (estado.permissoes.arquivos) DialogoArquivosRemotos { painel = PainelMenu.NONE }
        PainelMenu.CLIPBOARD -> DialogoClipboard { painel = PainelMenu.NONE }
        PainelMenu.NONE -> Unit
    }
}

@Composable
private fun BarraTopo(estado: EstadoSessao, abrir: (PainelMenu) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF24272D)).horizontalScroll(rememberScrollState()).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Topo("Media", Ico.PLAY) { abrir(PainelMenu.MEDIA) }
        Topo("Power", Ico.POWER) { abrir(PainelMenu.POWER) }
        Topo("PPT", Ico.PPT) { abrir(PainelMenu.PPT) }
        Topo("Web", Ico.WEB) { abrir(PainelMenu.WEB) }
        Topo("Teclado", Ico.KEYBOARD) { abrir(PainelMenu.TECLADO) }
        if (estado.permissoes.tela) Topo("Desktop", Ico.MONITOR) { abrir(PainelMenu.REMOTE) }
        if (estado.permissoes.arquivos) Topo("Arquivos", Ico.FILE) { abrir(PainelMenu.FILES) }
    }
}

@Composable
private fun Topo(texto: String, ico: Ico, click: () -> Unit) {
    Column(Modifier.width(67.dp).clickable(onClick = click).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icone(ico, Color.White, 25.dp)
        Text(texto, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun Touchpad(entrada: Boolean) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        if (!entrada) Text("Controle de entrada bloqueado pelo PC", color = Perigo, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .border(1.dp, Color(0xFF3857FF), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .pointerInput(entrada) {
                        if (!entrada) return@pointerInput
                        detectTapGestures(
                            onTap = { mouseClique("left") },
                            onDoubleTap = { mouseClique("left"); mouseClique("left") },
                            onLongPress = { mouseClique("right") }
                        )
                    }
                    .pointerInput(entrada) {
                        if (!entrada) return@pointerInput
                        detectDragGestures { change, drag ->
                            change.consume()
                            SessaoPcFlow.enviar("mouse_move") { put("x", drag.x * 1.35); put("y", drag.y * 1.35) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(150.dp)) {
                    drawCircle(Ouro20, radius = size.minDimension * .24f)
                    drawCircle(Ouro.copy(alpha = .35f), radius = size.minDimension * .12f)
                    drawCircle(Ouro, radius = size.minDimension * .055f)
                }
                Text("Toque para clicar", color = Ouro, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 68.dp))
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(58.dp).background(Color(0xFF050607))) {
                    MouseBotao("Left", Modifier.weight(1f)) { mouseClique("left") }
                    MouseBotao("Middle", Modifier.width(82.dp)) { mouseClique("middle") }
                    MouseBotao("Right", Modifier.weight(1f)) { mouseClique("right") }
                }
            }
            Box(
                Modifier.width(46.dp).fillMaxHeight().background(Color.Black, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .border(1.dp, Color(0xFF3857FF), RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .pointerInput(entrada) {
                        if (!entrada) return@pointerInput
                        detectDragGestures { change, drag ->
                            change.consume()
                            val delta = (-drag.y * 8).toInt().coerceIn(-720, 720)
                            if (delta != 0) SessaoPcFlow.enviar("scroll") { put("delta", delta) }
                        }
                    }, contentAlignment = Alignment.Center
            ) { Box(Modifier.width(4.dp).height(64.dp).background(Borda, RoundedCornerShape(4.dp))) }
        }
    }
}

@Composable
private fun MouseBotao(texto: String, modifier: Modifier, click: () -> Unit) {
    Box(modifier.fillMaxHeight().border(.5.dp, Borda).clickable(onClick = click), contentAlignment = Alignment.Center) { Text(texto, fontSize = 11.sp) }
}

@Composable
private fun Games() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Games", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Layout de controle por teclado", color = Texto2, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GameKey("W")
                Row { GameKey("A"); GameKey("S"); GameKey("D") }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row { GameKey("ESC", "Y"); GameKey("SPACE", "X") }
                Row { GameKey("ENTER", "B"); GameKey("TAB", "A") }
            }
        }
    }
}

@Composable
private fun GameKey(tecla: String, rotulo: String = tecla) {
    Box(Modifier.size(64.dp).padding(4.dp).background(Painel2, CircleShape).border(1.dp, Borda, CircleShape).clickable { tecla(tecla) }, contentAlignment = Alignment.Center) {
        Text(rotulo, color = Ouro, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Layouts(abrir: (PainelMenu) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Layouts", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Modos prontos para cada tarefa", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
        LayoutCard("Touchpad", "Mouse e scroll", Ico.MOUSE) { }
        LayoutCard("Apresentação", "PowerPoint / LibreOffice", Ico.PPT) { abrir(PainelMenu.PPT) }
        LayoutCard("Navegador", "Abas e navegação web", Ico.WEB) { abrir(PainelMenu.WEB) }
        LayoutCard("Remote Desktop", "Controle visual da tela", Ico.MONITOR) { abrir(PainelMenu.REMOTE) }
    }
}

@Composable
private fun LayoutCard(titulo: String, subtitulo: String, ico: Ico, click: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = click), color = Painel, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Borda)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icone(ico, Ouro, 34.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(titulo); Text(subtitulo, color = Texto2, fontSize = 11.sp) }
            Text("Abrir", color = Ouro, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Utilities(estado: EstadoSessao, abrir: (PainelMenu) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Utilities", fontSize = 23.sp)
        Spacer(Modifier.height(16.dp))
        UtilityRow(
            Util("Remote Desktop", Ico.MONITOR, estado.permissoes.tela) { abrir(PainelMenu.REMOTE) },
            Util("File Explorer", Ico.FILE, estado.permissoes.arquivos) { abrir(PainelMenu.FILES) },
            Util("Task Manager", Ico.TASK, true) { tecla("TASK_MANAGER") }
        )
        UtilityRow(
            Util("Data Cable", Ico.PHONE, estado.permissoes.arquivos) { abrir(PainelMenu.FILES) },
            Util("Clipboard", Ico.CLIP, estado.permissoes.clipboard) { abrir(PainelMenu.CLIPBOARD) },
            Util("Explorer", Ico.FILE, true) { tecla("WIN_E") }
        )
        Text("Em desenvolvimento", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        UtilityRow(
            Util("Projector", Ico.MONITOR, false, null),
            Util("Camera", Ico.CAMERA, false, null),
            Util("Virtual Camera", Ico.CAMERA, false, null)
        )
    }
}

private data class Util(val nome: String, val ico: Ico, val ativo: Boolean, val click: (() -> Unit)?)

@Composable
private fun UtilityRow(vararg itens: Util) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itens.forEach { u ->
            val mod = Modifier.weight(1f).aspectRatio(.95f)
            Surface(
                modifier = if (u.ativo && u.click != null) mod.clickable(onClick = u.click) else mod,
                color = if (u.ativo) Painel else Color(0xFF101419),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (u.ativo) Borda else Color(0xFF282D34))
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icone(u.ico, if (u.ativo) Color.White else Color(0xFF555C65), 32.dp)
                    Text(u.nome, color = if (u.ativo) Color.White else Color(0xFF666D76), fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Mais(estado: EstadoSessao, abrir: (PainelMenu) -> Unit) {
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("More", fontSize = 23.sp)
        MoreItem("Clipboard", "Sincronizar texto") { abrir(PainelMenu.CLIPBOARD) }
        MoreItem("Trocar monitor", "Monitor ${monitor + 1} de ${estado.quantidadeMonitores}") { SessaoPcFlow.alterarMonitor((monitor + 1) % estado.quantidadeMonitores.coerceAtLeast(1)) }
        MoreItem("Mostrar desktop", "Win + D") { tecla("SHOW_DESKTOP") }
        MoreItem("Alternar janela", "Alt + Tab") { tecla("ALT_TAB") }
        MoreItem("Menu Iniciar", "Tecla Windows") { tecla("START_MENU") }
        MoreItem("Executar", "Win + R") { tecla("WIN_R") }
        OutlinedButton(onClick = SessaoPcFlow::desconectar, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), border = BorderStroke(1.dp, Color(0xFF784142))) { Text("Desconectar", color = Perigo) }
    }
}

@Composable
private fun MoreItem(titulo: String, subtitulo: String, click: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = click), color = Painel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Borda)) {
        Column(Modifier.padding(14.dp)) { Text(titulo); Text(subtitulo, color = Texto2, fontSize = 11.sp) }
    }
}

@Composable
private fun BarraBaixo(atual: Aba, selecionar: (Aba) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF3A3A3D)).padding(5.dp, 7.dp), horizontalArrangement = Arrangement.SpaceAround) {
        Bottom("Touchpad", Ico.MOUSE, atual == Aba.TOUCHPAD) { selecionar(Aba.TOUCHPAD) }
        Bottom("Games", Ico.GAME, atual == Aba.GAMES) { selecionar(Aba.GAMES) }
        Bottom("Layouts", Ico.GRID, atual == Aba.LAYOUTS) { selecionar(Aba.LAYOUTS) }
        Bottom("Utilities", Ico.TOOLS, atual == Aba.UTILITIES) { selecionar(Aba.UTILITIES) }
        Bottom("More", Ico.MORE, atual == Aba.MORE) { selecionar(Aba.MORE) }
    }
}

@Composable
private fun Bottom(texto: String, ico: Ico, ativo: Boolean, click: () -> Unit) {
    Column(Modifier.width(68.dp).clickable(onClick = click), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = if (ativo) Color(0xFF4C5360) else Color.Transparent, shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.width(52.dp).height(34.dp), contentAlignment = Alignment.Center) { Icone(ico, Color.White, 24.dp) }
        }
        Text(texto, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DialogoMedia(fechar: () -> Unit) = DialogoBase("Media", fechar) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Acao("Anterior", Modifier.weight(1f)) { media("previous") }
        Acao("Play/Pause", Modifier.weight(1f)) { media("playpause") }
        Acao("Próxima", Modifier.weight(1f)) { media("next") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Acao("Vol -", Modifier.weight(1f)) { media("volumedown") }
        Acao("Mudo", Modifier.weight(1f)) { media("mute") }
        Acao("Vol +", Modifier.weight(1f)) { media("volumeup") }
    }
}

@Composable
private fun DialogoPower(permitido: Boolean, fechar: () -> Unit) {
    var confirma by remember { mutableStateOf<String?>(null) }
    DialogoBase("Power", fechar) {
        if (!permitido) Text("Comandos de energia bloqueados pelo PC", color = Perigo)
        else listOf("Bloquear" to "lock", "Tela off" to "monitoroff", "Suspender" to "sleep", "Hibernar" to "hibernate", "Reiniciar" to "restart", "Desligar" to "shutdown").chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.forEach { (n, a) -> Acao(n, Modifier.weight(1f)) { if (a in listOf("sleep", "hibernate", "restart", "shutdown")) confirma = a else power(a) } }
            }
        }
    }
    confirma?.let { acao ->
        AlertDialog(onDismissRequest = { confirma = null }, containerColor = Painel, title = { Text("Confirmar") }, text = { Text("Executar $acao no PC agora?") }, confirmButton = { Button(onClick = { power(acao); confirma = null }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirma = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun DialogoPpt(fechar: () -> Unit) = DialogoBase("PPT", fechar) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Acao("Anterior", Modifier.weight(1f)) { tecla("PPT_PREVIOUS") }; Acao("Próximo", Modifier.weight(1f)) { tecla("PPT_NEXT") } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Acao("Iniciar F5", Modifier.weight(1f)) { tecla("PPT_START") }; Acao("Encerrar", Modifier.weight(1f)) { tecla("PPT_END") } }
}

@Composable
private fun DialogoWeb(fechar: () -> Unit) = DialogoBase("Web", fechar) {
    listOf("Voltar" to "BROWSER_BACK", "Avançar" to "BROWSER_FORWARD", "Atualizar" to "BROWSER_REFRESH", "Nova aba" to "NEW_TAB", "Fechar aba" to "CLOSE_TAB", "Reabrir" to "REOPEN_TAB").chunked(2).forEach { linha ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { linha.forEach { (n, a) -> Acao(n, Modifier.weight(1f)) { tecla(a) } } }
    }
}

@Composable
private fun DialogoTeclado(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    DialogoBase("Teclado", fechar) {
        OutlinedTextField(value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Digite para enviar") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Button(onClick = { if (texto.isNotBlank()) SessaoPcFlow.enviar("texto") { put("texto", texto) }; texto = "" }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Enviar") }
        listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT", "HOME", "END", "F5").chunked(4).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { linha.forEach { t -> OutlinedButton(onClick = { tecla(t) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(t, fontSize = 8.sp) } }; repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) } }
        }
    }
}

@Composable
private fun DialogoClipboard(fechar: () -> Unit) {
    val contexto = LocalContext.current
    val remoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    val clip = remember { contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    LaunchedEffect(Unit) { SessaoPcFlow.solicitarClipboard() }
    DialogoBase("Clipboard", fechar) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 190.dp), color = Fundo2, shape = RoundedCornerShape(12.dp)) { Text(remoto.ifBlank { "Nenhum texto recebido." }, color = Texto2, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) }
        Button(onClick = { clip.setPrimaryClip(ClipData.newPlainText("PCFlow", remoto)) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Copiar do PC") }
        OutlinedButton(onClick = { val t = clip.primaryClip?.getItemAt(0)?.coerceToText(contexto)?.toString().orEmpty(); if (t.isNotBlank()) SessaoPcFlow.enviarClipboard(t) }, modifier = Modifier.fillMaxWidth()) { Text("Enviar clipboard do celular") }
    }
}

@Composable
private fun RemoteDesktop(estado: EstadoSessao, fechar: () -> Unit) {
    Dialog(
        onDismissRequest = fechar,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            TelaRemotaPcFlow(estado, fechar)
        }
    }
}

@Composable
private fun Side(t: String, cor: Color = Color.White, click: () -> Unit) { Box(Modifier.size(48.dp).clickable(onClick = click), contentAlignment = Alignment.Center) { Text(t, color = cor, fontSize = 16.sp) } }

@Composable
private fun DialogoBase(titulo: String, fechar: () -> Unit, conteudo: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = fechar) {
        Surface(color = Painel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(titulo, fontSize = 20.sp, modifier = Modifier.weight(1f)); TextButton(onClick = fechar) { Text("Fechar", color = Texto2) } }
                Spacer(Modifier.height(8.dp)); conteudo()
            }
        }
    }
}

@Composable
private fun Acao(texto: String, modifier: Modifier = Modifier, click: () -> Unit) { OutlinedButton(onClick = click, modifier = modifier.padding(vertical = 3.dp), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(14.dp)) { Text(texto, fontSize = 10.sp) } }

private fun mouseClique(botao: String) = SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }
private fun media(a: String) = SessaoPcFlow.enviar("media") { put("acao", a) }
private fun power(a: String) = SessaoPcFlow.enviar("power") { put("acao", a) }
private fun tecla(t: String) = SessaoPcFlow.enviar("tecla") { put("tecla", t) }

private fun lerQrPcFlow(texto: String): Pair<PcEncontrado, String?>? {
    return try {
        val uri = Uri.parse(texto)
        if (uri.scheme != "pcflow" || uri.host != "connect") return null
        val host = uri.getQueryParameter("host") ?: return null
        val porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456
        val id = uri.getQueryParameter("id") ?: ""
        val tls = uri.getQueryParameter("tls") ?: return null
        val pin = uri.getQueryParameter("pin")
        PcEncontrado(nome = "PCFlow $id", host = host, porta = porta, portaTela = 45457, portaArquivos = 45458, maquinaId = id, tls = tls) to pin
    } catch (_: Exception) { null }
}

private fun formatarId(id: String): String = if (id.length == 9) "${id.substring(0,3)} ${id.substring(3,6)} ${id.substring(6,9)}" else id

@Composable
private fun Icone(tipo: Ico, cor: Color, tamanho: Dp) {
    Canvas(Modifier.size(tamanho)) {
        val w = size.width; val h = size.height; val s = maxOf(1.4f, w * .055f)
        when (tipo) {
            Ico.MONITOR -> {
                drawRoundRect(cor, Offset(w*.12f,h*.14f), androidx.compose.ui.geometry.Size(w*.76f,h*.56f), androidx.compose.ui.geometry.CornerRadius(w*.07f), style=Stroke(s))
                drawLine(cor,Offset(w*.5f,h*.70f),Offset(w*.5f,h*.84f),s); drawLine(cor,Offset(w*.30f,h*.84f),Offset(w*.70f,h*.84f),s)
            }
            Ico.MOUSE -> { drawRoundRect(cor,Offset(w*.27f,h*.10f),androidx.compose.ui.geometry.Size(w*.46f,h*.78f),androidx.compose.ui.geometry.CornerRadius(w*.22f),style=Stroke(s)); drawLine(cor,Offset(w*.5f,h*.10f),Offset(w*.5f,h*.36f),s) }
            Ico.GAME -> {
                val p=Path().apply{moveTo(w*.18f,h*.62f);quadraticBezierTo(w*.22f,h*.30f,w*.42f,h*.38f);lineTo(w*.58f,h*.38f);quadraticBezierTo(w*.78f,h*.30f,w*.82f,h*.62f);quadraticBezierTo(w*.84f,h*.82f,w*.68f,h*.70f);lineTo(w*.58f,h*.60f);lineTo(w*.42f,h*.60f);lineTo(w*.32f,h*.70f);quadraticBezierTo(w*.16f,h*.82f,w*.18f,h*.62f)}; drawPath(p,cor,style=Stroke(s)); drawLine(cor,Offset(w*.31f,h*.46f),Offset(w*.31f,h*.61f),s); drawLine(cor,Offset(w*.24f,h*.535f),Offset(w*.38f,h*.535f),s)
            }
            Ico.GRID -> listOf(.14f to .14f,.59f to .14f,.14f to .59f,.59f to .59f).forEach{(x,y)->drawRoundRect(cor,Offset(w*x,h*y),androidx.compose.ui.geometry.Size(w*.27f,h*.27f),androidx.compose.ui.geometry.CornerRadius(w*.04f),style=Stroke(s))}
            Ico.TOOLS -> { drawCircle(cor,w*.19f,Offset(w*.35f,h*.35f),style=Stroke(s)); drawLine(cor,Offset(w*.49f,h*.49f),Offset(w*.82f,h*.82f),s*1.2f) }
            Ico.MORE -> { drawCircle(cor,w*.055f,Offset(w*.5f,h*.25f));drawCircle(cor,w*.055f,Offset(w*.5f,h*.5f));drawCircle(cor,w*.055f,Offset(w*.5f,h*.75f)) }
            Ico.PLAY -> { val p=Path().apply{moveTo(w*.30f,h*.20f);lineTo(w*.78f,h*.50f);lineTo(w*.30f,h*.80f);close()};drawPath(p,cor,style=Stroke(s)) }
            Ico.POWER -> { drawArc(cor,-55f,290f,false,Offset(w*.14f,h*.14f),androidx.compose.ui.geometry.Size(w*.72f,h*.72f),style=Stroke(s));drawLine(cor,Offset(w*.5f,h*.06f),Offset(w*.5f,h*.45f),s) }
            Ico.PPT, Ico.FILE -> { drawRoundRect(cor,Offset(w*.20f,h*.12f),androidx.compose.ui.geometry.Size(w*.60f,h*.76f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s));drawLine(cor,Offset(w*.32f,h*.40f),Offset(w*.68f,h*.40f),s);drawLine(cor,Offset(w*.32f,h*.55f),Offset(w*.60f,h*.55f),s) }
            Ico.WEB -> { drawCircle(cor,w*.36f,Offset(w*.5f,h*.5f),style=Stroke(s));drawOval(cor,Offset(w*.36f,h*.14f),androidx.compose.ui.geometry.Size(w*.28f,h*.72f),style=Stroke(s));drawLine(cor,Offset(w*.14f,h*.5f),Offset(w*.86f,h*.5f),s) }
            Ico.KEYBOARD -> { drawRoundRect(cor,Offset(w*.10f,h*.24f),androidx.compose.ui.geometry.Size(w*.80f,h*.52f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s));for(r in 0..2)for(c in 0..4)drawCircle(cor,w*.018f,Offset(w*(.22f+c*.14f),h*(.34f+r*.13f))) }
            Ico.CLIP -> { drawRoundRect(cor,Offset(w*.22f,h*.20f),androidx.compose.ui.geometry.Size(w*.56f,h*.68f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s));drawRoundRect(cor,Offset(w*.36f,h*.10f),androidx.compose.ui.geometry.Size(w*.28f,h*.20f),androidx.compose.ui.geometry.CornerRadius(w*.04f),style=Stroke(s)) }
            Ico.CAMERA -> { drawRoundRect(cor,Offset(w*.12f,h*.28f),androidx.compose.ui.geometry.Size(w*.68f,h*.48f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=Stroke(s));drawCircle(cor,w*.12f,Offset(w*.46f,h*.52f),style=Stroke(s)) }
            Ico.TASK -> { drawRoundRect(cor,Offset(w*.12f,h*.16f),androidx.compose.ui.geometry.Size(w*.76f,h*.68f),androidx.compose.ui.geometry.CornerRadius(w*.06f),style=Stroke(s));drawLine(cor,Offset(w*.25f,h*.62f),Offset(w*.42f,h*.44f),s);drawLine(cor,Offset(w*.42f,h*.44f),Offset(w*.58f,h*.58f),s);drawLine(cor,Offset(w*.58f,h*.58f),Offset(w*.74f,h*.30f),s) }
            Ico.PHONE -> { drawRoundRect(cor,Offset(w*.30f,h*.08f),androidx.compose.ui.geometry.Size(w*.40f,h*.84f),androidx.compose.ui.geometry.CornerRadius(w*.09f),style=Stroke(s));drawCircle(cor,w*.025f,Offset(w*.5f,h*.83f)) }
        }
    }
}
