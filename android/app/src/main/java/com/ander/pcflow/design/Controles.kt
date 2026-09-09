package com.ander.pcflow.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Interruptor do PCFlow. O do Material vem com outra linguagem de forma e de
 * cor; este segue a pílula e o dourado do sistema, e mantém o alvo de 48 dp.
 */
@Composable
fun InterruptorPcFlow(
    ligado: Boolean,
    aoMudar: (Boolean) -> Unit,
    descricao: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    val cores = PcFlow.cores
    val trilho by animateColorAsState(
        targetValue = when {
            !habilitado -> cores.superficie
            ligado -> cores.acao
            else -> cores.superficieElevada
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "trilhoInterruptor"
    )
    val botao by animateColorAsState(
        targetValue = when {
            !habilitado -> cores.textoDesabilitado
            ligado -> cores.sobreAcao
            else -> cores.textoSecundario
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "botaoInterruptor"
    )
    val deslocamento by animateDpAsState(
        targetValue = if (ligado) 22.dp else 4.dp,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "posicaoInterruptor"
    )

    Box(
        modifier
            .size(DimensaoPcFlow.alvoMinimo)
            .toggleable(
                value = ligado,
                enabled = habilitado,
                role = Role.Switch,
                onValueChange = aoMudar
            )
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(PcFlow.formas.pilula)
                .background(trilho)
                .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.pilula)
        ) {
            Box(
                Modifier
                    .padding(start = deslocamento)
                    .align(Alignment.CenterStart)
                    .size(18.dp)
                    .clip(PcFlow.formas.pilula)
                    .background(botao)
            )
        }
    }
}

/** Filtro em pílula, com a contagem do que ele mostra. */
@Composable
fun ChipPcFlow(
    texto: String,
    selecionado: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    contagem: Int? = null,
    icone: Icone? = null
) {
    val cores = PcFlow.cores
    val fundo by animateColorAsState(
        targetValue = if (selecionado) cores.tingir(cores.acao, 0.16f) else cores.superficie,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "fundoChip"
    )
    val contorno by animateColorAsState(
        targetValue = if (selecionado) cores.acao else cores.borda,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaChip"
    )
    val tom = if (selecionado) cores.acaoTexto else cores.textoSecundario
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(PcFlow.formas.pilula)
            .background(fundo)
            .border(DimensaoPcFlow.borda, contorno, PcFlow.formas.pilula)
            .selectable(selected = selecionado, role = Role.Tab, onClick = aoClicar)
            .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        if (icone != null) IconePcFlow(icone, tom, tamanho = 16.dp)
        Text(
            text = if (contagem != null) "$texto ($contagem)" else texto,
            color = tom,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

/** Campo de busca em pílula, com a lupa dentro e o alvo mínimo respeitado. */
@Composable
fun CampoBusca(
    valor: String,
    aoMudar: (String) -> Unit,
    dica: String,
    modifier: Modifier = Modifier,
    aoFinal: @Composable (RowScope.() -> Unit)? = null
) {
    val cores = PcFlow.cores
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    val contorno by animateColorAsState(
        targetValue = if (focado) cores.acao else cores.borda,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaBusca"
    )
    Row(
        modifier
            .heightIn(min = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.cartao)
            .background(cores.superficieElevada)
            .border(DimensaoPcFlow.borda, contorno, PcFlow.formas.cartao)
            .padding(horizontal = EspacoPcFlow.x16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        IconePcFlow(Icone.BUSCAR, cores.textoSecundario, tamanho = 20.dp)
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) {
                Text(
                    text = dica,
                    color = cores.textoDesabilitado,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cores.texto),
                cursorBrush = SolidColor(cores.acao),
                keyboardOptions = KeyboardOptions.Default,
                interactionSource = interacao,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = dica }
            )
        }
        if (aoFinal != null) aoFinal()
    }
}

/**
 * Uma escolha dentro de um grupo (tema, qualidade, modo). Linha inteira é
 * clicável, com o alvo cheio, e a marca de seleção é dourada.
 */
@Composable
fun OpcaoPcFlow(
    titulo: String,
    selecionado: Boolean,
    aoSelecionar: () -> Unit,
    modifier: Modifier = Modifier,
    descricao: String? = null,
    habilitado: Boolean = true,
    motivoDesabilitado: String? = null
) {
    val cores = PcFlow.cores
    val contorno by animateColorAsState(
        targetValue = if (selecionado) cores.acao else cores.borda,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaOpcao"
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.cartao)
            .background(if (selecionado) cores.tingir(cores.acao, 0.08f) else cores.superficie)
            .border(DimensaoPcFlow.borda, contorno, PcFlow.formas.cartao)
            .selectable(
                selected = selecionado,
                enabled = habilitado,
                role = Role.RadioButton,
                onClick = aoSelecionar
            )
            .padding(DimensaoPcFlow.folgaItem),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(PcFlow.formas.pilula)
                .background(if (selecionado) cores.acao else Color.Transparent)
                .border(
                    DimensaoPcFlow.borda,
                    if (selecionado) cores.acao else cores.bordaFoco,
                    PcFlow.formas.pilula
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selecionado) IconePcFlow(Icone.CHECK, cores.sobreAcao, tamanho = 12.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                color = if (habilitado) cores.texto else cores.textoDesabilitado,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!descricao.isNullOrBlank()) {
                Text(
                    text = descricao,
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!habilitado && !motivoDesabilitado.isNullOrBlank()) {
                Text(
                    text = motivoDesabilitado,
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Cabeçalho de seção com contagem — "Favoritos  (2)". A contagem é sempre a
 * quantidade listada logo abaixo, nunca um número inventado.
 */
@Composable
fun CabecalhoSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    icone: Icone? = null,
    corIcone: Color? = null,
    contagem: Int? = null,
    acao: @Composable (RowScope.() -> Unit)? = null
) {
    val cores = PcFlow.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = EspacoPcFlow.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        if (icone != null) IconePcFlow(icone, corIcone ?: cores.acaoTexto, tamanho = 20.dp)
        Text(
            text = titulo,
            color = cores.texto,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (contagem != null) {
            Text(
                text = "($contagem)",
                color = cores.textoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.weight(1f))
        if (acao != null) acao()
    }
}

/** Ponto de estado: turquesa quando online, cinza quando não. */
@Composable
fun PontoEstado(online: Boolean, modifier: Modifier = Modifier) {
    val cores = PcFlow.cores
    Box(
        modifier
            .size(8.dp)
            .clip(PcFlow.formas.pilula)
            .background(if (online) cores.seguro else cores.textoDesabilitado)
            .clearAndSetSemantics { }
    )
}

/** Estrela de favorito: marcada em dourado, vazia em contorno. */
@Composable
fun BotaoFavorito(
    favorito: Boolean,
    aoAlternar: () -> Unit,
    nomeDoDispositivo: String,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    Box(
        modifier
            .size(DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.pilula)
            .toggleable(
                value = favorito,
                role = Role.Checkbox,
                onValueChange = { aoAlternar() }
            )
            .semantics {
                contentDescription =
                    if (favorito) "Tirar $nomeDoDispositivo dos favoritos"
                    else "Marcar $nomeDoDispositivo como favorito"
            },
        contentAlignment = Alignment.Center
    ) {
        IconePcFlow(
            icone = if (favorito) Icone.ESTRELA_CHEIA else Icone.ESTRELA,
            cor = if (favorito) cores.acao else cores.textoSecundario,
            tamanho = 22.dp
        )
    }
}
