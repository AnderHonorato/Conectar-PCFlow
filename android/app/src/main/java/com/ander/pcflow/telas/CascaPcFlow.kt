package com.ander.pcflow.telas

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ander.pcflow.DialogoArquivosRemotos
import com.ander.pcflow.EstadoConexao
import com.ander.pcflow.PcEncontrado
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.TelaRemotaPcFlow
import com.ander.pcflow.design.DimensaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.FaixaAviso
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.IconePcFlow
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.TomAviso
import com.ander.pcflow.design.especPcFlow
import com.ander.pcflow.design.rememberControleAvisos
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Os quatro destinos do aplicativo.
 *
 * O mockup trazia "Contatos" com pessoas, presença e conversa. Isso exige
 * conta de usuário e servidor de diretório, que o PCFlow não tem — e botão
 * que não funciona é pior que botão ausente (TELAS_V13). O lugar ficou com
 * Recentes, que o motor sustenta hoje.
 */
enum class Destino(val rotulo: String, val apoio: String, val icone: Icone) {
    INICIO("Início", "Conectar a um computador", Icone.CASA),
    RECENTES("Recentes", "Histórico das suas sessões", Icone.RELOGIO),
    DISPOSITIVOS("Dispositivos", "Seus computadores conhecidos", Icone.COMPUTADOR),
    AJUSTES("Ajustes", "Tema, sessão e privacidade", Icone.AJUSTES)
}

/** O que as telas podem pedir à casca. Evita passar dez funções por tela. */
class AcoesCasca(
    val pedirConexao: (Dispositivo) -> Unit,
    val renomear: (Dispositivo) -> Unit,
    val alternarFavorito: (Dispositivo) -> Unit,
    val abrirMenu: (Dispositivo) -> Unit,
    val escanearQr: () -> Unit,
    val conectarPorCodigo: () -> Unit,
    val buscarNaRede: () -> Unit,
    val adicionarDispositivo: () -> Unit,
    val irPara: (Destino) -> Unit,
    val avisar: (String, TomAviso) -> Unit
)

/** Diálogos que a casca pode ter aberto. Um de cada vez. */
private sealed interface Painel {
    data object Nenhum : Painel
    data object Menu : Painel
    data object Sobre : Painel
    data object Adicionar : Painel
    data object PorCodigo : Painel
    data class Conectar(val dispositivo: Dispositivo) : Painel
    data class Renomear(val dispositivo: Dispositivo) : Painel
    data class MenuDispositivo(val dispositivo: Dispositivo) : Painel
    data class Esquecer(val dispositivo: Dispositivo) : Painel
    data object LimparHistorico : Painel
}

@Composable
fun CascaPcFlow() {
    val contexto = LocalContext.current
    val casca = rememberEstadoCasca()
    val avisos = rememberControleAvisos()

    val estado by SessaoPcFlow.estado.collectAsStateWithLifecycle()
    val encontrados by SessaoPcFlow.pcs.collectAsStateWithLifecycle()

    var destino by remember { mutableStateOf(Destino.INICIO) }
    var painel by remember { mutableStateOf<Painel>(Painel.Nenhum) }
    var abrirArquivosNaSessao by remember { mutableStateOf(false) }
    var inicioSessaoMs by remember { mutableLongStateOf(0L) }
    var estadoAnterior by remember { mutableStateOf(estado.estado) }

    val dispositivos = remember(encontrados, casca.lembrados, casca.favoritos, casca.apelidos) {
        casca.dispositivos(encontrados)
    }

    val notificacao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val leitorQr = rememberLauncherForActivityResult(ScanContract()) { resultado ->
        val lido = resultado.contents?.let(::lerQrPcFlow)
        if (lido == null) {
            if (resultado.contents != null) {
                avisos.mostrar("Esse QR não é do PCFlow.", TomAviso.ERRO)
            }
        } else {
            casca.aplicarNaSessao()
            SessaoPcFlow.conectar(lido.first, lido.second, null)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        casca.aplicarNaSessao()
        SessaoPcFlow.descobrir()
    }

    // Uma linha do histórico por sessão: quando ela começou, quanto durou e
    // como terminou. É a matéria-prima da aba Recentes.
    LaunchedEffect(estado.estado) {
        val de = estadoAnterior
        val para = estado.estado
        if (de == para) return@LaunchedEffect
        val agora = System.currentTimeMillis()
        if (para == EstadoConexao.CONECTADO) {
            inicioSessaoMs = agora
            estado.pc?.let(casca::lembrar)
            casca.aplicarNaSessao()
        }
        CronometroSessao.resultadoDe(de, para, estado.mensagem)?.let { resultado ->
            val pc = estado.pc
            casca.registrar(
                RegistroSessao(
                    inicioMs = if (inicioSessaoMs > 0L) inicioSessaoMs else agora,
                    duracaoSegundos = CronometroSessao.duracaoSegundos(inicioSessaoMs, agora),
                    dispositivoId = pc?.maquinaId.orEmpty(),
                    nome = pc?.let(casca::nomeDe) ?: "Computador",
                    resultado = resultado
                )
            )
            inicioSessaoMs = 0L
            if (resultado != ResultadoSessao.CONCLUIDA) abrirArquivosNaSessao = false
        }
        estadoAnterior = para
    }

    val escanear = {
        leitorQr.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Aponte para o QR do PCFlow no computador")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    val acoes = remember(casca, dispositivos) {
        AcoesCasca(
            pedirConexao = { painel = Painel.Conectar(it) },
            renomear = { painel = Painel.Renomear(it) },
            alternarFavorito = { casca.alternarFavorito(it.chave) },
            abrirMenu = { painel = Painel.MenuDispositivo(it) },
            escanearQr = { escanear() },
            conectarPorCodigo = { painel = Painel.PorCodigo },
            buscarNaRede = {
                SessaoPcFlow.descobrir()
                avisos.mostrar("Procurando computadores nesta rede…")
            },
            adicionarDispositivo = { painel = Painel.Adicionar },
            irPara = { destino = it },
            avisar = { texto, tom -> avisos.mostrar(texto, tom) }
        )
    }

    val conectar: (PcEncontrado, String?, String?) -> Unit = { pc, codigo, senha ->
        casca.aplicarNaSessao()
        SessaoPcFlow.conectar(pc, codigo, senha)
    }

    if (estado.estado == EstadoConexao.CONECTADO && estado.pc != null) {
        // Em sessão a tela é da frente B, inteira. A casca só volta a existir
        // quando ela pedir para fechar.
        TelaRemotaPcFlow(estado) { SessaoPcFlow.desconectar() }
        if (abrirArquivosNaSessao) {
            if (estado.permissoes.arquivos) {
                DialogoArquivosRemotos { abrirArquivosNaSessao = false }
            } else {
                LaunchedEffect(Unit) {
                    abrirArquivosNaSessao = false
                    avisos.mostrar(
                        "Este computador não liberou a transferência de arquivos.",
                        TomAviso.ERRO
                    )
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PcFlow.cores.fundo)
    ) {
        Column(Modifier.safeDrawingPadding()) {
            CabecalhoCasca(
                aoAbrirMenu = { painel = Painel.Menu },
                aoAbrirAjustes = { destino = Destino.AJUSTES }
            )
            Box(Modifier.weight(1f)) {
                when (destino) {
                    Destino.INICIO -> TelaInicio(
                        dispositivos = dispositivos,
                        estado = estado,
                        acoes = acoes
                    )
                    Destino.RECENTES -> TelaRecentes(
                        historico = casca.historico,
                        dispositivos = dispositivos,
                        acoes = acoes,
                        aoLimpar = { painel = Painel.LimparHistorico }
                    )
                    Destino.DISPOSITIVOS -> TelaDispositivos(
                        dispositivos = dispositivos,
                        acoes = acoes
                    )
                    Destino.AJUSTES -> TelaAjustes(
                        ajustes = casca.ajustes,
                        aoMudar = casca::salvarAjustes,
                        aoSobre = { painel = Painel.Sobre },
                        conectado = estado.estado == EstadoConexao.CONECTADO,
                        avisar = acoes.avisar
                    )
                }
                FaixaAviso(
                    aviso = avisos.atual,
                    aoFechar = avisos::limpar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(EspacoPcFlow.x16)
                )
            }
            BarraDestinos(destino) { destino = it }
        }
    }

    PaineisDaCasca(
        painel = painel,
        casca = casca,
        avisos = { texto, tom -> avisos.mostrar(texto, tom) },
        contexto = contexto,
        fechar = { painel = Painel.Nenhum },
        abrir = { painel = it },
        conectar = conectar,
        escanear = { escanear() },
        irPara = { destino = it },
        pedirArquivos = { abrirArquivosNaSessao = true }
    )
}

@Composable
private fun PaineisDaCasca(
    painel: Painel,
    casca: EstadoCasca,
    avisos: (String, TomAviso) -> Unit,
    contexto: Context,
    fechar: () -> Unit,
    abrir: (Painel) -> Unit,
    conectar: (PcEncontrado, String?, String?) -> Unit,
    escanear: () -> Unit,
    irPara: (Destino) -> Unit,
    pedirArquivos: () -> Unit
) {
    when (painel) {
        Painel.Nenhum -> Unit

        Painel.Menu -> DialogoMenuAplicativo(
            aoFechar = fechar,
            aoIr = { destino -> fechar(); irPara(destino) },
            aoBuscarNaRede = {
                fechar()
                SessaoPcFlow.descobrir()
                avisos("Procurando computadores nesta rede…", TomAviso.INFO)
            },
            aoSobre = { abrir(Painel.Sobre) }
        )

        Painel.Sobre -> DialogoSobre(aoFechar = fechar)

        Painel.Adicionar -> DialogoAdicionarDispositivo(
            aoFechar = fechar,
            aoDigitarId = {
                fechar()
                irPara(Destino.INICIO)
                avisos("Digite o ID no campo do início.", TomAviso.INFO)
            },
            aoEscanear = { fechar(); escanear() },
            aoPorCodigo = { abrir(Painel.PorCodigo) },
            aoBuscarNaRede = {
                fechar()
                SessaoPcFlow.descobrir()
                avisos("Procurando computadores nesta rede…", TomAviso.INFO)
            }
        )

        Painel.PorCodigo -> DialogoConectarPorCodigo(
            servidorInicial = casca.ajustes.servidorRetransmissao.ifBlank { SessaoPcFlow.servidorSalvo() },
            aoFechar = fechar,
            aoConectar = { destino, senha, servidor ->
                casca.salvarAjustes(casca.ajustes.copy(servidorRetransmissao = servidor))
                fechar()
                conectar(destino, null, senha)
            }
        )

        is Painel.Conectar -> DialogoConectar(
            dispositivo = painel.dispositivo,
            aoFechar = fechar,
            aoConectar = { pc, codigo, senha ->
                fechar()
                conectar(pc, codigo, senha)
            }
        )

        is Painel.Renomear -> DialogoRenomear(
            dispositivo = painel.dispositivo,
            aoFechar = fechar,
            aoSalvar = { apelido ->
                casca.renomear(painel.dispositivo.chave, apelido)
                fechar()
                avisos(
                    if (apelido.isBlank()) "Apelido removido."
                    else "Agora chama \"${apelido.trim()}\".",
                    TomAviso.SUCESSO
                )
            }
        )

        is Painel.MenuDispositivo -> DialogoMenuDispositivo(
            dispositivo = painel.dispositivo,
            aoFechar = fechar,
            aoConectar = { abrir(Painel.Conectar(painel.dispositivo)) },
            aoRenomear = { abrir(Painel.Renomear(painel.dispositivo)) },
            aoAlternarFavorito = {
                casca.alternarFavorito(painel.dispositivo.chave)
                fechar()
            },
            aoCopiarId = {
                copiarParaAreaDeTransferencia(contexto, painel.dispositivo.id)
                fechar()
                avisos("ID copiado.", TomAviso.SUCESSO)
            },
            aoAbrirArquivos = {
                pedirArquivos()
                abrir(Painel.Conectar(painel.dispositivo))
            },
            aoEsquecer = { abrir(Painel.Esquecer(painel.dispositivo)) }
        )

        is Painel.Esquecer -> DialogoConfirmar(
            titulo = "Esquecer ${painel.dispositivo.rotulo}?",
            texto = "O apelido, o favorito e o endereço guardado saem deste " +
                "aparelho. O computador continua existindo na rede.",
            textoConfirmar = "Esquecer",
            aoFechar = fechar,
            aoConfirmar = {
                casca.esquecer(painel.dispositivo.chave)
                fechar()
                avisos("Computador esquecido.", TomAviso.INFO)
            }
        )

        Painel.LimparHistorico -> DialogoConfirmar(
            titulo = "Apagar o histórico?",
            texto = "As sessões guardadas neste aparelho somem. Isso não " +
                "desconecta nem apaga nada no computador.",
            textoConfirmar = "Apagar",
            aoFechar = fechar,
            aoConfirmar = {
                casca.limparHistorico()
                fechar()
                avisos("Histórico apagado.", TomAviso.INFO)
            }
        )
    }
}

/** Barra inferior do mockup: quatro destinos, o ativo em dourado. */
@Composable
private fun BarraDestinos(atual: Destino, aoEscolher: (Destino) -> Unit) {
    val cores = PcFlow.cores
    Row(
        Modifier
            .fillMaxWidth()
            .background(cores.superficie)
            .padding(horizontal = EspacoPcFlow.x8, vertical = EspacoPcFlow.x8),
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x4)
    ) {
        Destino.entries.forEach { item ->
            val ativo = item == atual
            val fundo by animateColorAsState(
                targetValue = if (ativo) cores.acao else cores.superficie,
                animationSpec = especPcFlow(),
                label = "fundoDestino"
            )
            val tinta = if (ativo) cores.sobreAcao else cores.textoSecundario
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = DimensaoPcFlow.alvoMinimo)
                    .clip(PcFlow.formas.cartao)
                    .background(fundo)
                    .selectable(
                        selected = ativo,
                        role = Role.Tab,
                        onClick = { aoEscolher(item) }
                    )
                    .padding(vertical = EspacoPcFlow.x8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x4)
            ) {
                IconePcFlow(item.icone, tinta, Modifier.size(22.dp), tamanho = 22.dp)
                Text(
                    text = item.rotulo,
                    color = tinta,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun copiarParaAreaDeTransferencia(contexto: Context, texto: String) {
    val servico = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    servico?.setPrimaryClip(ClipData.newPlainText("PCFlow", texto))
}

/**
 * QR do PCFlow: `pcflow://connect?host=…&port=…&id=…&tls=…&pin=…`. Sem a
 * impressão do certificado (`tls`) o código não serve, porque é ela que
 * garante que o computador do outro lado é o esperado.
 */
fun lerQrPcFlow(texto: String): Pair<PcEncontrado, String?>? = runCatching {
    val uri = Uri.parse(texto)
    if (uri.scheme != "pcflow" || uri.host != "connect") return null
    val host = uri.getQueryParameter("host") ?: return null
    val tls = uri.getQueryParameter("tls") ?: return null
    val id = uri.getQueryParameter("id").orEmpty()
    PcEncontrado(
        nome = if (id.isBlank()) "PCFlow em $host" else "PCFlow $id",
        host = host,
        porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456,
        portaTela = 45457,
        portaArquivos = 45458,
        maquinaId = id,
        tls = tls
    ) to uri.getQueryParameter("pin")
}.getOrNull()
