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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

private val Fundo = Color(0xFF0B0E12)
private val Fundo2 = Color(0xFF0F1319)
private val Painel = Color(0xFF151A22)
private val Painel2 = Color(0xFF1A2029)
private val Borda = Color(0xFF353C47)
private val Ouro = Color(0xFFF2AA2E)
private val OuroSuave = Color(0x33F2AA2E)
private val Ciano = Color(0xFF18D2C6)
private val Texto2 = Color(0xFF9BA3AE)
private val Perigo = Color(0xFFFF776D)

private enum class AbaPrincipal { TOUCHPAD, GAMES, LAYOUTS, UTILITIES, MORE }
private enum class PainelRapido { NONE, MEDIA, POWER, PPT, WEB, KEYBOARD, REMOTE, FILES, CLIPBOARD }
private enum class IconePc { MONITOR, MOUSE, GAME, GRID, TOOLS, MORE, MEDIA, POWER, PPT, WEB, KEYBOARD, REMOTE, FILE, TASK, CAMERA, PHONE, CLIPBOARD, SCREEN, HOME, BACK, PLAY }

@Composable
fun PcFlowApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Fundo,
            surface = Painel,
            primary = Ouro,
            secondary = Ciano,
            onBackground = Color(0xFFF5F7FA),
            onSurface = Color(0xFFF5F7FA)
        )
    ) {
        RootPcFlow()
    }
}

@Composable
private fun RootPcFlow() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var pcSelecionado by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    val permissaoNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val conteudo = resultado.contents ?: return@rememberLauncherForActivityResult
        val lido = lerQrPcFlowNovo(conteudo) ?: return@rememberLauncherForActivityResult
        pcSelecionado = lido.first
        pin = lido.second.orEmpty()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    Surface(Modifier.fillMaxSize(), color = Fundo) {
        if (estado.estado == EstadoConexao.CONECTADO && estado.pc != null) {
            ControlePrincipal(estado)
        } else {
            TelaConexaoNova(
                pcs = pcs,
                estado = estado,
                conectar = { pc -> pcSelecionado = pc; pin = ""; senha = "" },
                atualizar = SessaoPcFlow::descobrir,
                escanear = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Aponte para o QR Code exibido no PCFlow do computador")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }
            )
        }
    }

    if (pcSelecionado != null) {
        DialogoConectar(
            pc = pcSelecionado!!,
            pin = pin,
            senha = senha,
            onPin = { pin = it.filter(Char::isDigit).take(6) },
            onSenha = { senha = it },
            fechar = { pcSelecionado = null },
            conectar = {
                SessaoPcFlow.conectar(pcSelecionado!!, pin.ifBlank { null }, senha.ifBlank { null })
                pcSelecionado = null
                pin = ""
                senha = ""
            }
        )
    }
}

@Composable
private fun TelaConexaoNova(
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    conectar: (PcEncontrado) -> Unit,
    atualizar: () -> Unit,
    escanear: () -> Unit
) {
    var busca by remember { mutableStateOf("") }
    val filtro = busca.filter(Char::isDigit)
    val exibidos = if (filtro.isBlank()) pcs else pcs.filter { it.maquinaId.contains(filtro) }

    Column(Modifier.fillMaxSize().background(Fundo).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconeLinha(IconePc.MONITOR, Ouro, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PCFlow", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Text("Controle seu PC sem complicação", color = Texto2, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Surface(color = Painel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(15.dp)) {
                Text("Conectar a um computador", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it.filter(Char::isDigit).take(9) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    placeholder = { Text("ID do computador · 000 000 000") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = atualizar, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(15.dp)) {
                        Text("Buscar na rede", color = Ouro)
                    }
                    Button(onClick = escanear, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                        Text("Escanear QR")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Computadores encontrados", fontSize = 19.sp, modifier = Modifier.weight(1f))
            Text("${exibidos.size}", color = Texto2, fontSize = 13.sp)
        }

        if (exibidos.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), color = Painel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Borda)) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (filtro.isBlank()) "Procurando na rede local…" else "Esse ID não apareceu nesta rede.")
                    Text("Mantenha o PCFlow do Windows aberto ou na bandeja e confirme que os dois dispositivos estão na mesma rede.", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(exibidos, key = { it.maquinaId.ifBlank { it.host } }) { pc ->
                    ComputadorCard(pc) { conectar(pc) }
                }
            }
        }

        when (estado.estado) {
            EstadoConexao.CONECTANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                Text(estado.mensagem.ifBlank { "Solicitando acesso…" }, color = Ouro, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            EstadoConexao.ERRO -> {
                Surface(Modifier.fillMaxWidth().padding(top = 12.dp), color = Color(0xFF2B181B), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Não foi possível conectar", color = Perigo, fontWeight = FontWeight.Medium)
                        Text(estado.mensagem, color = Color(0xFFFFA29A), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("Se você acabou de atualizar o PCFlow, feche a versão antiga pelo ícone da bandeja do Windows antes de abrir a nova.", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
            else -> Unit
        }

        Text("Rede local · TLS com pinagem · sem conta", color = Texto2, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
    }
}

@Composable
private fun ComputadorCard(pc: PcEncontrado, click: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        color = Painel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Borda)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = OuroSuave, shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { IconeLinha(IconePc.MONITOR, Ouro, 34.dp) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pc.nome, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatarIdNovo(pc.maquinaId), color = Ouro, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${pc.host} · ${pc.monitores} monitor(es)", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("Conectar", color = Ouro, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DialogoConectar(
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
        containerColor = Painel,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Text("Conectar a ${pc.nome}")
                Text(formatarIdNovo(pc.maquinaId), color = Ouro, fontSize = 14.sp)
            }
        },
        text = {
            Column {
                Text("Você pode solicitar acesso e aceitar no PC, usar o código exibido no computador/QR ou informar a senha de acesso não supervisionado.", color = Texto2, fontSize = 12.sp)
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPin,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    label = { Text("Código de 6 dígitos") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = senha,
                    onValueChange = onSenha,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Senha não supervisionada") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("Sem código ou senha, o computador mostrará uma solicitação para aceitar.", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { Button(onClick = conectar) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } }
    )
}

@Composable
private fun ControlePrincipal(estado: EstadoSessao) {
    var aba by remember { mutableStateOf(AbaPrincipal.TOUCHPAD) }
    var painel by remember { mutableStateOf(PainelRapido.NONE) }
    val pc = requireNotNull(estado.pc)

    Column(Modifier.fillMaxSize().background(Fundo)) {
        CabecalhoConectado(pc, estado)
        BarraRapida(
            abrir = { painel = it },
            remotoPermitido = estado.permissoes.tela,
            arquivosPermitidos = estado.permissoes.arquivos
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (aba) {
                AbaPrincipal.TOUCHPAD -> TouchpadPage(estado)
                AbaPrincipal.GAMES -> GamesPage()
                AbaPrincipal.LAYOUTS -> LayoutsPage { destino ->
                    when (destino) {
                        "touchpad" -> aba = AbaPrincipal.TOUCHPAD
                        "ppt" -> painel = PainelRapido.PPT
                        "web" -> painel = PainelRapido.WEB
                        "remote" -> painel = PainelRapido.REMOTE
                    }
                }
                AbaPrincipal.UTILITIES -> UtilitiesPage(estado) { painel = it }
                AbaPrincipal.MORE -> MorePage(estado) { painel = it }
            }
        }

        BarraInferior(aba) { aba = it }
    }

    when (painel) {
        PainelRapido.MEDIA -> DialogoMedia { painel = PainelRapido.NONE }
        PainelRapido.POWER -> DialogoPower(estado.permissoes.energia) { painel = PainelRapido.NONE }
        PainelRapido.PPT -> DialogoPpt { painel = PainelRapido.NONE }
        PainelRapido.WEB -> DialogoWeb { painel = PainelRapido.NONE }
        PainelRapido.KEYBOARD -> DialogoTecladoNovo { painel = PainelRapido.NONE }
        PainelRapido.REMOTE -> RemoteDesktopDialog(estado) { painel = PainelRapido.NONE }
        PainelRapido.FILES -> if (estado.permissoes.arquivos) DialogoArquivosRemotos { painel = PainelRapido.NONE }
        PainelRapido.CLIPBOARD -> DialogoClipboardNovo { painel = PainelRapido.NONE }
        PainelRapido.NONE -> Unit
    }
}

@Composable
private fun CabecalhoConectado(pc: PcEncontrado, estado: EstadoSessao) {
    Row(Modifier.fillMaxWidth().background(Fundo2).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeLinha(IconePc.MONITOR, Ouro, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(pc.nome, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Ciano, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("Conectado · ${formatarIdNovo(pc.maquinaId)}", color = Ciano, fontSize = 11.sp)
            }
        }
        Text("${estado.quantidadeMonitores} tela(s)", color = Texto2, fontSize = 11.sp)
    }
}

@Composable
private fun BarraRapida(abrir: (PainelRapido) -> Unit, remotoPermitido: Boolean, arquivosPermitidos: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF24272D)).horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AtalhoTopo("Mídia", IconePc.MEDIA) { abrir(PainelRapido.MEDIA) }
        AtalhoTopo("Power", IconePc.POWER) { abrir(PainelRapido.POWER) }
        AtalhoTopo("PPT", IconePc.PPT) { abrir(PainelRapido.PPT) }
        AtalhoTopo("Web", IconePc.WEB) { abrir(PainelRapido.WEB) }
        DivisorVertical()
        AtalhoTopo("Teclado", IconePc.KEYBOARD) { abrir(PainelRapido.KEYBOARD) }
        if (remotoPermitido) AtalhoTopo("Tela", IconePc.REMOTE) { abrir(PainelRapido.REMOTE) }
        if (arquivosPermitidos) AtalhoTopo("Arquivos", IconePc.FILE) { abrir(PainelRapido.FILES) }
    }
}

@Composable
private fun AtalhoTopo(nome: String, icone: IconePc, click: () -> Unit) {
    Column(
        Modifier.width(66.dp).clickable(onClick = click).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconeLinha(icone, Color.White, 25.dp)
        Text(nome, fontSize = 10.sp, color = Color(0xFFE7E9EC), modifier = Modifier.padding(top = 3.dp), maxLines = 1)
    }
}

@Composable
private fun DivisorVertical() {
    Box(Modifier.width(1.dp).height(42.dp).background(Color(0xFF3A3E44)).padding(horizontal = 3.dp))
}

@Composable
private fun TouchpadPage(estado: EstadoSessao) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        if (!estado.permissoes.entrada) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF322124), shape = RoundedCornerShape(12.dp)) {
                Text("O PC deixou esta sessão apenas para visualização.", color = Perigo, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
                    .border(1.dp, Color(0xFF3855FF), RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
                    .pointerInput(estado.permissoes.entrada) {
                        if (!estado.permissoes.entrada) return@pointerInput
                        detectTapGestures(
                            onTap = { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } },
                            onDoubleTap = { repeat(2) { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } } },
                            onLongPress = { SessaoPcFlow.enviar("mouse_click") { put("botao", "right") } }
                        )
                    }
                    .pointerInput(estado.permissoes.entrada) {
                        if (!estado.permissoes.entrada) return@pointerInput
                        detectDragGestures { change, drag ->
                            change.consume()
                            SessaoPcFlow.enviar("mouse_move") { put("x", drag.x * 1.35); put("y", drag.y * 1.35) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(150.dp)) {
                    drawCircle(OuroSuave, radius = size.minDimension * .24f)
                    drawCircle(Ouro.copy(alpha = .35f), radius = size.minDimension * .12f)
                    drawCircle(Ouro, radius = size.minDimension * .055f)
                }
                Text("Toque para clicar", color = Color(0xFFC9CDD3), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 66.dp))
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp).background(Color(0xFF050607)),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    MouseBotao("Left", Modifier.weight(1f)) { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } }
                    MouseBotao("Middle", Modifier.width(86.dp)) { SessaoPcFlow.enviar("mouse_click") { put("botao", "middle") } }
                    MouseBotao("Right", Modifier.weight(1f)) { SessaoPcFlow.enviar("mouse_click") { put("botao", "right") } }
                }
            }

            Box(
                Modifier.width(46.dp).fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                    .border(1.dp, Color(0xFF3855FF), RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                    .pointerInput(estado.permissoes.entrada) {
                        if (!estado.permissoes.entrada) return@pointerInput
                        detectDragGestures { change, drag ->
                            change.consume()
                            val delta = (-drag.y * 8f).toInt().coerceIn(-720, 720)
                            if (delta != 0) SessaoPcFlow.enviar("scroll") { put("delta", delta) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(4.dp).height(64.dp).background(Color(0xFF333943), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun MouseBotao(texto: String, modifier: Modifier, click: () -> Unit) {
    Box(modifier.fillMaxHeight().border(.5.dp, Borda).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Text(texto, fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun GamesPage() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Gamepad", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Controle básico por teclado. Os botões enviam teclas reais ao Windows.", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GameKey("W")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GameKey("A")
                    GameKey("S")
                    GameKey("D")
                }
                Text("WASD", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GameKey("ESC", "Y")
                    GameKey("SPACE", "X")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GameKey("ENTER", "B")
                    GameKey("TAB", "A")
                }
                Text("Ações", color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", "ALT_TAB") } }, modifier = Modifier.weight(1f)) { Text("Alt + Tab") }
            OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", "SHOW_DESKTOP") } }, modifier = Modifier.weight(1f)) { Text("Desktop") }
        }
    }
}

@Composable
private fun GameKey(tecla: String, rotulo: String = tecla) {
    Box(
        Modifier.size(62.dp).padding(3.dp).background(Painel2, CircleShape).border(1.dp, Color(0xFF59606B), CircleShape)
            .clickable { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } },
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = Ouro, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LayoutsPage(abrir: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Layouts", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Acesso rápido aos modos de controle.", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        LayoutCard("Touchpad clássico", "Mouse, cliques e scroll", IconePc.MOUSE) { abrir("touchpad") }
        LayoutCard("Apresentação", "PowerPoint / LibreOffice", IconePc.PPT) { abrir("ppt") }
        LayoutCard("Navegação web", "Abas, voltar, avançar e atualizar", IconePc.WEB) { abrir("web") }
        LayoutCard("Área de trabalho remota", "Ver e tocar diretamente na tela do PC", IconePc.REMOTE) { abrir("remote") }
    }
}

@Composable
private fun LayoutCard(titulo: String, subtitulo: String, icone: IconePc, click: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = click), color = Painel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Borda)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeLinha(icone, Ouro, 34.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontSize = 15.sp)
                Text(subtitulo, color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("Abrir", color = Ouro, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UtilitiesPage(estado: EstadoSessao, abrir: (PainelRapido) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Utilities", fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        UtilityRow(
            Utility("Remote Desktop", IconePc.REMOTE, estado.permissoes.tela) { abrir(PainelRapido.REMOTE) },
            Utility("File Explorer", IconePc.FILE, estado.permissoes.arquivos) { abrir(PainelRapido.FILES) },
            Utility("Task Manager", IconePc.TASK, true) { SessaoPcFlow.enviar("tecla") { put("tecla", "TASK_MANAGER") } }
        )
        UtilityRow(
            Utility("Data Cable", IconePc.PHONE, estado.permissoes.arquivos) { abrir(PainelRapido.FILES) },
            Utility("Clipboard", IconePc.CLIPBOARD, estado.permissoes.clipboard) { abrir(PainelRapido.CLIPBOARD) },
            Utility("Explorer", IconePc.FILE, true) { SessaoPcFlow.enviar("tecla") { put("tecla", "WIN_E") } }
        )
        Spacer(Modifier.height(10.dp))
        Text("Em desenvolvimento", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        UtilityRow(
            Utility("Projector", IconePc.SCREEN, false, null),
            Utility("Camera", IconePc.CAMERA, false, null),
            Utility("Virtual Camera", IconePc.CAMERA, false, null)
        )
    }
}

private data class Utility(val nome: String, val icone: IconePc, val ativo: Boolean, val acao: (() -> Unit)?)

@Composable
private fun UtilityRow(vararg itens: Utility) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itens.forEach { item ->
            Surface(
                modifier = Modifier.weight(1f).aspectRatio(.92f).then(if (item.ativo && item.acao != null) Modifier.clickable(onClick = item.acao) else Modifier),
                color = if (item.ativo) Painel else Color(0xFF11151A),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (item.ativo) Borda else Color(0xFF282D34))
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconeLinha(item.icone, if (item.ativo) Color.White else Color(0xFF555B64), 32.dp)
                    Text(item.nome, fontSize = 10.sp, color = if (item.ativo) Color.White else Color(0xFF666D77), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MorePage(estado: EstadoSessao, abrir: (PainelRapido) -> Unit) {
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("Mais", fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Text("Sessão e atalhos do Windows", color = Texto2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        MoreButton("Área de transferência", "Sincronizar texto entre celular e PC") { abrir(PainelRapido.CLIPBOARD) }
        MoreButton("Trocar monitor", "Monitor ${monitor + 1} de ${estado.quantidadeMonitores}") {
            SessaoPcFlow.alterarMonitor((monitor + 1) % estado.quantidadeMonitores.coerceAtLeast(1))
        }
        MoreButton("Mostrar área de trabalho", "Win + D") { SessaoPcFlow.enviar("tecla") { put("tecla", "SHOW_DESKTOP") } }
        MoreButton("Alternar janela", "Alt + Tab") { SessaoPcFlow.enviar("tecla") { put("tecla", "ALT_TAB") } }
        MoreButton("Menu Iniciar", "Abrir o Iniciar do Windows") { SessaoPcFlow.enviar("tecla") { put("tecla", "START_MENU") } }
        MoreButton("Executar", "Win + R") { SessaoPcFlow.enviar("tecla") { put("tecla", "WIN_R") } }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = SessaoPcFlow::desconectar, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFF753E3E)), shape = RoundedCornerShape(16.dp)) {
            Text("Desconectar", color = Perigo)
        }
    }
}

@Composable
private fun MoreButton(titulo: String, subtitulo: String, acao: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = acao), color = Painel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Borda)) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, fontSize = 14.sp)
            Text(subtitulo, color = Texto2, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BarraInferior(aba: AbaPrincipal, selecionar: (AbaPrincipal) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF3A3A3D)).padding(horizontal = 4.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceAround) {
        BottomItem("Touchpad", IconePc.MOUSE, aba == AbaPrincipal.TOUCHPAD) { selecionar(AbaPrincipal.TOUCHPAD) }
        BottomItem("Games", IconePc.GAME, aba == AbaPrincipal.GAMES) { selecionar(AbaPrincipal.GAMES) }
        BottomItem("Layouts", IconePc.GRID, aba == AbaPrincipal.LAYOUTS) { selecionar(AbaPrincipal.LAYOUTS) }
        BottomItem("Utilities", IconePc.TOOLS, aba == AbaPrincipal.UTILITIES) { selecionar(AbaPrincipal.UTILITIES) }
        BottomItem("More", IconePc.MORE, aba == AbaPrincipal.MORE) { selecionar(AbaPrincipal.MORE) }
    }
}

@Composable
private fun BottomItem(nome: String, icone: IconePc, ativo: Boolean, click: () -> Unit) {
    Column(Modifier.width(66.dp).clickable(onClick = click), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = if (ativo) Color(0xFF4B5260) else Color.Transparent, shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.width(52.dp).height(34.dp), contentAlignment = Alignment.Center) {
                IconeLinha(icone, if (ativo) Color.White else Color(0xFFE7E7E7), 25.dp)
            }
        }
        Text(nome, fontSize = 10.sp, color = if (ativo) Color.White else Color(0xFFD3D3D3), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DialogoMedia(fechar: () -> Unit) {
    PainelDialog("Mídia", fechar) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcaoGrande("Anterior", Modifier.weight(1f)) { media("previous") }
            AcaoGrande("Play / Pause", Modifier.weight(1f)) { media("playpause") }
            AcaoGrande("Próxima", Modifier.weight(1f)) { media("next") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcaoGrande("Vol -", Modifier.weight(1f)) { media("volumedown") }
            AcaoGrande("Mudo", Modifier.weight(1f)) { media("mute") }
            AcaoGrande("Vol +", Modifier.weight(1f)) { media("volumeup") }
        }
        OutlinedButton(onClick = { media("stop") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Parar") }
    }
}

@Composable
private fun DialogoPower(permitido: Boolean, fechar: () -> Unit) {
    var confirmar by remember { mutableStateOf<String?>(null) }
    PainelDialog("Energia", fechar) {
        if (!permitido) {
            Text("O computador bloqueou comandos de energia para esta sessão.", color = Perigo)
        } else {
            val acoes = listOf(
                "Bloquear" to "lock",
                "Desligar tela" to "monitoroff",
                "Suspender" to "sleep",
                "Hibernar" to "hibernate",
                "Reiniciar" to "restart",
                "Desligar" to "shutdown"
            )
            acoes.chunked(2).forEach { linha ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    linha.forEach { (nome, acao) ->
                        AcaoGrande(nome, Modifier.weight(1f)) {
                            if (acao in listOf("restart", "shutdown", "hibernate", "sleep")) confirmar = acao else power(acao)
                        }
                    }
                }
            }
        }
    }
    confirmar?.let { acao ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            containerColor = Painel,
            title = { Text("Confirmar comando") },
            text = { Text("Executar '$acao' no computador agora?", color = Texto2) },
            confirmButton = { Button(onClick = { power(acao); confirmar = null }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DialogoPpt(fechar: () -> Unit) {
    PainelDialog("Apresentação", fechar) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcaoGrande("Anterior", Modifier.weight(1f)) { tecla("PPT_PREVIOUS") }
            AcaoGrande("Próximo", Modifier.weight(1f)) { tecla("PPT_NEXT") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcaoGrande("Iniciar F5", Modifier.weight(1f)) { tecla("PPT_START") }
            AcaoGrande("Encerrar", Modifier.weight(1f)) { tecla("PPT_END") }
        }
    }
}

@Composable
private fun DialogoWeb(fechar: () -> Unit) {
    PainelDialog("Navegador", fechar) {
        val acoes = listOf(
            "Voltar" to "BROWSER_BACK",
            "Avançar" to "BROWSER_FORWARD",
            "Atualizar" to "BROWSER_REFRESH",
            "Nova aba" to "NEW_TAB",
            "Fechar aba" to "CLOSE_TAB",
            "Reabrir aba" to "REOPEN_TAB"
        )
        acoes.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { (nome, acao) -> AcaoGrande(nome, Modifier.weight(1f)) { tecla(acao) } }
            }
        }
    }
}

@Composable
private fun DialogoTecladoNovo(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    PainelDialog("Teclado", fechar) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Digite para enviar ao PC") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Button(onClick = {
            if (texto.isNotBlank()) SessaoPcFlow.enviar("texto") { put("texto", texto) }
            texto = ""
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Enviar texto") }
        listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT", "HOME", "END", "F5").chunked(4).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                linha.forEach { t ->
                    OutlinedButton(onClick = { tecla(t) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)) { Text(t, fontSize = 9.sp) }
                }
                repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DialogoClipboardNovo(fechar: () -> Unit) {
    val contexto = LocalContext.current
    val remoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    val clipboard = remember { contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    LaunchedEffect(Unit) { SessaoPcFlow.solicitarClipboard() }
    PainelDialog("Área de transferência", fechar) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 190.dp), color = Fundo2, shape = RoundedCornerShape(12.dp)) {
            Text(remoto.ifBlank { "Nenhum texto recebido do PC." }, color = Texto2, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()))
        }
        Button(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("PCFlow", remoto)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Copiar do PC") }
        OutlinedButton(onClick = {
            val texto = clipboard.primaryClip?.getItemAt(0)?.coerceToText(contexto)?.toString().orEmpty()
            if (texto.isNotBlank()) SessaoPcFlow.enviarClipboard(texto)
        }, modifier = Modifier.fillMaxWidth()) { Text("Enviar clipboard do celular") }
    }
}

@Composable
private fun RemoteDesktopDialog(estado: EstadoSessao, fechar: () -> Unit) {
    Dialog(onDismissRequest = fechar) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            RemoteDesktopView(estado, fechar)
        }
    }
}

@Composable
private fun RemoteDesktopView(estado: EstadoSessao, fechar: () -> Unit) {
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitor by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    var area by remember { mutableStateOf(IntSize.Zero) }
    val pc = requireNotNull(estado.pc)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = quadro
        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { area = it }
                .pointerInput(bitmap, area, monitor, estado.permissoes.entrada) {
                    if (bitmap == null || !estado.permissoes.entrada) return@pointerInput
                    detectTapGestures(
                        onTap = { pos -> enviarPosicaoNova(pos, area, bitmap.width, bitmap.height, monitor, "left") },
                        onDoubleTap = { pos ->
                            val p = mapearNovo(pos, area, bitmap.width, bitmap.height) ?: return@detectTapGestures
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
                            repeat(2) { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } }
                        },
                        onLongPress = { pos -> enviarPosicaoNova(pos, area, bitmap.width, bitmap.height, monitor, "right") }
                    )
                }
                .pointerInput(bitmap, area, monitor, estado.permissoes.entrada) {
                    if (bitmap == null || !estado.permissoes.entrada) return@pointerInput
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = mapearNovo(pos, area, bitmap.width, bitmap.height) ?: return@detectDragGestures
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
                            SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                        },
                        onDragEnd = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") } },
                        onDragCancel = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") } },
                        onDrag = { change, _ ->
                            val p = mapearNovo(change.position, area, bitmap.width, bitmap.height) ?: return@detectDragGestures
                            change.consume()
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Tela do computador", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Ouro)
                    Text("Recebendo tela…", color = Texto2, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        Surface(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), color = Color(0xD9151A22), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Borda)) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Ciano, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${pc.nome} · Monitor ${monitor + 1}/${estado.quantidadeMonitores}", fontSize = 11.sp)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).background(Color(0xE51A2028), RoundedCornerShape(28.dp)).border(1.dp, Borda, RoundedCornerShape(28.dp)).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RadialButton("K") { tecla("TAB") }
            RadialButton("M") { SessaoPcFlow.alterarMonitor((monitor + 1) % estado.quantidadeMonitores.coerceAtLeast(1)) }
            RadialButton("+") { SessaoPcFlow.enviar("scroll") { put("delta", 240) } }
            RadialButton("-") { SessaoPcFlow.enviar("scroll") { put("delta", -240) } }
            RadialButton("X", Perigo) { fechar() }
        }
    }
}

@Composable
private fun RadialButton(texto: String, cor: Color = Color.White, acao: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = acao), contentAlignment = Alignment.Center) {
        Text(texto, color = cor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PainelDialog(titulo: String, fechar: () -> Unit, conteudo: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = fechar) {
        Surface(color = Painel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = fechar) { Text("Fechar", color = Texto2) }
                }
                Spacer(Modifier.height(8.dp))
                conteudo()
            }
        }
    }
}

@Composable
private fun AcaoGrande(texto: String, modifier: Modifier = Modifier, acao: () -> Unit) {
    OutlinedButton(onClick = acao, modifier = modifier.padding(vertical = 4.dp), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Borda)) {
        Text(texto, fontSize = 11.sp)
    }
}

private fun media(acao: String) = SessaoPcFlow.enviar("media") { put("acao", acao) }
private fun power(acao: String) = SessaoPcFlow.enviar("power") { put("acao", acao) }
private fun tecla(tecla: String) = SessaoPcFlow.enviar("tecla") { put("tecla", tecla) }

private fun enviarPosicaoNova(pos: Offset, area: IntSize, largura: Int, altura: Int, monitor: Int, botao: String) {
    val p = mapearNovo(pos, area, largura, altura) ?: return
    SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
    SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }
}

private fun mapearNovo(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int): Pair<Double, Double>? {
    if (area.width <= 0 || area.height <= 0 || larguraImagem <= 0 || alturaImagem <= 0) return null
    val escala = minOf(area.width.toFloat() / larguraImagem, area.height.toFloat() / alturaImagem)
    val larguraRender = larguraImagem * escala
    val alturaRender = alturaImagem * escala
    val esquerda = (area.width - larguraRender) / 2f
    val topo = (area.height - alturaRender) / 2f
    if (pos.x < esquerda || pos.x > esquerda + larguraRender || pos.y < topo || pos.y > topo + alturaRender) return null
    return ((pos.x - esquerda) / larguraRender).coerceIn(0f, 1f).toDouble() to ((pos.y - topo) / alturaRender).coerceIn(0f, 1f).toDouble()
}

private fun lerQrPcFlowNovo(texto: String): Pair<PcEncontrado, String?>? = try {
    val uri = Uri.parse(texto)
    if (uri.scheme != "pcflow" || uri.host != "connect") null else {
        val host = uri.getQueryParameter("host") ?: return null
        val porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456
        val id = uri.getQueryParameter("id") ?: ""
        val tls = uri.getQueryParameter("tls") ?: return null
        val pin = uri.getQueryParameter("pin")
        PcEncontrado(
            nome = "PCFlow $id",
            host = host,
            porta = porta,
            portaTela = 45457,
            portaArquivos = 45458,
            maquinaId = id,
            tls = tls
        ) to pin
    }
} catch (_: Exception) { null }

private fun formatarIdNovo(id: String): String = if (id.length == 9) "${id.substring(0, 3)} ${id.substring(3, 6)} ${id.substring(6, 9)}" else id

@Composable
private fun IconeLinha(tipo: IconePc, cor: Color, tamanho: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(tamanho)) {
        val w = size.width
        val h = size.height
        val s = maxOf(1.5f, w * .055f)
        when (tipo) {
            IconePc.MONITOR, IconePc.REMOTE, IconePc.SCREEN -> {
                drawRoundRect(cor, Offset(w * .12f, h * .15f), androidx.compose.ui.geometry.Size(w * .76f, h * .55f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(s))
                drawLine(cor, Offset(w * .5f, h * .70f), Offset(w * .5f, h * .84f), s)
                drawLine(cor, Offset(w * .30f, h * .84f), Offset(w * .70f, h * .84f), s)
            }
            IconePc.MOUSE -> {
                drawRoundRect(cor, Offset(w * .27f, h * .10f), androidx.compose.ui.geometry.Size(w * .46f, h * .78f), androidx.compose.ui.geometry.CornerRadius(w * .22f), style = Stroke(s))
                drawLine(cor, Offset(w * .5f, h * .10f), Offset(w * .5f, h * .35f), s)
            }
            IconePc.GAME -> {
                val p = Path().apply { moveTo(w*.18f,h*.62f); quadraticBezierTo(w*.22f,h*.30f,w*.42f,h*.38f); lineTo(w*.58f,h*.38f); quadraticBezierTo(w*.78f,h*.30f,w*.82f,h*.62f); quadraticBezierTo(w*.84f,h*.82f,w*.68f,h*.70f); lineTo(w*.58f,h*.60f); lineTo(w*.42f,h*.60f); lineTo(w*.32f,h*.70f); quadraticBezierTo(w*.16f,h*.82f,w*.18f,h*.62f) }
                drawPath(p, cor, style = Stroke(s))
                drawLine(cor, Offset(w*.31f,h*.46f), Offset(w*.31f,h*.61f), s)
                drawLine(cor, Offset(w*.24f,h*.535f), Offset(w*.38f,h*.535f), s)
                drawCircle(cor, w*.035f, Offset(w*.67f,h*.49f)); drawCircle(cor, w*.035f, Offset(w*.74f,h*.57f))
            }
            IconePc.GRID -> {
                val q = w * .27f
                listOf(.14f to .14f, .59f to .14f, .14f to .59f, .59f to .59f).forEach { (x,y) -> drawRoundRect(cor, Offset(w*x,h*y), androidx.compose.ui.geometry.Size(q,q), androidx.compose.ui.geometry.CornerRadius(w*.04f), style = Stroke(s)) }
            }
            IconePc.TOOLS -> {
                drawCircle(cor, w*.22f, Offset(w*.36f,h*.36f), style = Stroke(s))
                drawLine(cor, Offset(w*.50f,h*.50f), Offset(w*.82f,h*.82f), s*1.3f)
                drawCircle(cor, w*.07f, Offset(w*.78f,h*.78f), style = Stroke(s))
            }
            IconePc.MORE -> { drawCircle(cor,w*.055f,Offset(w*.5f,h*.25f)); drawCircle(cor,w*.055f,Offset(w*.5f,h*.5f)); drawCircle(cor,w*.055f,Offset(w*.5f,h*.75f)) }
            IconePc.MEDIA, IconePc.PLAY -> {
                val p = Path().apply { moveTo(w*.30f,h*.20f); lineTo(w*.78f,h*.50f); lineTo(w*.30f,h*.80f); close() }
                drawPath(p, cor, style = Stroke(s))
            }
            IconePc.POWER -> { drawArc(cor, -55f, 290f, false, Offset(w*.14f,h*.14f), androidx.compose.ui.geometry.Size(w*.72f,h*.72f), style = Stroke(s)); drawLine(cor, Offset(w*.5f,h*.06f), Offset(w*.5f,h*.45f), s) }
            IconePc.PPT -> { drawRoundRect(cor,Offset(w*.18f,h*.14f),androidx.compose.ui.geometry.Size(w*.64f,h*.72f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s)); drawLine(cor,Offset(w*.32f,h*.35f),Offset(w*.68f,h*.35f),s); drawLine(cor,Offset(w*.32f,h*.50f),Offset(w*.60f,h*.50f),s) }
            IconePc.WEB -> { drawCircle(cor,w*.36f,Offset(w*.5f,h*.5f),style=Stroke(s)); drawOval(cor,Offset(w*.36f,h*.14f),androidx.compose.ui.geometry.Size(w*.28f,h*.72f),style=Stroke(s)); drawLine(cor,Offset(w*.14f,h*.5f),Offset(w*.86f,h*.5f),s) }
            IconePc.KEYBOARD -> { drawRoundRect(cor,Offset(w*.10f,h*.24f),androidx.compose.ui.geometry.Size(w*.80f,h*.52f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s)); for(r in 0..2) for(c in 0..4) drawCircle(cor,w*.018f,Offset(w*(.22f+c*.14f),h*(.34f+r*.13f))) }
            IconePc.FILE -> { val p=Path().apply{moveTo(w*.24f,h*.12f);lineTo(w*.58f,h*.12f);lineTo(w*.78f,h*.32f);lineTo(w*.78f,h*.88f);lineTo(w*.24f,h*.88f);close()};drawPath(p,cor,style=Stroke(s));drawLine(cor,Offset(w*.58f,h*.12f),Offset(w*.58f,h*.33f),s);drawLine(cor,Offset(w*.58f,h*.33f),Offset(w*.78f,h*.33f),s) }
            IconePc.TASK -> { drawRoundRect(cor,Offset(w*.12f,h*.16f),androidx.compose.ui.geometry.Size(w*.76f,h*.68f),androidx.compose.ui.geometry.CornerRadius(w*.06f),style=Stroke(s)); drawLine(cor,Offset(w*.25f,h*.62f),Offset(w*.38f,h*.45f),s);drawLine(cor,Offset(w*.38f,h*.45f),Offset(w*.51f,h*.57f),s);drawLine(cor,Offset(w*.51f,h*.57f),Offset(w*.72f,h*.31f),s) }
            IconePc.CAMERA -> { drawRoundRect(cor,Offset(w*.12f,h*.28f),androidx.compose.ui.geometry.Size(w*.62f,h*.48f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=Stroke(s));drawCircle(cor,w*.12f,Offset(w*.43f,h*.52f),style=Stroke(s));val p=Path().apply{moveTo(w*.74f,h*.42f);lineTo(w*.90f,h*.32f);lineTo(w*.90f,h*.72f);lineTo(w*.74f,h*.62f)};drawPath(p,cor,style=Stroke(s)) }
            IconePc.PHONE -> { drawRoundRect(cor,Offset(w*.30f,h*.08f),androidx.compose.ui.geometry.Size(w*.40f,h*.84f),androidx.compose.ui.geometry.CornerRadius(w*.09f),style=Stroke(s));drawCircle(cor,w*.025f,Offset(w*.5f,h*.83f)) }
            IconePc.CLIPBOARD -> { drawRoundRect(cor,Offset(w*.22f,h*.20f),androidx.compose.ui.geometry.Size(w*.56f,h*.68f),androidx.compose.ui.geometry.CornerRadius(w*.05f),style=Stroke(s));drawRoundRect(cor,Offset(w*.36f,h*.10f),androidx.compose.ui.geometry.Size(w*.28f,h*.20f),androidx.compose.ui.geometry.CornerRadius(w*.04f),style=Stroke(s)) }
            IconePc.HOME -> { val p=Path().apply{moveTo(w*.16f,h*.48f);lineTo(w*.5f,h*.18f);lineTo(w*.84f,h*.48f);moveTo(w*.28f,h*.42f);lineTo(w*.28f,h*.82f);lineTo(w*.72f,h*.82f);lineTo(w*.72f,h*.42f)};drawPath(p,cor,style=Stroke(s)) }
            IconePc.BACK -> { drawLine(cor,Offset(w*.74f,h*.25f),Offset(w*.32f,h*.5f),s);drawLine(cor,Offset(w*.32f,h*.5f),Offset(w*.74f,h*.75f),s) }
        }
    }
}
