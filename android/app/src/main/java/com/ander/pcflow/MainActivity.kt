package com.ander.pcflow

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ander.pcflow.rede.EnderecoPcFlow
import com.ander.pcflow.rede.EventoPc
import com.ander.pcflow.rede.ItemRemoto
import com.ander.pcflow.rede.SessaoPcFlow
import com.ander.pcflow.ui.Fundo
import com.ander.pcflow.ui.Preferencias
import com.ander.pcflow.ui.TelaConectar
import com.ander.pcflow.ui.TelaControle
import com.ander.pcflow.ui.TemaPcFlow
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import androidx.compose.foundation.layout.safeDrawingPadding

class MainActivity : ComponentActivity() {

    /** Guarda um pcflow:// aberto por link externo até a interface estar pronta. */
    private var linkPendente by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 força edge-to-edge: sem isto o conteúdo fica embaixo da
        // barra de status e dos botões de navegação.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        SessaoPcFlow.inicializar(this)
        linkPendente = intent?.dataString

        setContent {
            TemaPcFlow {
                Surface(Modifier.fillMaxSize(), color = Fundo) {
                    AppPcFlow(
                        linkInicial = linkPendente,
                        aoConsumirLink = { linkPendente = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { linkPendente = it }
    }
}

@Composable
private fun AppPcFlow(linkInicial: String?, aoConsumirLink: () -> Unit) {
    val contexto = LocalContext.current
    val prefs = remember { Preferencias(contexto) }

    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val pcs by SessaoPcFlow.pcs.collectAsStateWithLifecycle()
    val evento by SessaoPcFlow.eventos.collectAsStateWithLifecycle()

    var aplicativos by remember { mutableStateOf<List<ItemRemoto>>(emptyList()) }
    var arquivos by remember { mutableStateOf<Pair<String, List<ItemRemoto>>?>(null) }
    var quadroTela by remember { mutableStateOf<ImageBitmap?>(null) }
    var recebendo by remember { mutableStateOf<Pair<String, java.io.OutputStream>?>(null) }

    val pedirNotificacao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val leitorQr = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val conteudo = resultado.contents ?: return@rememberLauncherForActivityResult
        val lido = EnderecoPcFlow.interpretar(conteudo)
        if (lido == null) {
            Toast.makeText(contexto, "QR não reconhecido pelo PCFlow.", Toast.LENGTH_LONG).show()
        } else {
            SessaoPcFlow.adicionarManual(lido.pc)
            SessaoPcFlow.conectar(lido.pc, lido.pin)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        SessaoPcFlow.descobrir()
        if (prefs.conectarAoAbrir) SessaoPcFlow.reconectarAutomaticamente()
    }

    // Link pcflow:// vindo do leitor de QR do próprio sistema.
    LaunchedEffect(linkInicial) {
        val link = linkInicial ?: return@LaunchedEffect
        EnderecoPcFlow.interpretar(link)?.let {
            SessaoPcFlow.adicionarManual(it.pc)
            SessaoPcFlow.conectar(it.pc, it.pin)
        }
        aoConsumirLink()
    }

    // Eventos vindos do PC.
    LaunchedEffect(evento) {
        when (val e = evento) {
            null -> return@LaunchedEffect
            is EventoPc.Aviso ->
                Toast.makeText(contexto, e.mensagem, Toast.LENGTH_LONG).show()

            is EventoPc.AreaTransferencia -> {
                val gerenciador = contexto.getSystemService(android.content.ClipboardManager::class.java)
                gerenciador?.setPrimaryClip(
                    android.content.ClipData.newPlainText("PCFlow", e.texto)
                )
                Toast.makeText(contexto, "Texto do PC copiado.", Toast.LENGTH_SHORT).show()
            }

            is EventoPc.Aplicativos -> aplicativos = e.itens
            is EventoPc.Arquivos -> arquivos = e.caminho to e.itens

            is EventoPc.BlocoArquivo -> {
                val nome = e.caminho.substringAfterLast('\\').substringAfterLast('/')
                val destino = File(
                    contexto.getExternalFilesDir(null) ?: contexto.filesDir, nome
                )
                runCatching {
                    val fluxo = recebendo?.takeIf { it.first == e.caminho }?.second
                        ?: java.io.FileOutputStream(destino).also { recebendo = e.caminho to it }
                    if (e.dadosBase64.isNotEmpty()) {
                        fluxo.write(Base64.decode(e.dadosBase64, Base64.DEFAULT))
                    }
                    if (e.fim) {
                        fluxo.flush(); fluxo.close(); recebendo = null
                        Toast.makeText(
                            contexto, "Salvo em ${destino.absolutePath}", Toast.LENGTH_LONG
                        ).show()
                    } else {
                        SessaoPcFlow.baixarArquivo(
                            e.caminho, e.offset + Base64.decode(e.dadosBase64, Base64.DEFAULT).size
                        )
                    }
                }.onFailure {
                    recebendo = null
                    Toast.makeText(contexto, "Falha ao salvar: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }

            is EventoPc.QuadroTela -> runCatching {
                val bytes = Base64.decode(e.jpegBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.let { quadroTela = it }
        }
        SessaoPcFlow.consumirEvento()
    }

    if (estado.conectado) {
        TelaControle(
            estado = estado,
            prefs = prefs,
            aplicativos = aplicativos,
            arquivos = arquivos,
            quadroTela = quadroTela,
            aoDesconectar = {
                SessaoPcFlow.desconectar()
                aplicativos = emptyList()
                arquivos = null
                quadroTela = null
            },
            modifier = Modifier.safeDrawingPadding()
        )
    } else {
        TelaConectar(
            pcs = pcs,
            estado = estado,
            aoConectar = { pc, pin -> SessaoPcFlow.conectar(pc, pin) },
            aoAtualizar = { SessaoPcFlow.descobrir() },
            aoEsquecer = { SessaoPcFlow.esquecerPc(it) },
            aoEscanear = {
                leitorQr.launch(
                    ScanOptions()
                        .setPrompt("Aponte para o QR que aparece no PCFlow do computador")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                )
            },
            aoAdicionarManual = { texto ->
                val lido = EnderecoPcFlow.interpretar(texto)
                if (lido == null) {
                    Toast.makeText(contexto, "Endereço inválido.", Toast.LENGTH_LONG).show()
                } else {
                    SessaoPcFlow.adicionarManual(lido.pc)
                    SessaoPcFlow.conectar(lido.pc, lido.pin)
                }
            },
            modifier = Modifier.safeDrawingPadding()
        )
    }
}
