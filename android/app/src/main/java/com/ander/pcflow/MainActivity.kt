package com.ander.pcflow

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

private val Fundo = Color(0xFF10141A)
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
            background = Fundo, surface = Painel, primary = Dourado,
            secondary = Turquesa, onBackground = Color(0xFFF4F6F8), onSurface = Color(0xFFF4F6F8)
        ), content = content
    )
}

@Composable
private fun AppPcFlow() {
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    var pcParaParear by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }

    val permissaoNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissaoNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Fundo) {
        if (estado.estado == EstadoConexao.CONECTADO && estado.pc != null) {
            TelaControle(estado.pc!!)
        } else {
            TelaConectar(pcs, estado,
                conectar = { pc ->
                    if (SessaoPcFlow.jaPareado(pc)) SessaoPcFlow.conectar(pc)
                    else pcParaParear = pc
                },
                atualizar = SessaoPcFlow::descobrir
            )
        }
    }

    if (pcParaParear != null) {
        AlertDialog(
            onDismissRequest = { pcParaParear = null },
            containerColor = Painel,
            title = { Text("Conectar a ${pcParaParear!!.nome}") },
            text = {
                Column {
                    Text("Se este celular já foi autorizado, deixe o código vazio. No primeiro acesso, digite o PIN exibido no PC.", color = TextoSecundario)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, label = { Text("Código de 6 dígitos") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    SessaoPcFlow.conectar(pcParaParear!!, pin.ifBlank { null })
                    pcParaParear = null
                    pin = ""
                }) { Text("Conectar") }
            },
            dismissButton = { TextButton(onClick = { pcParaParear = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TelaConectar(pcs: List<PcEncontrado>, estado: EstadoSessao, conectar: (PcEncontrado) -> Unit, atualizar: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("PCFlow", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("Controle seu computador pela rede local", color = TextoSecundario, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Computadores disponíveis", fontSize = 18.sp)
            TextButton(onClick = atualizar) { Text("Atualizar", color = Dourado) }
        }
        Spacer(Modifier.height(8.dp))
        if (pcs.isEmpty()) {
            BorderCard {
                Text("Procurando PCFlow na sua rede…", color = TextoSecundario)
                Spacer(Modifier.height(8.dp))
                Text("Deixe o aplicativo do Windows aberto ou minimizado na bandeja.", color = TextoSecundario, fontSize = 13.sp)
            }
        } else {
            pcs.forEach { pc ->
                BorderCard(Modifier.clickable { conectar(pc) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeMonitor()
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pc.nome, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text("${pc.host}:${pc.porta}", color = TextoSecundario, fontSize = 13.sp)
                        }
                        Text("Conectar", color = Dourado)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (estado.estado == EstadoConexao.ERRO) {
            Text(estado.mensagem, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 18.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("Sem conta · sem nuvem · LAN por padrão", color = TextoSecundario, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun TelaControle(pc: PcEncontrado) {
    var aba by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconeMonitor()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pc.nome, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Turquesa, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text("Conectado", color = Turquesa, fontSize = 13.sp)
                }
            }
            TextButton(onClick = SessaoPcFlow::desconectar) { Text("Sair", color = TextoSecundario) }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.weight(1f)) {
            when (aba) {
                0 -> Touchpad(abrirTeclado = { aba = 1 })
                1 -> TecladoRemoto()
                else -> Comandos()
            }
        }
        Navegacao(aba) { aba = it }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Touchpad(abrirTeclado: () -> Unit) {
    var sensibilidade by remember { mutableFloatStateOf(1.35f) }
    var segurando by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (segurando) SessaoPcFlow.enviar("mouse_up") { put("botao", "left") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TOUCHPAD", fontSize = 13.sp, color = Dourado, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = abrirTeclado) { Text("⌨ Teclado do celular", color = Turquesa) }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(Color(0xFF121D29), RoundedCornerShape(26.dp))
                .border(1.dp, Color(0xFF287E89), RoundedCornerShape(26.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } },
                        onDoubleTap = {
                            repeat(2) { SessaoPcFlow.enviar("mouse_click") { put("botao", "left") } }
                        }
                    )
                }
                .pointerInput(sensibilidade) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        SessaoPcFlow.enviar("mouse_move") {
                            put("x", dragAmount.x * sensibilidade)
                            put("y", dragAmount.y * sensibilidade)
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)) {
                Text("Toque para clicar", color = Turquesa, fontSize = 15.sp)
                Text("Deslize para mover o mouse", color = TextoSecundario, fontSize = 12.sp)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Esquerdo" to "left", "Meio" to "middle", "Direito" to "right").forEach { (nome, botao) ->
                BotaoAcao(nome, Modifier.weight(1f)) {
                    SessaoPcFlow.enviar("mouse_click") { put("botao", botao) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotaoAcao("Rolar ↑", Modifier.weight(1f)) {
                SessaoPcFlow.enviar("scroll") { put("delta", 120) }
            }
            BotaoAcao("Rolar ↓", Modifier.weight(1f)) {
                SessaoPcFlow.enviar("scroll") { put("delta", -120) }
            }
            BotaoAcao("⇠", Modifier.weight(1f)) {
                SessaoPcFlow.enviar("scroll_horizontal") { put("delta", -120) }
            }
            BotaoAcao("⇢", Modifier.weight(1f)) {
                SessaoPcFlow.enviar("scroll_horizontal") { put("delta", 120) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BotaoAcao(if (segurando) "Soltar arraste" else "Segurar e arrastar", Modifier.weight(1f)) {
                segurando = !segurando
                SessaoPcFlow.enviar(if (segurando) "mouse_down" else "mouse_up") {
                    put("botao", "left")
                }
            }
            Text("Velocidade", color = TextoSecundario, fontSize = 11.sp)
            Slider(value = sensibilidade, onValueChange = { sensibilidade = it },
                valueRange = 0.5f..3f, modifier = Modifier.width(112.dp).padding(start = 7.dp))
        }
    }
}

/**
 * Sincroniza apenas a edição confirmada pelo teclado Android.
 * O servidor recebe os comandos na ordem de produção (fila única).
 * A posição remota presume o cursor no fim do texto digitado por esta aba.
 */
private fun enviarDiferencaDigitacao(anterior: String, novo: String) {
    if (anterior == novo) return
    var prefixo = 0
    while (prefixo < minOf(anterior.length, novo.length) &&
        anterior[prefixo] == novo[prefixo]) prefixo++

    var sufixo = 0
    while (sufixo < minOf(anterior.length - prefixo, novo.length - prefixo) &&
        anterior[anterior.length - 1 - sufixo] == novo[novo.length - 1 - sufixo]) sufixo++

    repeat(sufixo) { SessaoPcFlow.enviar("tecla") { put("tecla", "LEFT") } }
    repeat(anterior.length - prefixo - sufixo) {
        SessaoPcFlow.enviar("tecla") { put("tecla", "BACKSPACE") }
    }

    val inserido = novo.substring(prefixo, novo.length - sufixo)
    val trechos = inserido.split("\n")
    trechos.forEachIndexed { indice, trecho ->
        if (trecho.isNotEmpty()) SessaoPcFlow.enviar("texto") { put("texto", trecho) }
        if (indice < trechos.lastIndex) SessaoPcFlow.enviar("tecla") { put("tecla", "ENTER") }
    }
    repeat(sufixo) { SessaoPcFlow.enviar("tecla") { put("tecla", "RIGHT") } }
}

@Composable
private fun TecladoRemoto() {
    var texto by remember { mutableStateOf(TextFieldValue("")) }
    var confirmado by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        foco.requestFocus()
        teclado?.show()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Teclado do celular", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("A digitação aparece na janela ativa do Windows em tempo real.",
            color = TextoSecundario, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        OutlinedTextField(
            value = texto,
            onValueChange = { novo ->
                texto = novo
                if (novo.composition == null) {
                    enviarDiferencaDigitacao(confirmado, novo.text)
                    confirmado = novo.text
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(foco),
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            label = { Text("Digite com o teclado do Android") }
        )
        TextButton(onClick = {
            // Limpa somente o campo local; nao apaga o texto que ja apareceu no PC.
            texto = TextFieldValue("")
            confirmado = ""
            foco.requestFocus()
            teclado?.show()
        }) { Text("Limpar campo (não apaga o PC)", color = Turquesa) }

        Spacer(Modifier.height(8.dp))
        listOf(
            listOf("ESC", "TAB", "ENTER"),
            listOf("BACKSPACE", "DELETE", "HOME"),
            listOf("LEFT", "UP", "RIGHT"),
            listOf("DOWN", "END", "PAGEUP")
        ).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { tecla ->
                    BotaoAcao(tecla, Modifier.weight(1f)) {
                        SessaoPcFlow.enviar("tecla") { put("tecla", tecla) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Comandos() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Mídia", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Anterior" to "previous", "Play / Pause" to "playpause", "Próxima" to "next").forEach { (rotulo, acao) ->
                BotaoAcao(rotulo, Modifier.weight(1f)) { SessaoPcFlow.enviar("media") { put("acao", acao) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Vol -" to "volumedown", "Mudo" to "mute", "Vol +" to "volumeup").forEach { (rotulo, acao) ->
                BotaoAcao(rotulo, Modifier.weight(1f)) { SessaoPcFlow.enviar("media") { put("acao", acao) } }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Energia", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        val energia = listOf("Bloquear" to "lock", "Monitor off" to "monitoroff", "Suspender" to "sleep", "Hibernar" to "hibernate", "Reiniciar" to "restart", "Desligar" to "shutdown")
        energia.chunked(2).forEach { linha ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { (rotulo, acao) -> BotaoAcao(rotulo, Modifier.weight(1f)) { SessaoPcFlow.enviar("power") { put("acao", acao) } } }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Ações de energia são executadas imediatamente pelo Windows. Use com cuidado.", color = TextoSecundario, fontSize = 12.sp)
    }
}

@Composable
private fun Navegacao(aba: Int, selecionar: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF171C23), RoundedCornerShape(28.dp)).border(1.dp, Borda, RoundedCornerShape(28.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Touchpad", "Teclado", "Comandos").forEachIndexed { indice, titulo ->
            TextButton(onClick = { selecionar(indice) }, modifier = Modifier.weight(1f)) {
                Text(titulo, color = if (aba == indice) Dourado else TextoSecundario)
            }
        }
    }
}

@Composable
private fun BotaoAcao(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.padding(vertical = 4.dp), border = BorderStroke(1.dp, Borda), shape = RoundedCornerShape(16.dp)) {
        Text(texto, color = Color(0xFFF4F6F8), fontSize = 12.sp)
    }
}

@Composable
private fun BorderCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Painel, RoundedCornerShape(18.dp)).border(1.dp, Borda, RoundedCornerShape(18.dp)).padding(18.dp), content = content)
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
