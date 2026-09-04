package com.ander.pcflow

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat

private val ArquivosFundo = Color(0xFF11161C)
private val ArquivosPainel = Color(0xFF171C23)
private val ArquivosBorda = Color(0xFF343B45)
private val ArquivosDourado = Color(0xFFF2AA2E)
private val ArquivosTurquesa = Color(0xFF16D3C6)
private val ArquivosSecundario = Color(0xFF9AA2AC)

@Composable
fun DialogoArquivosRemotos(fechar: () -> Unit) {
    val estado by SessaoPcFlow.arquivos.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var novaPasta by remember { mutableStateOf(false) }
    var nomePasta by remember { mutableStateOf("") }
    var apagar by remember { mutableStateOf<ArquivoRemoto?>(null) }

    val selecionarArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) SessaoPcFlow.enviarArquivo(contexto, uri)
    }

    LaunchedEffect(Unit) {
        SessaoPcFlow.listarArquivos("")
    }

    Dialog(
        onDismissRequest = fechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            color = ArquivosFundo,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ArquivosBorda)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Arquivos do computador", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (estado.caminho.isBlank()) "Este computador" else estado.caminho,
                            color = ArquivosSecundario,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                    TextButton(onClick = fechar) { Text("Fechar") }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (estado.pai.isNotBlank()) SessaoPcFlow.listarArquivos(estado.pai)
                            else SessaoPcFlow.listarArquivos("")
                        },
                        enabled = estado.caminho.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) { Text("Voltar", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { SessaoPcFlow.listarArquivos(estado.caminho) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) { Text("Atualizar", fontSize = 11.sp) }
                    Button(
                        onClick = { selecionarArquivo.launch(arrayOf("*/*")) },
                        enabled = estado.caminho.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) { Text("Enviar", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { novaPasta = true },
                        enabled = estado.caminho.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                    ) { Text("+ Pasta", fontSize = 11.sp) }
                }

                if (estado.carregando) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                }
                if (estado.mensagem.isNotBlank()) {
                    Text(
                        estado.mensagem,
                        color = if (estado.mensagem.startsWith("Arquivos:") || estado.mensagem.startsWith("Download:") || estado.mensagem.startsWith("Envio:")) Color(0xFFFF8A80) else ArquivosTurquesa,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (!estado.carregando && estado.itens.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum item encontrado.", color = ArquivosSecundario)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(estado.itens, key = { it.caminho }) { item ->
                            ItemArquivoRemoto(
                                item = item,
                                abrir = {
                                    if (item.pasta || item.raiz) SessaoPcFlow.listarArquivos(item.caminho)
                                    else SessaoPcFlow.baixarArquivo(contexto, item)
                                },
                                baixar = { SessaoPcFlow.baixarArquivo(contexto, item) },
                                apagar = { apagar = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (novaPasta) {
        AlertDialog(
            onDismissRequest = { novaPasta = false; nomePasta = "" },
            containerColor = ArquivosPainel,
            title = { Text("Criar pasta") },
            text = {
                OutlinedTextField(
                    value = nomePasta,
                    onValueChange = { nomePasta = it.take(80) },
                    singleLine = true,
                    label = { Text("Nome da pasta") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SessaoPcFlow.criarPasta(nomePasta)
                        novaPasta = false
                        nomePasta = ""
                    },
                    enabled = nomePasta.isNotBlank()
                ) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { novaPasta = false; nomePasta = "" }) { Text("Cancelar") } }
        )
    }

    apagar?.let { item ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            containerColor = ArquivosPainel,
            title = { Text("Excluir ${if (item.pasta) "pasta" else "arquivo"}?") },
            text = { Text("${item.nome}\n\nEssa ação será executada no computador remoto.", color = ArquivosSecundario) },
            confirmButton = {
                Button(onClick = { SessaoPcFlow.apagarArquivo(item); apagar = null }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ItemArquivoRemoto(
    item: ArquivoRemoto,
    abrir: () -> Unit,
    baixar: () -> Unit,
    apagar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = abrir),
        color = ArquivosPainel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ArquivosBorda)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(Color(0xFF20262E), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (item.pasta || item.raiz) "DIR" else "FILE", color = ArquivosDourado, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.nome.ifBlank { item.caminho }, fontSize = 13.sp, maxLines = 1)
                val detalhe = buildString {
                    if (!item.pasta && !item.raiz) append(formatarTamanho(item.tamanho))
                    if (item.modificado.isNotBlank()) {
                        if (isNotBlank()) append(" · ")
                        append(item.modificado)
                    }
                }
                if (detalhe.isNotBlank()) Text(detalhe, color = ArquivosSecundario, fontSize = 10.sp, maxLines = 1)
            }
            if (!item.pasta && !item.raiz) {
                TextButton(onClick = baixar, contentPadding = PaddingValues(6.dp)) { Text("Baixar", fontSize = 10.sp) }
            }
            if (!item.raiz) {
                TextButton(onClick = apagar, contentPadding = PaddingValues(6.dp)) { Text("Excluir", color = Color(0xFFFF8A80), fontSize = 10.sp) }
            }
        }
    }
}

private fun formatarTamanho(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("0.#").format(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${DecimalFormat("0.#").format(mb)} MB"
    return "${DecimalFormat("0.##").format(mb / 1024.0)} GB"
}
