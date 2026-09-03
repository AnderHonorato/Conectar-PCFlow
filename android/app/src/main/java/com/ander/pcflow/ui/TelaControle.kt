package com.ander.pcflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.pcflow.rede.EstadoSessao
import com.ander.pcflow.rede.ItemRemoto
import com.ander.pcflow.rede.SessaoPcFlow

@Composable
fun TelaControle(
    modifier: Modifier = Modifier,
    estado: EstadoSessao,
    prefs: Preferencias,
    aplicativos: List<ItemRemoto>,
    arquivos: Pair<String, List<ItemRemoto>>?,
    quadroTela: ImageBitmap?,
    aoDesconectar: () -> Unit
) {
    var aba by remember { mutableIntStateOf(0) }
    var mostrarAjustes by remember { mutableStateOf(false) }
    var ajustes by remember { mutableStateOf(prefs.ajustesTouchpad()) }

    val abasDisponiveis = buildList {
        add("Touchpad")
        add("Teclado")
        add("Comandos")
        if (estado.recursos.arquivos) add("Arquivos")
        if (estado.recursos.tela) add("Tela")
    }
    if (aba >= abasDisponiveis.size) aba = 0

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))

        // Cabeçalho
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconeMonitor(36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    estado.pc?.nome ?: "PC",
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoPrimario
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Turquesa, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (estado.latenciaMs >= 0) "Conectado · ${estado.latenciaMs} ms" else "Conectado",
                        color = Turquesa, fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = { mostrarAjustes = true }) { Text("Ajustes", color = Dourado) }
            TextButton(onClick = aoDesconectar) { Text("Sair", color = TextoSecundario) }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            when (abasDisponiveis.getOrNull(aba)) {
                "Touchpad" -> AreaTouchpadCompleta(ajustes)
                "Teclado" -> AbaTeclado()
                "Comandos" -> AbaComandos(estado, aplicativos)
                "Arquivos" -> AbaArquivos(arquivos)
                "Tela" -> AbaTela(quadroTela, prefs, ajustes)
            }
        }

        Spacer(Modifier.height(10.dp))
        BarraAbas(abasDisponiveis, aba) { aba = it }
        Spacer(Modifier.height(10.dp))
    }

    if (mostrarAjustes) {
        DialogoAjustes(
            prefs = prefs,
            ajustes = ajustes,
            aoAlterar = { ajustes = it },
            aoFechar = { mostrarAjustes = false }
        )
    }
}

@Composable
private fun BarraAbas(abas: List<String>, selecionada: Int, aoSelecionar: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Painel, RoundedCornerShape(22.dp))
            .border(1.dp, Borda, RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        abas.forEachIndexed { indice, titulo ->
            val ativa = indice == selecionada
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(
                        if (ativa) PainelClaro else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { aoSelecionar(indice) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    titulo,
                    color = if (ativa) Dourado else TextoSecundario,
                    fontSize = 12.sp,
                    fontWeight = if (ativa) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Teclado
// ----------------------------------------------------------------------

@Composable
private fun AbaTeclado() {
    var texto by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var win by remember { mutableStateOf(false) }

    fun modificadores(): List<String> = buildList {
        if (ctrl) add("ctrl"); if (alt) add("alt"); if (shift) add("shift"); if (win) add("win")
    }

    fun enviarTecla(t: String) {
        SessaoPcFlow.tecla(t, modificadores())
        ctrl = false; alt = false; shift = false; win = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Texto para digitar no PC") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (texto.isNotEmpty()) SessaoPcFlow.digitar(texto)
                    texto = ""
                },
                modifier = Modifier.weight(1f)
            ) { Text("Enviar texto") }
            BotaoTecla("Enter", Modifier.weight(1f)) { enviarTecla("ENTER") }
        }

        Titulo("Modificadores (ficam presos até a próxima tecla)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoAlternavel("Ctrl", ctrl, Modifier.weight(1f)) { ctrl = !ctrl }
            BotaoAlternavel("Alt", alt, Modifier.weight(1f)) { alt = !alt }
            BotaoAlternavel("Shift", shift, Modifier.weight(1f)) { shift = !shift }
            BotaoAlternavel("Win", win, Modifier.weight(1f)) { win = !win }
        }

        Titulo("Navegação")
        GradeTeclas(
            listOf("ESC", "TAB", "BACKSPACE", "DELETE", "HOME", "END", "PAGEUP", "PAGEDOWN", "SPACE"),
            3
        ) { enviarTecla(it) }

        Titulo("Setas")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            BotaoTecla("↑", Modifier.width(88.dp)) { enviarTecla("UP") }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            BotaoTecla("←", Modifier.width(88.dp)) { enviarTecla("LEFT") }
            Spacer(Modifier.width(8.dp))
            BotaoTecla("↓", Modifier.width(88.dp)) { enviarTecla("DOWN") }
            Spacer(Modifier.width(8.dp))
            BotaoTecla("→", Modifier.width(88.dp)) { enviarTecla("RIGHT") }
        }

        Titulo("Função")
        GradeTeclas((1..12).map { "F$it" }, 4) { enviarTecla(it) }

        Titulo("Atalhos prontos")
        GradeAtalhos(
            listOf(
                "Copiar" to "ctrl+c",
                "Colar" to "ctrl+v",
                "Recortar" to "ctrl+x",
                "Desfazer" to "ctrl+z",
                "Salvar" to "ctrl+s",
                "Selecionar tudo" to "ctrl+a",
                "Alternar janela" to "alt+tab",
                "Área de trabalho" to "win+d",
                "Bloquear" to "win+l",
                "Explorador" to "win+e",
                "Fechar" to "alt+F4",
                "Buscar" to "win+s"
            )
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ----------------------------------------------------------------------
// Comandos
// ----------------------------------------------------------------------

@Composable
private fun AbaComandos(estado: EstadoSessao, aplicativos: List<ItemRemoto>) {
    var confirmar by remember { mutableStateOf<Pair<String, String>?>(null) }
    var textoClipboard by remember { mutableStateOf("") }

    LaunchedEffect(estado.recursos.atalhos) {
        if (estado.recursos.atalhos) SessaoPcFlow.listarAplicativos()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Titulo("Mídia", primeiro = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoTecla("⏮", Modifier.weight(1f)) { SessaoPcFlow.midia("previous") }
            BotaoTecla("⏯", Modifier.weight(1f)) { SessaoPcFlow.midia("playpause") }
            BotaoTecla("⏭", Modifier.weight(1f)) { SessaoPcFlow.midia("next") }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotaoTecla("Vol −", Modifier.weight(1f)) { SessaoPcFlow.midia("volumedown") }
            BotaoTecla("Mudo", Modifier.weight(1f)) { SessaoPcFlow.midia("mute") }
            BotaoTecla("Vol +", Modifier.weight(1f)) { SessaoPcFlow.midia("volumeup") }
        }

        if (estado.recursos.areaTransferencia) {
            Titulo("Área de transferência")
            OutlinedTextField(
                value = textoClipboard,
                onValueChange = { textoClipboard = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Texto") }
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BotaoTecla("Enviar ao PC", Modifier.weight(1f)) {
                    if (textoClipboard.isNotBlank()) SessaoPcFlow.enviarAreaTransferencia(textoClipboard)
                }
                BotaoTecla("Trazer do PC", Modifier.weight(1f)) { SessaoPcFlow.pedirAreaTransferencia() }
            }
        }

        if (estado.recursos.energia) {
            Titulo("Energia")
            GradeAcoes(
                listOf(
                    "Bloquear" to "lock",
                    "Desligar monitor" to "monitoroff",
                    "Suspender" to "sleep",
                    "Hibernar" to "hibernate"
                )
            ) { SessaoPcFlow.energia(it) }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BotaoTecla("Reiniciar", Modifier.weight(1f), Vermelho) {
                    confirmar = "Reiniciar o computador?" to "restart"
                }
                BotaoTecla("Desligar", Modifier.weight(1f), Vermelho) {
                    confirmar = "Desligar o computador?" to "shutdown"
                }
            }
        }

        if (estado.recursos.atalhos) {
            Titulo("Abrir no PC")
            if (aplicativos.isEmpty()) {
                Text("Carregando a lista de programas…", color = TextoSecundario, fontSize = 13.sp)
            } else {
                aplicativos.take(60).forEach { app ->
                    Cartao(
                        Modifier
                            .padding(bottom = 8.dp)
                            .clickable { SessaoPcFlow.abrirAplicativo(app.caminho) }
                    ) {
                        Text(app.nome, color = TextoPrimario, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    confirmar?.let { (pergunta, acao) ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            containerColor = Painel,
            title = { Text("Confirmar", color = TextoPrimario) },
            text = { Text("$pergunta\n\nEsta ação é executada na hora.", color = TextoSecundario) },
            confirmButton = {
                Button(onClick = { SessaoPcFlow.energia(acao); confirmar = null }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = TextoSecundario) }
            }
        )
    }
}

// ----------------------------------------------------------------------
// Arquivos
// ----------------------------------------------------------------------

@Composable
private fun AbaArquivos(arquivos: Pair<String, List<ItemRemoto>>?) {
    LaunchedEffect(Unit) { if (arquivos == null) SessaoPcFlow.listarArquivos(null) }

    Column(Modifier.fillMaxSize()) {
        val caminho = arquivos?.first.orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { SessaoPcFlow.listarArquivos(null) }) {
                Text("Pastas iniciais", color = Dourado, fontSize = 13.sp)
            }
            if (caminho.isNotBlank()) {
                TextButton(onClick = {
                    val pai = caminho.trimEnd('\\', '/').substringBeforeLast('\\', "")
                    SessaoPcFlow.listarArquivos(pai.ifBlank { null })
                }) { Text("Voltar", color = TextoSecundario, fontSize = 13.sp) }
            }
        }
        if (caminho.isNotBlank()) {
            Text(caminho, color = TextoSecundario, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val itens = arquivos?.second.orEmpty()
            if (itens.isEmpty()) {
                Text("Pasta vazia ou ainda carregando…", color = TextoSecundario, fontSize = 13.sp)
            }
            itens.forEach { item ->
                Cartao(
                    Modifier.padding(bottom = 8.dp).clickable {
                        if (item.pasta) SessaoPcFlow.listarArquivos(item.caminho)
                        else SessaoPcFlow.baixarArquivo(item.caminho)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.pasta) "📁" else "📄", fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.nome, color = TextoPrimario, fontSize = 14.sp)
                            if (!item.pasta) {
                                Text(
                                    formatarTamanho(item.tamanho),
                                    color = TextoSecundario, fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            if (item.pasta) "Abrir" else "Baixar",
                            color = Dourado, fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatarTamanho(bytes: Long): String {
    // Locale explícito: em pt-BR o separador decimal é vírgula.
    val idioma = java.util.Locale.getDefault()
    return when {
        bytes >= 1_073_741_824 -> String.format(idioma, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(idioma, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format(idioma, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

// ----------------------------------------------------------------------
// Tela remota
// ----------------------------------------------------------------------

@Composable
private fun AbaTela(quadro: ImageBitmap?, prefs: Preferencias, ajustes: AjustesTouchpad) {
    DisposableEffect(Unit) {
        SessaoPcFlow.iniciarTela(prefs.larguraTela, prefs.qualidadeTela, prefs.fpsTela)
        onDispose { SessaoPcFlow.pararTela() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(14.dp))
                .border(1.dp, Borda, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (quadro != null) {
                Image(
                    bitmap = quadro,
                    contentDescription = "Tela do computador",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Recebendo a imagem do PC…", color = TextoSecundario, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Use o touchpad na aba anterior para mover o ponteiro enquanto vê a tela.",
            color = TextoSecundario, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Touchpad(ajustes, Modifier.height(120.dp).fillMaxWidth(), rodape = "Touchpad")
    }
}

// ----------------------------------------------------------------------
// Ajustes
// ----------------------------------------------------------------------

@Composable
private fun DialogoAjustes(
    prefs: Preferencias,
    ajustes: AjustesTouchpad,
    aoAlterar: (AjustesTouchpad) -> Unit,
    aoFechar: () -> Unit
) {
    var atual by remember { mutableStateOf(ajustes) }
    var qualidade by remember { mutableStateOf(prefs.qualidadeTela.toFloat()) }
    var fps by remember { mutableStateOf(prefs.fpsTela.toFloat()) }

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Painel,
        title = { Text("Ajustes", color = TextoPrimario) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Sensibilidade do ponteiro: ${"%.1f".format(java.util.Locale.getDefault(), atual.sensibilidade)}×",
                    color = TextoPrimario, fontSize = 13.sp)
                Slider(
                    value = atual.sensibilidade,
                    onValueChange = { atual = atual.copy(sensibilidade = it) },
                    valueRange = 0.6f..4f
                )
                Text("Velocidade da rolagem: ${"%.1f".format(java.util.Locale.getDefault(), atual.velocidadeScroll)}×",
                    color = TextoPrimario, fontSize = 13.sp)
                Slider(
                    value = atual.velocidadeScroll,
                    onValueChange = { atual = atual.copy(velocidadeScroll = it) },
                    valueRange = 0.3f..3f
                )
                LinhaSwitch("Inverter rolagem", atual.inverterScroll) {
                    atual = atual.copy(inverterScroll = it)
                }
                LinhaSwitch("Aceleração do ponteiro", atual.aceleracao) {
                    atual = atual.copy(aceleracao = it)
                }
                LinhaSwitch("Vibrar ao tocar", atual.vibrar) { atual = atual.copy(vibrar = it) }

                Spacer(Modifier.height(12.dp))
                Text("Tela remota", color = Dourado, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Qualidade: ${qualidade.toInt()}", color = TextoPrimario, fontSize = 13.sp)
                Slider(value = qualidade, onValueChange = { qualidade = it }, valueRange = 20f..90f)
                Text("Quadros por segundo: ${fps.toInt()}", color = TextoPrimario, fontSize = 13.sp)
                Slider(value = fps, onValueChange = { fps = it }, valueRange = 5f..30f)
            }
        },
        confirmButton = {
            Button(onClick = {
                prefs.sensibilidade = atual.sensibilidade
                prefs.velocidadeScroll = atual.velocidadeScroll
                prefs.inverterScroll = atual.inverterScroll
                prefs.aceleracao = atual.aceleracao
                prefs.vibrar = atual.vibrar
                prefs.qualidadeTela = qualidade.toInt()
                prefs.fpsTela = fps.toInt()
                aoAlterar(atual)
                aoFechar()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar", color = TextoSecundario) } }
    )
}

@Composable
private fun LinhaSwitch(rotulo: String, valor: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, color = TextoPrimario, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = aoMudar)
    }
}

// ----------------------------------------------------------------------
// Peças reutilizadas
// ----------------------------------------------------------------------

@Composable
private fun Titulo(texto: String, primeiro: Boolean = false) {
    Text(
        texto,
        color = Dourado,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = if (primeiro) 0.dp else 20.dp, bottom = 8.dp)
    )
}

@Composable
fun BotaoTecla(
    rotulo: String,
    modifier: Modifier = Modifier,
    cor: androidx.compose.ui.graphics.Color = TextoPrimario,
    aoTocar: () -> Unit
) {
    Box(
        modifier
            .height(48.dp)
            .background(PainelClaro, RoundedCornerShape(14.dp))
            .border(1.dp, Borda, RoundedCornerShape(14.dp))
            .clickable { aoTocar() },
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = cor, fontSize = 13.sp)
    }
}

@Composable
private fun BotaoAlternavel(
    rotulo: String,
    ativo: Boolean,
    modifier: Modifier = Modifier,
    aoTocar: () -> Unit
) {
    Box(
        modifier
            .height(48.dp)
            .background(if (ativo) DouradoFraco else PainelClaro, RoundedCornerShape(14.dp))
            .border(1.dp, if (ativo) Dourado else Borda, RoundedCornerShape(14.dp))
            .clickable { aoTocar() },
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, color = if (ativo) Dourado else TextoPrimario, fontSize = 13.sp)
    }
}

@Composable
private fun GradeTeclas(teclas: List<String>, porLinha: Int, aoTocar: (String) -> Unit) {
    teclas.chunked(porLinha).forEach { linha ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            linha.forEach { t -> BotaoTecla(t, Modifier.weight(1f)) { aoTocar(t) } }
            repeat(porLinha - linha.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun GradeAcoes(itens: List<Pair<String, String>>, aoTocar: (String) -> Unit) {
    itens.chunked(2).forEach { linha ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            linha.forEach { (rotulo, acao) ->
                BotaoTecla(rotulo, Modifier.weight(1f)) { aoTocar(acao) }
            }
            repeat(2 - linha.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun GradeAtalhos(itens: List<Pair<String, String>>) {
    itens.chunked(2).forEach { linha ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            linha.forEach { (rotulo, combo) ->
                BotaoTecla(rotulo, Modifier.weight(1f)) { SessaoPcFlow.atalho(combo) }
            }
            repeat(2 - linha.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
