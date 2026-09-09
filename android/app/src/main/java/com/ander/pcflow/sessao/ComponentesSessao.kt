package com.ander.pcflow.sessao

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ander.pcflow.design.DURACAO_PAINEL_MS
import com.ander.pcflow.design.DimensaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.IconePcFlow
import com.ander.pcflow.design.OPACIDADE_VEU
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.RaioPcFlow
import com.ander.pcflow.design.duracaoPcFlow

/**
 * Painel da sessão: sobe de baixo por cima da tela remota.
 *
 * Não é `Dialog` nem `AlertDialog` de propósito. Uma janela do sistema por cima
 * de uma sessão em tela cheia rouba o foco do teclado e deixa a imagem
 * escondida atrás de um retângulo cinza; aqui o painel é desenhado dentro da
 * própria tela, com a cara do PCFlow.
 */
@Composable
fun BoxScope.PainelSessao(
    visivel: Boolean,
    titulo: String,
    aoFechar: () -> Unit,
    subtitulo: String? = null,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val cores = PcFlow.cores
    val duracao = duracaoPcFlow(DURACAO_PAINEL_MS)

    AnimatedVisibility(
        visible = visivel,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(duracao)),
        exit = fadeOut(tween(duracao))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = OPACIDADE_VEU))
                // Fechar tocando fora só vale porque nenhum painel daqui faz
                // coisa destrutiva sem confirmar.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = aoFechar
                )
        )
    }

    AnimatedVisibility(
        visible = visivel,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(duracao)) { it } + fadeIn(tween(duracao)),
        exit = slideOutVertically(tween(duracao)) { it } + fadeOut(tween(duracao))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(topStart = RaioPcFlow.painel, topEnd = RaioPcFlow.painel))
                .background(cores.superficie)
                .border(
                    DimensaoPcFlow.borda,
                    cores.borda,
                    RoundedCornerShape(topStart = RaioPcFlow.painel, topEnd = RaioPcFlow.painel)
                )
                // Engole o toque para não vazar gesto para a tela remota atrás.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(EspacoPcFlow.x16)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titulo, color = cores.texto, style = MaterialTheme.typography.headlineSmall)
                    if (!subtitulo.isNullOrBlank()) {
                        Text(
                            subtitulo,
                            color = cores.textoSecundario,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                BotaoSessao(
                    icone = Icone.FECHAR,
                    descricao = "Fechar o painel",
                    aoTocar = aoFechar
                )
            }
            Spacer(Modifier.height(EspacoPcFlow.x12))
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12),
                content = conteudo
            )
            Spacer(Modifier.height(EspacoPcFlow.x8))
        }
    }
}

/** Botão redondo de ícone da sessão, com alvo de 48 dp e pressão longa opcional. */
@Composable
fun BotaoSessao(
    icone: Icone,
    descricao: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    ativo: Boolean = false,
    cor: Color? = null,
    aoPressionarLongo: (() -> Unit)? = null
) {
    val cores = PcFlow.cores
    val tom = cor ?: if (ativo) cores.acao else cores.texto
    Box(
        modifier
            .size(DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.pilula)
            .background(if (ativo) cores.tingir(tom, 0.18f) else Color.Transparent)
            .pointerInput(aoTocar, aoPressionarLongo) {
                detectTapGestures(
                    onTap = { aoTocar() },
                    onLongPress = aoPressionarLongo?.let { longo -> { longo() } }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        IconePcFlow(icone, tom, tamanho = 22.dp, descricao = descricao)
    }
}

/** Tecla do teclado da sessão. Pressão longa serve para travar o modificador. */
@Composable
fun TeclaSessao(
    rotulo: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    destaque: Boolean = false,
    travada: Boolean = false,
    aoPressionarLongo: (() -> Unit)? = null
) {
    val cores = PcFlow.cores
    val corTexto = when {
        travada -> cores.sobreAcao
        destaque -> cores.acao
        else -> cores.texto
    }
    val fundo = when {
        travada -> cores.acao
        destaque -> cores.tingir(cores.acao, 0.16f)
        else -> cores.superficieElevada
    }
    Box(
        modifier
            .heightIn(min = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.controle)
            .background(fundo)
            .border(DimensaoPcFlow.borda, if (travada) cores.acao else cores.borda, PcFlow.formas.controle)
            .pointerInput(aoTocar, aoPressionarLongo) {
                detectTapGestures(
                    onTap = { aoTocar() },
                    onLongPress = aoPressionarLongo?.let { longo -> { longo() } }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            rotulo,
            color = corTexto,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EspacoPcFlow.x8)
        )
    }
}

/** Escolha entre poucas opções, sempre com a atual visível. */
@Composable
fun <T> SeletorSegmentado(
    opcoes: List<T>,
    selecionado: T,
    rotulo: (T) -> String,
    aoSelecionar: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    Row(
        modifier
            .clip(PcFlow.formas.pilula)
            .background(cores.superficieElevada)
            .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.pilula)
            .padding(EspacoPcFlow.x4),
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x4)
    ) {
        for (opcao in opcoes) {
            val ativo = opcao == selecionado
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(PcFlow.formas.pilula)
                    .background(if (ativo) cores.acao else Color.Transparent)
                    .clickable { aoSelecionar(opcao) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rotulo(opcao),
                    color = if (ativo) cores.sobreAcao else cores.textoSecundario,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = EspacoPcFlow.x8)
                )
            }
        }
    }
}

/** Linha "nome … valor" dos painéis de estado. */
@Composable
fun LinhaValor(nome: String, valor: String, corValor: Color? = null) {
    val cores = PcFlow.cores
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(nome, color = cores.textoSecundario, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(valor, color = corValor ?: cores.texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Faixa curta que aparece e some, para confirmar o que acabou de acontecer. */
@Composable
fun BoxScope.AvisoSessao(texto: String?, alinhamento: Alignment = Alignment.BottomCenter) {
    val cores = PcFlow.cores
    AnimatedVisibility(
        visible = texto != null,
        modifier = Modifier.align(alinhamento).padding(EspacoPcFlow.x24),
        enter = fadeIn(tween(duracaoPcFlow(DURACAO_PAINEL_MS))),
        exit = fadeOut(tween(duracaoPcFlow(DURACAO_PAINEL_MS)))
    ) {
        Text(
            text = texto.orEmpty(),
            color = cores.texto,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(PcFlow.formas.pilula)
                .background(cores.superficieElevada.copy(alpha = 0.96f))
                .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.pilula)
                .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x8)
        )
    }
}

/** Pílula de estado do topo: o modo atual precisa estar sempre visível. */
@Composable
fun EtiquetaSessao(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color? = null,
    icone: Icone? = null
) {
    val cores = PcFlow.cores
    val tom = cor ?: cores.textoSecundario
    Row(
        modifier
            .clip(PcFlow.formas.pilula)
            .background(cores.superficie.copy(alpha = 0.92f))
            .border(DimensaoPcFlow.borda, cores.contornar(tom, 0.30f), PcFlow.formas.pilula)
            .padding(horizontal = EspacoPcFlow.x12, vertical = EspacoPcFlow.x8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        if (icone != null) IconePcFlow(icone, tom, tamanho = 14.dp)
        Text(texto, color = tom, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun EspacoLinha(altura: androidx.compose.ui.unit.Dp = EspacoPcFlow.x8) {
    Spacer(Modifier.height(altura).width(altura))
}
