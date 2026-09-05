package com.ander.pcflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V13Bg = Color(0xFF080B0F)
private val V13BgElevado = Color(0xFF0E1319)
private val V13Card = Color(0xFF151B22)
private val V13Card2 = Color(0xFF1B242D)
private val V13Border = Color(0xFF303A45)
private val V13Gold = Color(0xFFF3B13F)
private val V13Teal = Color(0xFF24D2C2)
private val V13Text2 = Color(0xFF9EABB8)
private val V13Danger = Color(0xFFFF716A)
private val V13Blue = Color(0xFF5EA8FF)

private enum class AbaMobileV13(val titulo: String, val simbolo: String) {
    INICIO("Início", "⌂"),
    RECENTES("Recentes", "◷"),
    DISPOSITIVOS("Dispositivos", "▣"),
    CONTATOS("Contatos", "♙")
}

@Composable
fun PcFlowV13CompletoApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = V13Bg,
            surface = V13Card,
            primary = V13Gold,
            secondary = V13Teal,
            error = V13Danger,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
        if (estado.estado == EstadoConexao.CONECTADO) {
            // Reaproveita o controlador remoto já validado da V1.3: toque, touchpad,
            // visualização, teclado, clipboard, monitores, arquivos e menu da sessão.
            PcFlowV13App()
        } else {
            ShellMobileV13()
        }
    }
}

@Composable
private fun ShellMobileV13() {
    val contexto = LocalContext.current
    val pcsRede by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val dispositivos by RepositorioPcFlowV13.dispositivos.collectAsStateWithLifecycle()
    val recentes by RepositorioPcFlowV13.recentes.collectAsStateWithLifecycle()
    val contatos by RepositorioPcFlowV13.contatos.collectAsStateWithLifecycle()
    val convites by RepositorioPcFlowV13.convites.collectAsStateWithLifecycle()

    var aba by rememberSaveable { mutableStateOf(AbaMobileV13.INICIO) }
    var pcSelecionado by remember { mutableStateOf<PcEncontrado?>(null) }
    var pin by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var ultimoRegistro by remember { mutableStateOf<String?>(null) }

    val permissaoNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val texto = resultado.contents ?: return@rememberLauncherForActivityResult
        val pc = RepositorioPcFlowV13.lerDeepLink(texto)
        if (pc != null) {
            RepositorioPcFlowV13.absorverDescoberta(listOf(pc))
            pcSelecionado = pc
        } else {
            Toast.makeText(contexto, "QR Code não pertence ao PCFlow.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        RepositorioPcFlowV13.inicializar(contexto)
        if (Build.VERSION.SDK_INT >= 33) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
    }

    LaunchedEffect(pcsRede) {
        RepositorioPcFlowV13.absorverDescoberta(pcsRede)
    }

    LaunchedEffect(estado.estado, estado.pc?.maquinaId, estado.mensagem) {
        val pc = estado.pc ?: return@LaunchedEffect
        val assinatura = "${estado.estado}:${pc.maquinaId}:${estado.mensagem}"
        if (assinatura == ultimoRegistro) return@LaunchedEffect
        when (estado.estado) {
            EstadoConexao.CONECTADO -> RepositorioPcFlowV13.registrarConexao(pc)
            EstadoConexao.ERRO -> RepositorioPcFlowV13.registrarFalha(pc)
            else -> Unit
        }
        ultimoRegistro = assinatura
    }

    Surface(Modifier.fillMaxSize(), color = V13Bg) {
        Column(Modifier.fillMaxSize()) {
            CabecalhoMobileV13(aba)
            AnimatedContent(targetState = aba, label = "trocaAba") { atual ->
                Box(Modifier.weight(1f)) {
                    when (atual) {
                        AbaMobileV13.INICIO -> AbaInicioV13(
                            pcsRede = pcsRede,
                            dispositivos = dispositivos,
                            estado = estado,
                            abrirPc = { pcSelecionado = it },
                            escanear = {
                                scanner.launch(
                                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt("Aponte para o QR Code do PCFlow")
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(false)
                                )
                            }
                        )
                        AbaMobileV13.RECENTES -> AbaRecentesV13(recentes, dispositivos) { pcSelecionado = it }
                        AbaMobileV13.DISPOSITIVOS -> AbaDispositivosV13(pcsRede, dispositivos) { pcSelecionado = it }
                        AbaMobileV13.CONTATOS -> AbaContatosV13(contatos, convites, dispositivos) { pcSelecionado = it }
                    }
                }
            }
            BarraInferiorV13(aba) { aba = it }
        }
    }

    pcSelecionado?.let { pc ->
        DialogoConectarCompletoV13(
            pc = pc,
            pin = pin,
            senha = senha,
            onPin = { pin = it.filter(Char::isDigit).take(6) },
            onSenha = { senha = it },
            fechar = { pcSelecionado = null; pin = ""; senha = "" },
            conectar = {
                SessaoPcFlow.conectar(pc, pin.ifBlank { null }, senha.ifBlank { null })
                RepositorioPcFlowV13.absorverDescoberta(listOf(pc))
                pcSelecionado = null
                pin = ""
                senha = ""
            }
        )
    }
}

@Composable
private fun CabecalhoMobileV13(aba: AbaMobileV13) {
    Surface(color = V13Bg, tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarcaCompactaV13()
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Flow", color = V13Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Surface(color = Color.Transparent, border = BorderStroke(1.dp, V13Gold.copy(alpha = .7f)), shape = RoundedCornerShape(7.dp), modifier = Modifier.padding(start = 7.dp)) {
                        Text("V1.3", color = V13Gold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                Text("Acesso remoto simples e seguro", color = V13Text2, fontSize = 9.sp)
            }
            Text(aba.simbolo, color = V13Gold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun MarcaCompactaV13() {
    Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF202933), border = BorderStroke(1.dp, V13Border), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("↗", color = V13Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
    }
}

@Composable
private fun AbaInicioV13(
    pcsRede: List<PcEncontrado>,
    dispositivos: List<DispositivoSalvoV13>,
    estado: EstadoSessao,
    abrirPc: (PcEncontrado) -> Unit,
    escanear: () -> Unit
) {
    var id by rememberSaveable { mutableStateOf("") }
    val idLimpo = id.filter(Char::isDigit)
    val candidato = pcsRede.firstOrNull { it.maquinaId == idLimpo }
        ?: dispositivos.firstOrNull { it.maquinaId == idLimpo }?.paraPc()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = V13Card, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, V13Border)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Conectar a um computador", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Digite o ID do dispositivo para iniciar a conexão", color = V13Text2, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it.filter(Char::isDigit).take(9) },
                        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                        placeholder = { Text("Digite o ID do dispositivo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = escanear, modifier = Modifier.weight(1f).height(48.dp), border = BorderStroke(1.dp, V13Border), shape = RoundedCornerShape(14.dp)) {
                            Text("▦  Escanear QR", color = Color.White, fontSize = 11.sp)
                        }
                        Button(onClick = { candidato?.let(abrirPc) }, enabled = candidato != null, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                            Text("▣  Conectar", fontSize = 11.sp)
                        }
                    }
                    if (idLimpo.length == 9 && candidato == null) {
                        Text("Esse ID ainda não apareceu nesta rede. Toque em Atualizar e mantenha o PCFlow aberto no computador.", color = V13Danger, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meus dispositivos", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { SessaoPcFlow.descobrir() }) { Text("Atualizar", color = V13Gold, fontSize = 10.sp) }
            }
        }

        val destaque = dispositivos.sortedWith(compareByDescending<DispositivoSalvoV13> { it.favorito }.thenByDescending { it.ultimoVisto }).take(6)
        if (destaque.isEmpty()) {
            item { EstadoVazioV13("Nenhum dispositivo salvo", "Os computadores encontrados na rede aparecerão aqui automaticamente.") }
        } else {
            items(destaque, key = { it.chave }) { dispositivo ->
                CardDispositivoV13(dispositivo, online = pcsRede.any { chavePcV13(it) == dispositivo.chave }, conectar = { abrirPc(dispositivo.paraPc()) })
            }
        }

        if (estado.estado == EstadoConexao.CONECTANDO || estado.estado == EstadoConexao.ERRO) {
            item { EstadoConexaoCardV13(estado) }
        }
    }
}

@Composable
private fun AbaRecentesV13(
    recentes: List<RegistroConexaoV13>,
    dispositivos: List<DispositivoSalvoV13>,
    abrirPc: (PcEncontrado) -> Unit
) {
    var filtro by rememberSaveable { mutableStateOf(0) }
    val agora = System.currentTimeMillis()
    val limite = when (filtro) {
        0 -> agora - 24 * 60 * 60 * 1000L
        1 -> agora - 7 * 24 * 60 * 60 * 1000L
        else -> 0L
    }
    val lista = recentes.filter { it.quando >= limite }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Recentes", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Suas conexões mais recentes, sempre à mão.", color = V13Text2, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FiltroChipV13("Hoje", filtro == 0) { filtro = 0 }
                FiltroChipV13("Esta semana", filtro == 1) { filtro = 1 }
                FiltroChipV13("Todos", filtro == 2) { filtro = 2 }
            }
        }
        if (lista.isEmpty()) item { EstadoVazioV13("Sem conexões nesse período", "Quando você se conectar a um PC, a sessão aparecerá aqui.") }
        items(lista, key = { it.id }) { registro ->
            val d = dispositivos.firstOrNull { it.chave == registro.dispositivoChave }
            Surface(color = V13Card, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V13Border)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniaturaV13(d?.sistema ?: "PC")
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(d?.nome ?: registro.nome, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d?.maquinaId?.let(::formatarIdMobileV13) ?: "Sessão PCFlow", color = V13Text2, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(if (registro.sucesso) V13Teal else V13Danger, CircleShape))
                            Text(if (registro.sucesso) " Conexão concluída" else " Falha na conexão", color = if (registro.sucesso) V13Teal else V13Danger, fontSize = 9.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(tempoRelativoV13(registro.quando), color = V13Text2, fontSize = 9.sp)
                        OutlinedButton(onClick = { d?.paraPc()?.let(abrirPc) }, enabled = d != null, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp), modifier = Modifier.padding(top = 5.dp)) {
                            Text("Reconectar", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbaDispositivosV13(
    pcsRede: List<PcEncontrado>,
    dispositivos: List<DispositivoSalvoV13>,
    abrirPc: (PcEncontrado) -> Unit
) {
    var busca by rememberSaveable { mutableStateOf("") }
    var renomear by remember { mutableStateOf<DispositivoSalvoV13?>(null) }
    val termo = busca.trim().lowercase()
    val filtrados = dispositivos.filter { termo.isBlank() || it.nome.lowercase().contains(termo) || it.maquinaId.contains(termo.filter(Char::isDigit)) }
    val favoritos = filtrados.filter { it.favorito }
    val rede = filtrados.filter { salvo -> pcsRede.any { chavePcV13(it) == salvo.chave } && !salvo.favorito }
    val outros = filtrados.filterNot { it in favoritos || it in rede }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("Dispositivos", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Gerencie e conecte aos seus computadores.", color = V13Text2, fontSize = 11.sp)
            OutlinedTextField(value = busca, onValueChange = { busca = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("Buscar dispositivo, nome ou ID…") }, singleLine = true, shape = RoundedCornerShape(15.dp))
        }
        if (favoritos.isNotEmpty()) item { TituloSecaoV13("★", "Favoritos", favoritos.size) }
        items(favoritos, key = { "fav-${it.chave}" }) { d -> CardGerenciarDispositivoV13(d, pcsRede, abrirPc, { RepositorioPcFlowV13.alternarFavorito(d.chave) }, { renomear = d }) }
        if (rede.isNotEmpty()) item { TituloSecaoV13("⌂", "Na mesma rede", rede.size) }
        items(rede, key = { "rede-${it.chave}" }) { d -> CardGerenciarDispositivoV13(d, pcsRede, abrirPc, { RepositorioPcFlowV13.alternarFavorito(d.chave) }, { renomear = d }) }
        if (outros.isNotEmpty()) item { TituloSecaoV13("☷", "Todos os dispositivos", outros.size) }
        items(outros, key = { "all-${it.chave}" }) { d -> CardGerenciarDispositivoV13(d, pcsRede, abrirPc, { RepositorioPcFlowV13.alternarFavorito(d.chave) }, { renomear = d }) }
        if (filtrados.isEmpty()) item { EstadoVazioV13("Nenhum dispositivo", "Atualize a rede ou conecte usando um QR Code para salvar um computador.") }
    }

    renomear?.let { d ->
        DialogoTextoV13("Renomear dispositivo", d.nome, "Novo nome", { renomear = null }) { novo ->
            RepositorioPcFlowV13.renomearDispositivo(d.chave, novo)
            renomear = null
        }
    }
}

@Composable
private fun AbaContatosV13(
    contatos: List<ContatoV13>,
    convites: List<ConviteV13>,
    dispositivos: List<DispositivoSalvoV13>,
    abrirPc: (PcEncontrado) -> Unit
) {
    val contexto = LocalContext.current
    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    var busca by rememberSaveable { mutableStateOf("") }
    var novoContato by remember { mutableStateOf(false) }
    var chatContato by remember { mutableStateOf<ContatoV13?>(null) }
    var selecionarConvite by remember { mutableStateOf(false) }
    val termo = busca.trim().lowercase()
    val filtrados = contatos.filter { termo.isBlank() || it.nome.lowercase().contains(termo) || it.grupo.lowercase().contains(termo) }
    val grupos = filtrados.groupBy { if (it.favorito) "Favoritos" else it.grupo }.toSortedMap(compareBy<String> { if (it == "Favoritos") 0 else 1 }.thenBy { it })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Contatos", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("Conecte-se com quem importa.", color = V13Text2, fontSize = 11.sp)
                }
                Button(onClick = { selecionarConvite = true }, shape = RoundedCornerShape(14.dp)) { Text("Convidar", fontSize = 10.sp) }
            }
            OutlinedTextField(value = busca, onValueChange = { busca = it }, modifier = Modifier.fillMaxWidth().padding(top = 11.dp), placeholder = { Text("Buscar contatos, dispositivos ou grupos…") }, singleLine = true, shape = RoundedCornerShape(15.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FiltroChipV13("Todos (${filtrados.size})", true) { }
                OutlinedButton(onClick = { novoContato = true }, modifier = Modifier.weight(1f)) { Text("+ Contato", fontSize = 9.sp) }
            }
        }

        grupos.forEach { (grupo, lista) ->
            item { TituloSecaoV13(if (grupo == "Favoritos") "★" else "♙", grupo, lista.size) }
            items(lista, key = { "contato-${it.id}" }) { contato ->
                val dispositivo = contato.dispositivoChave?.let { chave -> dispositivos.firstOrNull { it.chave == chave } }
                CardContatoV13(
                    contato = contato,
                    online = estado.estado == EstadoConexao.CONECTADO && dispositivo?.chave == estado.pc?.let(::chavePcV13),
                    conectar = { dispositivo?.paraPc()?.let(abrirPc) },
                    chat = { chatContato = contato },
                    favorito = { RepositorioPcFlowV13.alternarFavoritoContato(contato.id) }
                )
            }
        }

        if (convites.isNotEmpty()) {
            item { TituloSecaoV13("◷", "Solicitações", convites.size) }
            items(convites, key = { "convite-${it.id}" }) { convite ->
                Surface(color = V13Card, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V13Border)) {
                    Column(Modifier.padding(13.dp)) {
                        Text(convite.remetente, fontWeight = FontWeight.Medium)
                        Text("Compartilhou ${convite.nomeDispositivo} · ${formatarIdMobileV13(convite.maquinaId)}", color = V13Text2, fontSize = 9.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { RepositorioPcFlowV13.rejeitarConvite(convite.id) }) { Text("Recusar", color = V13Text2) }
                            Button(onClick = { RepositorioPcFlowV13.aceitarConvite(convite.id)?.let(abrirPc) }, modifier = Modifier.padding(start = 7.dp)) { Text("Aceitar") }
                        }
                    }
                }
            }
        }

        if (filtrados.isEmpty() && convites.isEmpty()) item { EstadoVazioV13("Nenhum contato", "Adicione um contato a partir de um dispositivo salvo ou compartilhe um convite PCFlow.") }
    }

    if (novoContato) DialogoNovoContatoV13(dispositivos, { novoContato = false }) { nome, chave, grupo ->
        RepositorioPcFlowV13.criarContato(nome, chave, grupo)
        novoContato = false
    }

    if (selecionarConvite) DialogoCompartilharV13(dispositivos, { selecionarConvite = false }) { d ->
        RepositorioPcFlowV13.compartilharDispositivo(contexto, d)
        selecionarConvite = false
    }

    chatContato?.let { contato ->
        DialogoChatV13(contato, estado, { chatContato = null }) { texto ->
            val alvo = contato.dispositivoChave
            val atual = estado.pc?.let(::chavePcV13)
            if (estado.estado == EstadoConexao.CONECTADO && alvo != null && alvo == atual) {
                SessaoPcFlow.enviar("chat") { put("texto", texto) }
                Toast.makeText(contexto, "Mensagem enviada para o PC.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(contexto, "Conecte-se ao dispositivo desse contato antes de enviar a mensagem.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun BarraInferiorV13(aba: AbaMobileV13, selecionar: (AbaMobileV13) -> Unit) {
    Surface(color = Color(0xFF0B0F14), border = BorderStroke(1.dp, Color(0xFF222B34))) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 7.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AbaMobileV13.entries.forEach { item ->
                val ativo = aba == item
                val fundo by animateColorAsState(if (ativo) Color(0x332F2410) else Color.Transparent, label = "fundoNav")
                val escala by animateFloatAsState(if (ativo) 1.04f else 1f, tween(140), label = "escalaNav")
                Column(
                    Modifier.weight(1f).scale(escala).background(fundo, RoundedCornerShape(14.dp)).clickable { selecionar(item) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(item.simbolo, color = if (ativo) V13Gold else V13Text2, fontSize = 18.sp)
                    Text(item.titulo, color = if (ativo) V13Gold else V13Text2, fontSize = 9.sp)
                    AnimatedVisibility(ativo, enter = fadeIn(), exit = fadeOut()) { Box(Modifier.padding(top = 3.dp).width(24.dp).height(2.dp).background(V13Gold, CircleShape)) }
                }
            }
        }
    }
}

@Composable
private fun CardDispositivoV13(dispositivo: DispositivoSalvoV13, online: Boolean, conectar: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = conectar), color = V13Card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V13Border)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniaturaV13(dispositivo.sistema)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(dispositivo.nome, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatarIdMobileV13(dispositivo.maquinaId), color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (online) V13Teal else Color(0xFF708090), CircleShape))
                    Text(if (online) "  Online" else "  Salvo", color = if (online) V13Teal else V13Text2, fontSize = 9.sp)
                }
            }
            IconeFavoritoV13(dispositivo.favorito) { RepositorioPcFlowV13.alternarFavorito(dispositivo.chave) }
        }
    }
}

@Composable
private fun CardGerenciarDispositivoV13(
    d: DispositivoSalvoV13,
    pcsRede: List<PcEncontrado>,
    abrirPc: (PcEncontrado) -> Unit,
    favorito: () -> Unit,
    renomear: () -> Unit
) {
    val online = pcsRede.any { chavePcV13(it) == d.chave }
    Surface(color = V13Card, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V13Border)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniaturaV13(d.sistema)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(d.nome, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatarIdMobileV13(d.maquinaId), color = V13Text2, fontSize = 9.sp)
                    Text("${d.sistema} · ${if (online) "Online" else "Offline"}", color = if (online) V13Teal else V13Text2, fontSize = 9.sp)
                }
                IconeFavoritoV13(d.favorito, favorito)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { abrirPc(d.paraPc()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp)) { Text("▣  Conectar", fontSize = 9.sp) }
                OutlinedButton(onClick = renomear, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp)) { Text("✎  Renomear", fontSize = 9.sp) }
                TextButton(onClick = { RepositorioPcFlowV13.removerDispositivo(d.chave) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("⋮", color = V13Text2) }
            }
        }
    }
}

@Composable
private fun CardContatoV13(contato: ContatoV13, online: Boolean, conectar: () -> Unit, chat: () -> Unit, favorito: () -> Unit) {
    Surface(color = V13Card, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, V13Border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF26313D), CircleShape), contentAlignment = Alignment.Center) { Text(contato.nome.take(1).uppercase(), color = V13Gold, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(contato.nome, fontWeight = FontWeight.Medium)
                Text(contato.grupo, color = V13Text2, fontSize = 9.sp)
                Text(if (online) "● Online" else "● Disponível", color = if (online) V13Teal else V13Text2, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    OutlinedButton(onClick = conectar, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text("Conectar", fontSize = 8.sp) }
                    OutlinedButton(onClick = chat, modifier = Modifier.padding(start = 5.dp), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) { Text("Chat", fontSize = 8.sp) }
                }
                TextButton(onClick = favorito, contentPadding = PaddingValues(0.dp)) { Text(if (contato.favorito) "★ Favorito" else "☆ Favoritar", color = if (contato.favorito) V13Gold else V13Text2, fontSize = 8.sp) }
            }
        }
    }
}

@Composable
private fun MiniaturaV13(sistema: String) {
    val sigla = when {
        sistema.contains("Linux", true) || sistema.contains("Ubuntu", true) -> "LX"
        sistema.contains("mac", true) -> "MC"
        else -> "PC"
    }
    Surface(modifier = Modifier.size(width = 62.dp, height = 47.dp), color = Color(0xFF1F2A35), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF3A4652))) {
        Box(contentAlignment = Alignment.Center) { Text(sigla, color = V13Blue, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
    }
}

@Composable
private fun IconeFavoritoV13(ativo: Boolean, click: () -> Unit) {
    TextButton(onClick = click, contentPadding = PaddingValues(5.dp)) { Text(if (ativo) "★" else "☆", color = if (ativo) V13Gold else V13Text2, fontSize = 22.sp) }
}

@Composable
private fun TituloSecaoV13(simbolo: String, titulo: String, quantidade: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(simbolo, color = V13Gold, fontSize = 19.sp)
        Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
        Text("$quantidade", color = V13Text2, fontSize = 10.sp)
    }
}

@Composable
private fun FiltroChipV13(texto: String, ativo: Boolean, click: () -> Unit) {
    Surface(Modifier.weight(1f).clickable(onClick = click), color = if (ativo) Color(0x332D2413) else V13BgElevado, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (ativo) V13Gold else V13Border)) {
        Text(texto, color = if (ativo) V13Gold else V13Text2, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(vertical = 9.dp))
    }
}

@Composable
private fun EstadoVazioV13(titulo: String, descricao: String) {
    Surface(Modifier.fillMaxWidth(), color = V13BgElevado, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V13Border)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(titulo, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text(descricao, color = V13Text2, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EstadoConexaoCardV13(estado: EstadoSessao) {
    val erro = estado.estado == EstadoConexao.ERRO
    Surface(Modifier.fillMaxWidth(), color = if (erro) Color(0xFF2A1719) else Color(0xFF17241F), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!erro) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = V13Teal)
            Text(estado.mensagem.ifBlank { if (erro) "Falha na conexão" else "Conectando…" }, color = if (erro) V13Danger else V13Teal, fontSize = 10.sp, modifier = Modifier.padding(start = if (erro) 0.dp else 9.dp))
        }
    }
}

@Composable
private fun DialogoConectarCompletoV13(pc: PcEncontrado, pin: String, senha: String, onPin: (String) -> Unit, onSenha: (String) -> Unit, fechar: () -> Unit, conectar: () -> Unit) {
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = V13Card,
        title = {
            Column {
                Text(pc.nome, fontSize = 20.sp)
                Text(formatarIdMobileV13(pc.maquinaId), color = V13Gold, fontSize = 12.sp)
            }
        },
        text = {
            Column {
                Text("Sem credencial, o computador pedirá aprovação. Você também pode usar o código temporário ou a senha não supervisionada.", color = V13Text2, fontSize = 10.sp)
                OutlinedTextField(value = pin, onValueChange = onPin, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Código de 6 dígitos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = senha, onValueChange = onSenha, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Senha de acesso") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { Button(onClick = conectar) { Text("Conectar") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar", color = V13Text2) } }
    )
}

@Composable
private fun DialogoTextoV13(titulo: String, valorInicial: String, rotulo: String, cancelar: () -> Unit, confirmar: (String) -> Unit) {
    var valor by remember { mutableStateOf(valorInicial) }
    AlertDialog(
        onDismissRequest = cancelar,
        containerColor = V13Card,
        title = { Text(titulo) },
        text = { OutlinedTextField(value = valor, onValueChange = { valor = it }, modifier = Modifier.fillMaxWidth(), label = { Text(rotulo) }, singleLine = true) },
        confirmButton = { Button(onClick = { confirmar(valor) }, enabled = valor.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar", color = V13Text2) } }
    )
}

@Composable
private fun DialogoNovoContatoV13(dispositivos: List<DispositivoSalvoV13>, cancelar: () -> Unit, salvar: (String, String?, String) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var grupo by remember { mutableStateOf("Equipe") }
    var escolhido by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = cancelar,
        containerColor = V13Card,
        title = { Text("Novo contato") },
        text = {
            Column {
                OutlinedTextField(nome, { nome = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(grupo, { grupo = it }, Modifier.fillMaxWidth().padding(top = 7.dp), label = { Text("Grupo") }, singleLine = true)
                Text("Vincular dispositivo", color = V13Text2, fontSize = 9.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                dispositivos.take(5).forEach { d ->
                    Surface(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { escolhido = d.chave }, color = if (escolhido == d.chave) Color(0x332D2413) else V13BgElevado, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (escolhido == d.chave) V13Gold else V13Border)) {
                        Text(d.nome, fontSize = 10.sp, modifier = Modifier.padding(9.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { salvar(nome, escolhido, grupo) }, enabled = nome.isNotBlank()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar", color = V13Text2) } }
    )
}

@Composable
private fun DialogoCompartilharV13(dispositivos: List<DispositivoSalvoV13>, cancelar: () -> Unit, compartilhar: (DispositivoSalvoV13) -> Unit) {
    AlertDialog(
        onDismissRequest = cancelar,
        containerColor = V13Card,
        title = { Text("Convidar para conectar") },
        text = {
            Column {
                Text("Escolha qual computador será compartilhado.", color = V13Text2, fontSize = 10.sp)
                dispositivos.take(8).forEach { d ->
                    Surface(Modifier.fillMaxWidth().padding(top = 6.dp).clickable { compartilhar(d) }, color = V13BgElevado, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, V13Border)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▣", color = V13Gold)
                            Column(Modifier.padding(start = 8.dp)) { Text(d.nome, fontSize = 11.sp); Text(formatarIdMobileV13(d.maquinaId), color = V13Text2, fontSize = 9.sp) }
                        }
                    }
                }
                if (dispositivos.isEmpty()) Text("Nenhum dispositivo salvo para compartilhar.", color = V13Danger, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = cancelar) { Text("Fechar", color = V13Text2) } }
    )
}

@Composable
private fun DialogoChatV13(contato: ContatoV13, estado: EstadoSessao, fechar: () -> Unit, enviar: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = fechar,
        containerColor = V13Card,
        title = { Text("Chat · ${contato.nome}") },
        text = {
            Column {
                Text(if (estado.estado == EstadoConexao.CONECTADO) "A mensagem será exibida no PC conectado." else "Conecte ao dispositivo para enviar mensagens.", color = V13Text2, fontSize = 10.sp)
                OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Mensagem") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { enviar(texto); texto = "" }, enabled = texto.isNotBlank()) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Fechar", color = V13Text2) } }
    )
}

private fun chavePcV13(pc: PcEncontrado): String = pc.maquinaId.ifBlank { "${pc.host}:${pc.porta}" }
private fun formatarIdMobileV13(id: String): String = if (id.length == 9) "${id.take(3)} ${id.substring(3, 6)} ${id.takeLast(3)}" else id.ifBlank { "Sem ID" }
private fun tempoRelativoV13(quando: Long): String {
    val delta = System.currentTimeMillis() - quando
    return when {
        delta < 60_000 -> "agora"
        delta < 60 * 60_000 -> "há ${delta / 60_000} min"
        delta < 24 * 60 * 60_000 -> "há ${delta / (60 * 60_000)} h"
        delta < 7 * 24 * 60 * 60_000 -> "há ${delta / (24 * 60 * 60_000)} dias"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(quando))
    }
}
