package com.ander.pcflow.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/**
 * O diálogo do PCFlow. Nenhum `AlertDialog` do Android sobra no aplicativo:
 * véu próprio a 55%, painel de raio 20 com borda de 1px, título em 20, texto
 * em 15, no máximo dois botões — e o de confirmar em dourado, ou em vermelho
 * quando a ação destrói alguma coisa.
 */
@Composable
fun DialogoPcFlow(
    titulo: String,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
    texto: String? = null,
    destrutivo: Boolean = false,
    textoConfirmar: String? = null,
    aoConfirmar: (() -> Unit)? = null,
    confirmarHabilitado: Boolean = true,
    motivoConfirmarDesabilitado: String? = null,
    textoCancelar: String? = "Cancelar",
    conteudo: @Composable ColumnScope.() -> Unit = {}
) {
    val cores = PcFlow.cores
    var visivel by remember { mutableStateOf(false) }
    var saindo by remember { mutableStateOf<(() -> Unit)?>(null) }
    val duracao = duracaoPcFlow(DURACAO_PAINEL_MS)

    val progresso by animateFloatAsState(
        targetValue = if (visivel && saindo == null) 1f else 0f,
        animationSpec = especPcFlow(DURACAO_PAINEL_MS),
        label = "aberturaDialogo"
    )

    LaunchedEffect(Unit) { visivel = true }
    LaunchedEffect(saindo) {
        val acao = saindo ?: return@LaunchedEffect
        delay(duracao.toLong())
        acao()
    }

    // Fechar animado: quem clica marca a saída, a animação corre e só então o
    // estado de quem chamou muda. Sem isso o painel some de uma vez.
    val encerrar: (() -> Unit) -> Unit = { acao -> if (saindo == null) saindo = acao }

    Dialog(
        onDismissRequest = { if (!destrutivo) encerrar(aoFechar) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        SemEscurecimentoDoSistema()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = OPACIDADE_VEU * progresso))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    // Ação destrutiva não sai por toque fora: só por decisão.
                    enabled = !destrutivo,
                    onClick = { encerrar(aoFechar) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier
                    .padding(EspacoPcFlow.x24)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .alpha(progresso)
                    .padding(top = ((1f - progresso) * 8f).dp)
                    .clip(PcFlow.formas.painel)
                    .background(cores.superficie)
                    .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.painel)
                    // O toque no painel não pode vazar para o véu e fechar.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(DimensaoPcFlow.folgaPainel)
            ) {
                Text(titulo, color = cores.texto, style = MaterialTheme.typography.headlineSmall)
                if (!texto.isNullOrBlank()) {
                    Spacer(Modifier.height(EspacoPcFlow.x8))
                    Text(texto, color = cores.textoSecundario, style = MaterialTheme.typography.bodyLarge)
                }
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    conteudo()
                }
                if (textoConfirmar != null && aoConfirmar != null || textoCancelar != null) {
                    Spacer(Modifier.height(EspacoPcFlow.x16))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
                    ) {
                        if (textoCancelar != null) {
                            BotaoSecundario(
                                texto = textoCancelar,
                                aoClicar = { encerrar(aoFechar) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (textoConfirmar != null && aoConfirmar != null) {
                            val confirmar = { encerrar(aoConfirmar) }
                            if (destrutivo) {
                                BotaoPerigo(
                                    texto = textoConfirmar,
                                    aoClicar = confirmar,
                                    modifier = Modifier.weight(1f),
                                    habilitado = confirmarHabilitado,
                                    motivoDesabilitado = motivoConfirmarDesabilitado
                                )
                            } else {
                                BotaoPrimario(
                                    texto = textoConfirmar,
                                    aoClicar = confirmar,
                                    modifier = Modifier.weight(1f),
                                    habilitado = confirmarHabilitado,
                                    motivoDesabilitado = motivoConfirmarDesabilitado
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * O Android escurece a tela atrás de qualquer diálogo. Como o PCFlow desenha
 * o próprio véu, o escurecimento do sistema é desligado para não somar dois.
 */
@Composable
private fun SemEscurecimentoDoSistema() {
    val vista = LocalView.current
    LaunchedEffect(vista) {
        (vista.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }
}

/** Tom do aviso em faixa. Vermelho continua reservado para erro. */
enum class TomAviso { INFO, SUCESSO, ERRO }

data class Aviso(val texto: String, val tom: TomAviso = TomAviso.INFO, val sequencia: Long = 0L)

/**
 * Substitui o `Toast` do Android. Fica dentro do aplicativo, com a cara dele,
 * some sozinho e pode ser dispensado no toque.
 */
class ControleAvisos {
    var atual by mutableStateOf<Aviso?>(null)
        private set

    private var sequencia = 0L

    fun mostrar(texto: String, tom: TomAviso = TomAviso.INFO) {
        sequencia += 1
        atual = Aviso(texto, tom, sequencia)
    }

    fun limpar() { atual = null }
}

@Composable
fun rememberControleAvisos(): ControleAvisos = remember { ControleAvisos() }

const val DURACAO_AVISO_MS = 4_000L

@Composable
fun FaixaAviso(
    aviso: Aviso?,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    LaunchedEffect(aviso?.sequencia) {
        if (aviso != null) {
            delay(DURACAO_AVISO_MS)
            aoFechar()
        }
    }
    val progresso by animateFloatAsState(
        targetValue = if (aviso != null) 1f else 0f,
        animationSpec = especPcFlow(DURACAO_PAINEL_MS),
        label = "aberturaAviso"
    )
    if (aviso == null && progresso == 0f) return

    val tom = when (aviso?.tom) {
        TomAviso.SUCESSO -> cores.seguro
        TomAviso.ERRO -> cores.erro
        else -> cores.acaoTexto
    }
    val icone = when (aviso?.tom) {
        TomAviso.SUCESSO -> Icone.CHECK
        TomAviso.ERRO -> Icone.ALERTA
        else -> Icone.INFO
    }
    Row(
        modifier
            .fillMaxWidth()
            .alpha(progresso)
            .padding(bottom = ((1f - progresso) * 8f).dp)
            .clip(PcFlow.formas.cartao)
            .background(cores.superficieElevada)
            .border(DimensaoPcFlow.borda, cores.contornar(tom), PcFlow.formas.cartao)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Dispensar aviso",
                onClick = aoFechar
            )
            .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        IconePcFlow(icone, tom, tamanho = 18.dp)
        Text(
            text = aviso?.texto.orEmpty(),
            color = cores.texto,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
