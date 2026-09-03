package com.ander.pcflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val Fundo = Color(0xFF0F1318)
private val Painel = Color(0xFF171C23)
private val Borda = Color(0xFF343B45)
private val Dourado = Color(0xFFF2AA2E)
private val Turquesa = Color(0xFF16D3C6)
private val TextoSecundario = Color(0xFF9AA2AC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessaoPcFlow.inicializar(this)
        setContent { TemaPcFlow { AppPcFlow() } }
    }
}

@Composable
private fun TemaPcFlow(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Fundo,
            surface = Painel,
            primary = Dourado,
            secondary = Turquesa,
            onBackground = Color(0xFFF4F6F8),
            onSurface = Color(0xFFF4F6F8)
        ),
        content = content
    )
}

@Composable
private fun AppPcFlow() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var pcParaConectar by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    val permissaoNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val conteudo = resultado.contents ?: return@rememberLauncherForActivityResult
        val lido = lerQrPcFlow(conteudo) ?: return@rememberLauncherForActivityResult
        pcParaConectar = lido.first
        pin = lido.second.orEmpty()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissaoNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    Surface(Modifier.fillMaxSize(), color = Fundo) {
        if (estado.estado == EstadoConexao.CONECTADO && estado.pc != null) {
            TelaRemota(estado)
        } else {
            TelaConectar(
                pcs = pcs,
                estado = estado,
                conectar = { pc -> pcParaConectar = pc; pin = ""; senha = "" },
                atualizar = SessaoPcFlow::descobrir,
                escanearQr = {
                    val opcoes = ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Aponte para o QR Code exibido no PCFlow do computador")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                    scanner.launch(opcoes)
                }
            )
        }
    }

    if (pcParaConectar != null) {
        AlertDialog(
            onDismissRequest = { pcParaConectar = null },
            containerColor = Painel,
            title = { Text("Conectar a ${pcParaConectar!!.nome}") },
            text = {
                Column {
                    Text("O computador exibirá uma solicitação para aceitar. Se o PC tiver acesso não supervisionado, informe a senha para entrar sem confirmação.", color = TextoSecundario)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        label = { Text("Código de pareamento (opcional)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it },
                        label = { Text("Senha não supervisionada (opcional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    SessaoPcFlow.conectar(pcParaConectar!!, pin.ifBlank { null }, senha.ifBlank { null })
                    pcParaConectar = null
                    pin = ""
                    senha = ""
                }) { Text("Solicitar acesso") }
            },
            dismissButton = { TextButton(onClick = { pcParaConectar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TelaConectar(
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    conectar: (PcEncontrado) -> Unit,
    atualizar: () -> Unit,
    escanearQr: () -> Unit
) {
    var buscaId by remember { mutableStateOf("") }
    val idLimpo = buscaId.filter(Char::isDigit)
    val exibidos = if (idLimpo.isBlank()) pcs else pcs.filter { it.maquinaId.contains(idLimpo) }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconeMonitor()
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PCFlow", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Acesso remoto seguro na sua rede", color = TextoSecundario, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = buscaId,
            onValueChange = { buscaId = it.filter(Char::isDigit).take(9) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ID do computador") },
            placeholder = { Text("000 000 000") },
            singleLine = true
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = atualizar, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Borda)) { Text("Buscar na rede", color = Dourado) }
            Button(onClick = escanearQr, modifier = Modifier.weight(1f)) { Text("Escanear QR") }
        }

        Spacer(Modifier.height(24.dp))
        Text("Computadores encontrados", fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        if (exibidos.isEmpty()) {
            BorderCard {
                Text(if (idLimpo.isBlank()) "Procurando computadores PCFlow…" else "Esse ID ainda não apareceu nesta rede.", color = TextoSecundario)
                Spacer(Modifier.height(7.dp))
                Text("O PC precisa estar ligado, com o PCFlow ativo e na mesma rede local.", color = TextoSecundario, fontSize = 12.sp)
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                exibidos.forEach { pc ->
                    BorderCard(Modifier.clickable { conectar(pc) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconeMonitor()
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pc.nome, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(formatarId(pc.maquinaId), color = Dourado, fontSize = 14.sp)
                                Text("${pc.host} · ${pc.monitores} monitor(es)", color = TextoSecundario, fontSize = 11.sp)
                            }
                            Text("Conectar", color = Dourado, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
        }

        if (estado.estado == EstadoConexao.CONECTANDO) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(estado.mensagem, color = Dourado, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
        }
        if (estado.estado == EstadoConexao.ERRO) {
            Text(estado.mensagem, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("TLS com pinagem · conexão restrita à LAN", color = TextoSecundario, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun TelaRemota(estado: EstadoSessao) {
    val pc = requireNotNull(estado.pc)
    val quadro by SessaoPcFlow.quadro.collectAsStateWithLifecycle()
    val monitorAtual by SessaoPcFlow.monitorAtual.collectAsStateWithLifecycle()
    val clipboardRemoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var tamanhoArea by remember { mutableStateOf(IntSize.Zero) }
    var dialogo by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = quadro
        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { tamanhoArea = it }
                .pointerInput(bitmap, tamanhoArea, monitorAtual, estado.permissoes.entrada) {
                    if (bitmap == null || !estado.permissoes.entrada) return@pointerInput
                    detectTapGestures(
                        onTap = { pos -> enviarPosicao(pos, tamanhoArea, bitmap.width, bitmap.height, monitorAtual, "left") },
                        onDoubleTap = { pos ->
                            val p = mapear(pos, tamanhoArea, bitmap.width, bitmap.height) ?: return@detectTapGestures
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitorAtual) }
                            repeat(2) { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } }
                        },
                        onLongPress = { pos -> enviarPosicao(pos, tamanhoArea, bitmap.width, bitmap.height, monitorAtual, "right") }
                    )
                }
                .pointerInput(bitmap, tamanhoArea, monitorAtual, estado.permissoes.entrada) {
                    if (bitmap == null || !estado.permissoes.entrada) return@pointerInput
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = mapear(pos, tamanhoArea, bitmap.width, bitmap.height) ?: return@detectDragGestures
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitorAtual) }
                            SessaoPcFlow.enviar("mouse_down") { put("botao", "left") }
                        },
                        onDragEnd = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") } },
                        onDragCancel = { SessaoPcFlow.enviar("mouse_up") { put("botao", "left") } },
                        onDrag = { change, _ ->
                            val p = mapear(change.position, tamanhoArea, bitmap.width, bitmap.height) ?: return@detectDragGestures
                            change.consume()
                            SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitorAtual) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Tela remota", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Dourado)
                    Text("Recebendo tela…", color = TextoSecundario, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            color = Color(0xD9161B22),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Borda)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Turquesa, RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Column {
                    Text(pc.nome, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${formatarId(pc.maquinaId)} · Monitor ${monitorAtual + 1}/${estado.quantidadeMonitores}", color = TextoSecundario, fontSize = 10.sp)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
                .background(Color(0xE5161B22), RoundedCornerShape(24.dp))
                .border(1.dp, Borda, RoundedCornerShape(24.dp)).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Ferramenta("Teclado") { dialogo = "teclado" }
            Ferramenta("Monitor") {
                val proximo = (monitorAtual + 1) % estado.quantidadeMonitores.coerceAtLeast(1)
                SessaoPcFlow.alterarMonitor(proximo)
            }
            Ferramenta("Clipboard") { SessaoPcFlow.solicitarClipboard(); dialogo = "clipboard" }
            if (estado.permissoes.arquivos) Ferramenta("Arquivos") { dialogo = "arquivos" }
            Ferramenta("Scroll +") { SessaoPcFlow.enviar("scroll") { put("delta", 240) } }
            Ferramenta("Scroll -") { SessaoPcFlow.enviar("scroll") { put("delta", -240) } }
            Ferramenta("Comandos") { dialogo = "comandos" }
            Ferramenta("Sair", destaque = true) { SessaoPcFlow.desconectar() }
        }

        if (!estado.permissoes.entrada) {
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), color = Color(0xDD3A2222), shape = RoundedCornerShape(15.dp)) {
                Text("Sessão em modo somente visualização", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp)
            }
        }
    }

    when (dialogo) {
        "teclado" -> DialogoTeclado { dialogo = null }
        "comandos" -> DialogoComandos(estado.permissoes.energia) { dialogo = null }
        "clipboard" -> DialogoClipboard(contexto, clipboardRemoto) { dialogo = null }
        "arquivos" -> DialogoArquivosRemotos { dialogo = null }
    }
}

@Composable
private fun Ferramenta(texto: String, destaque: Boolean = false, acao: () -> Unit) {
    TextButton(onClick = acao, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp)) {
        Text(texto, color = if (destaque) Color(0xFFFF8A65) else Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun DialogoTeclado(fechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    Dialog(onDismissRequest = fechar) {
        Surface(color = Painel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(18.dp)) {
                Text("Teclado remoto", fontSize = 20.sp)
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    label = { Text("Texto") }
                )
                Button(onClick = {
                    if (texto.isNotEmpty()) SessaoPcFlow.enviar("texto") { put("texto", texto) }
                    texto = ""
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Enviar texto") }
                val teclas = listOf("ESC", "TAB", "ENTER", "BACKSPACE", "DELETE", "LEFT", "UP", "DOWN", "RIGHT")
                teclas.chunked(3).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        linha.forEach { tecla ->
                            OutlinedButton(onClick = { SessaoPcFlow.enviar("tecla") { put("tecla", tecla) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text(tecla, fontSize = 10.sp) }
                        }
                        repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                TextButton(onClick = fechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun DialogoComandos(energiaPermitida: Boolean, fechar: () -> Unit) {
    Dialog(onDismissRequest = fechar) {
        Surface(color = Painel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(18.dp)) {
                Text("Comandos", fontSize = 20.sp)
                Text("Mídia", color = TextoSecundario, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                listOf("Anterior" to "previous", "Play / Pause" to "playpause", "Próxima" to "next", "Volume -" to "volumedown", "Mudo" to "mute", "Volume +" to "volumeup").chunked(3).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        linha.forEach { (rotulo, acao) -> OutlinedButton(onClick = { SessaoPcFlow.enviar("media") { put("acao", acao) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)) { Text(rotulo, fontSize = 9.sp) } }
                    }
                }
                if (energiaPermitida) {
                    Text("Sistema", color = TextoSecundario, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    listOf("Bloquear" to "lock", "Suspender" to "sleep", "Reiniciar" to "restart", "Desligar" to "shutdown").chunked(2).forEach { linha ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            linha.forEach { (rotulo, acao) -> OutlinedButton(onClick = { SessaoPcFlow.enviar("power") { put("acao", acao) } }, modifier = Modifier.weight(1f)) { Text(rotulo, fontSize = 10.sp) } }
                        }
                    }
                }
                TextButton(onClick = fechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun DialogoClipboard(contexto: Context, remoto: String, fechar: () -> Unit) {
    val clipboard = remember { contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    Dialog(onDismissRequest = fechar) {
        Surface(color = Painel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Borda)) {
            Column(Modifier.padding(18.dp)) {
                Text("Área de transferência", fontSize = 20.sp)
                Text(remoto.ifBlank { "Nenhum texto recebido ainda." }, color = TextoSecundario, modifier = Modifier.padding(vertical = 12.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                Button(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("PCFlow", remoto))
                }, modifier = Modifier.fillMaxWidth()) { Text("Copiar do PC para o celular") }
                OutlinedButton(onClick = {
                    val texto = clipboard.primaryClip?.getItemAt(0)?.coerceToText(contexto)?.toString().orEmpty()
                    if (texto.isNotEmpty()) SessaoPcFlow.enviarClipboard(texto)
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Enviar clipboard do celular ao PC") }
                TextButton(onClick = fechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

private fun enviarPosicao(pos: Offset, area: IntSize, largura: Int, altura: Int, monitor: Int, botao: String) {
    val p = mapear(pos, area, largura, altura) ?: return
    SessaoPcFlow.enviar("mouse_abs") { put("x", p.first); put("y", p.second); put("monitor", monitor) }
    SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }
}

private fun mapear(pos: Offset, area: IntSize, larguraImagem: Int, alturaImagem: Int): Pair<Double, Double>? {
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
private fun BorderCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Painel, RoundedCornerShape(18.dp)).border(1.dp, Borda, RoundedCornerShape(18.dp)).padding(17.dp), content = content)
}

@Composable
private fun IconeMonitor() {
    Canvas(Modifier.size(42.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(color = Dourado, topLeft = Offset(size.width * .12f, size.height * .12f), size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(Dourado, Offset(size.width * .5f, size.height * .68f), Offset(size.width * .5f, size.height * .82f), stroke)
        drawLine(Dourado, Offset(size.width * .30f, size.height * .84f), Offset(size.width * .70f, size.height * .84f), stroke)
    }
}
