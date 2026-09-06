package com.ander.pcflow.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- superfícies

/**
 * Bloco de conteúdo do PCFlow: fundo de superfície, borda de 1px e canto de 14.
 * A profundidade vem da diferença entre fundo e superfície, não de sombra.
 */
@Composable
fun CartaoPcFlow(
    modifier: Modifier = Modifier,
    aoClicar: (() -> Unit)? = null,
    habilitado: Boolean = true,
    selecionado: Boolean = false,
    rotuloAcessivel: String? = null,
    folga: androidx.compose.ui.unit.Dp = DimensaoPcFlow.folgaItem,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val cores = PcFlow.cores
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val focado by interacao.collectIsFocusedAsState()

    val fundo by animateColorAsState(
        targetValue = when {
            !habilitado -> cores.fundo
            pressionado -> cores.superficieElevada
            else -> cores.superficie
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "fundoCartao"
    )
    val borda by animateColorAsState(
        targetValue = when {
            selecionado -> cores.acao
            focado -> cores.bordaFoco
            else -> cores.borda
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaCartao"
    )

    val base = modifier
        .clip(PcFlow.formas.cartao)
        .background(fundo)
        .border(DimensaoPcFlow.borda, borda, PcFlow.formas.cartao)
    val clicavel = if (aoClicar != null) base.clickable(
        interactionSource = interacao,
        indication = null,
        enabled = habilitado,
        onClickLabel = rotuloAcessivel,
        role = Role.Button,
        onClick = aoClicar
    ) else base

    Column(clicavel.padding(folga), content = conteudo)
}

/** Linha fina de separação dentro de um painel. */
@Composable
fun SeparadorPcFlow(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(DimensaoPcFlow.borda)
            .background(PcFlow.cores.borda)
    )
}

// ------------------------------------------------------------------- rótulos

/** Título de um grupo de conteúdo. */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier, cor: Color? = null) {
    Text(
        text = texto,
        modifier = modifier,
        color = cor ?: PcFlow.cores.textoSecundario,
        style = MaterialTheme.typography.bodyMedium
    )
}

/** Etiqueta curta de estado. Dourado, turquesa ou vermelho — nunca decorativa. */
@Composable
fun Pilula(
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
            .background(cores.tingir(tom))
            .border(DimensaoPcFlow.borda, cores.contornar(tom), PcFlow.formas.pilula)
            .padding(horizontal = EspacoPcFlow.x12, vertical = EspacoPcFlow.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        if (icone != null) IconePcFlow(icone, tom, tamanho = 14.dp)
        Text(texto, color = tom, style = MaterialTheme.typography.labelSmall)
    }
}

/** Número de identidade (ID do PC, código de acesso) em grupos legíveis. */
@Composable
fun NumeroIdentidade(
    texto: String,
    modifier: Modifier = Modifier,
    grande: Boolean = false,
    cor: Color? = null
) {
    Text(
        text = texto,
        modifier = modifier,
        color = cor ?: PcFlow.cores.acao,
        style = if (grande) EstiloIdentidade else EstiloIdentidadeMenor
    )
}

// -------------------------------------------------------------------- botões

@Composable
private fun BotaoBase(
    texto: String,
    aoClicar: () -> Unit,
    corFundo: Color,
    corFundoPressionada: Color,
    corTexto: Color,
    corBorda: Color?,
    modifier: Modifier,
    icone: Icone?,
    habilitado: Boolean,
    carregando: Boolean,
    textoCarregando: String?
) {
    val cores = PcFlow.cores
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val focado by interacao.collectIsFocusedAsState()
    val ativo = habilitado && !carregando

    val fundo by animateColorAsState(
        targetValue = when {
            !ativo -> cores.superficie
            pressionado -> corFundoPressionada
            else -> corFundo
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "fundoBotao"
    )
    val conteudo = if (ativo) corTexto else cores.textoDesabilitado
    val contorno by animateColorAsState(
        targetValue = when {
            !ativo -> cores.borda
            focado -> cores.acao
            corBorda != null -> corBorda
            else -> corFundo
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaBotao"
    )

    Row(
        modifier
            .heightIn(min = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.cartao)
            .background(fundo)
            .border(DimensaoPcFlow.borda, contorno, PcFlow.formas.cartao)
            .clickable(
                interactionSource = interacao,
                indication = null,
                enabled = ativo,
                role = Role.Button,
                onClick = aoClicar
            )
            .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x12),
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            carregando -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = conteudo,
                strokeWidth = 2.dp
            )
            icone != null -> IconePcFlow(icone, conteudo, tamanho = 18.dp)
        }
        Text(
            text = if (carregando) textoCarregando ?: texto else texto,
            color = conteudo,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

/** Ação principal da tela. Dourado — só uma por tela. */
@Composable
fun BotaoPrimario(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: Icone? = null,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    textoCarregando: String? = null,
    motivoDesabilitado: String? = null
) {
    val cores = PcFlow.cores
    Column(modifier) {
        BotaoBase(
            texto = texto,
            aoClicar = aoClicar,
            corFundo = cores.acao,
            corFundoPressionada = cores.acaoPressionada,
            corTexto = cores.sobreAcao,
            corBorda = null,
            modifier = Modifier.fillMaxWidth(),
            icone = icone,
            habilitado = habilitado,
            carregando = carregando,
            textoCarregando = textoCarregando
        )
        MotivoDesabilitado(habilitado, motivoDesabilitado)
    }
}

/** Ação de apoio: mesmo peso de leitura, sem competir com o dourado. */
@Composable
fun BotaoSecundario(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: Icone? = null,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    textoCarregando: String? = null,
    motivoDesabilitado: String? = null
) {
    val cores = PcFlow.cores
    Column(modifier) {
        BotaoBase(
            texto = texto,
            aoClicar = aoClicar,
            corFundo = cores.superficie,
            corFundoPressionada = cores.superficieElevada,
            corTexto = cores.texto,
            corBorda = cores.borda,
            modifier = Modifier.fillMaxWidth(),
            icone = icone,
            habilitado = habilitado,
            carregando = carregando,
            textoCarregando = textoCarregando
        )
        MotivoDesabilitado(habilitado, motivoDesabilitado)
    }
}

/** Ação destrutiva. Vermelho só aqui e em erro. */
@Composable
fun BotaoPerigo(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: Icone? = null,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    textoCarregando: String? = null,
    motivoDesabilitado: String? = null
) {
    val cores = PcFlow.cores
    Column(modifier) {
        BotaoBase(
            texto = texto,
            aoClicar = aoClicar,
            corFundo = cores.tingir(cores.erro, 0.12f),
            corFundoPressionada = cores.tingir(cores.erro, 0.24f),
            corTexto = cores.erro,
            corBorda = cores.contornar(cores.erro, 0.55f),
            modifier = Modifier.fillMaxWidth(),
            icone = icone,
            habilitado = habilitado,
            carregando = carregando,
            textoCarregando = textoCarregando
        )
        MotivoDesabilitado(habilitado, motivoDesabilitado)
    }
}

/** Botão desabilitado sem motivo escrito é proibido pelo contrato. */
@Composable
private fun MotivoDesabilitado(habilitado: Boolean, motivo: String?) {
    if (!habilitado && !motivo.isNullOrBlank()) {
        Text(
            text = motivo,
            color = PcFlow.cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = EspacoPcFlow.x4)
        )
    }
}

/** Botão só de ícone, para barra de cabeçalho. Mantém o alvo de 48 dp. */
@Composable
fun BotaoIcone(
    icone: Icone,
    descricao: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color? = null,
    habilitado: Boolean = true
) {
    val cores = PcFlow.cores
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val fundo by animateColorAsState(
        targetValue = if (pressionado) cores.superficieElevada else Color.Transparent,
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "fundoBotaoIcone"
    )
    Box(
        modifier
            .sizeIn(minWidth = DimensaoPcFlow.alvoMinimo, minHeight = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.pilula)
            .background(fundo)
            .clickable(
                interactionSource = interacao,
                indication = null,
                enabled = habilitado,
                role = Role.Button,
                onClickLabel = descricao,
                onClick = aoClicar
            ),
        contentAlignment = Alignment.Center
    ) {
        IconePcFlow(
            icone = icone,
            cor = if (habilitado) cor ?: cores.texto else cores.textoDesabilitado,
            tamanho = 22.dp,
            descricao = descricao
        )
    }
}

// -------------------------------------------------------------------- campos

@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    dica: String = "",
    apoio: String? = null,
    erro: String? = null,
    habilitado: Boolean = true,
    linhaUnica: Boolean = true,
    minLinhas: Int = 1,
    estiloValor: TextStyle? = null,
    opcoesTeclado: KeyboardOptions = KeyboardOptions.Default,
    transformacao: VisualTransformation = VisualTransformation.None,
    aoFinal: @Composable (() -> Unit)? = null
) {
    val cores = PcFlow.cores
    val interacao = remember { MutableInteractionSource() }
    val focado by interacao.collectIsFocusedAsState()
    val contorno by animateColorAsState(
        targetValue = when {
            !erro.isNullOrBlank() -> cores.erro
            !habilitado -> cores.borda
            focado -> cores.acao
            else -> cores.borda
        },
        animationSpec = especPcFlow(DURACAO_CONTROLE_MS),
        label = "bordaCampo"
    )
    val corTexto = if (habilitado) cores.texto else cores.textoDesabilitado

    Column(modifier) {
        Rotulo(rotulo)
        Spacer(Modifier.height(EspacoPcFlow.x4))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DimensaoPcFlow.alvoMinimo)
                .clip(PcFlow.formas.cartao)
                .background(if (habilitado) cores.superficieElevada else cores.superficie)
                .border(DimensaoPcFlow.borda, contorno, PcFlow.formas.cartao)
                .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty() && dica.isNotEmpty()) {
                    Text(
                        text = dica,
                        color = cores.textoDesabilitado,
                        style = estiloValor ?: MaterialTheme.typography.bodyLarge
                    )
                }
                BasicTextField(
                    value = valor,
                    onValueChange = aoMudar,
                    enabled = habilitado,
                    singleLine = linhaUnica,
                    minLines = if (linhaUnica) 1 else minLinhas,
                    textStyle = (estiloValor ?: MaterialTheme.typography.bodyLarge).copy(color = corTexto),
                    cursorBrush = SolidColor(cores.acao),
                    keyboardOptions = opcoesTeclado,
                    visualTransformation = transformacao,
                    interactionSource = interacao,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (aoFinal != null) {
                Spacer(Modifier.width(EspacoPcFlow.x8))
                aoFinal()
            }
        }
        val mensagem = erro?.takeIf { it.isNotBlank() } ?: apoio?.takeIf { it.isNotBlank() }
        if (mensagem != null) {
            Text(
                text = mensagem,
                color = if (!erro.isNullOrBlank()) cores.erro else cores.textoSecundario,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = EspacoPcFlow.x4)
            )
        }
    }
}

@Composable
fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    dica: String = "",
    apoio: String? = null,
    erro: String? = null,
    habilitado: Boolean = true
) {
    var visivel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    CampoTexto(
        valor = valor,
        aoMudar = aoMudar,
        rotulo = rotulo,
        modifier = modifier,
        dica = dica,
        apoio = apoio,
        erro = erro,
        habilitado = habilitado,
        opcoesTeclado = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
        ),
        transformacao = if (visivel) VisualTransformation.None
        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        aoFinal = {
            BotaoIcone(
                icone = if (visivel) Icone.OLHO_FECHADO else Icone.OLHO,
                descricao = if (visivel) "Ocultar senha" else "Mostrar senha",
                aoClicar = { visivel = !visivel },
                cor = PcFlow.cores.textoSecundario,
                habilitado = habilitado
            )
        }
    )
}

// ------------------------------------------------------------ itens de lista

@Composable
fun ItemLista(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    detalhe: String? = null,
    icone: Icone? = null,
    corIcone: Color? = null,
    descricaoIcone: String? = null,
    selecionado: Boolean = false,
    habilitado: Boolean = true,
    motivoDesabilitado: String? = null,
    aoClicar: (() -> Unit)? = null,
    fim: @Composable (RowScope.() -> Unit)? = null
) {
    val cores = PcFlow.cores
    CartaoPcFlow(
        modifier = modifier.fillMaxWidth(),
        aoClicar = aoClicar?.takeIf { habilitado },
        habilitado = habilitado,
        selecionado = selecionado,
        rotuloAcessivel = titulo
    ) {
        Row(
            Modifier.heightIn(min = DimensaoPcFlow.alvoMinimo),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
        ) {
            if (icone != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(PcFlow.formas.controle)
                        .background(cores.superficieElevada),
                    contentAlignment = Alignment.Center
                ) {
                    IconePcFlow(
                        icone = icone,
                        cor = if (habilitado) corIcone ?: cores.acao else cores.textoDesabilitado,
                        tamanho = 22.dp,
                        descricao = descricaoIcone
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    color = if (habilitado) cores.texto else cores.textoDesabilitado,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!subtitulo.isNullOrBlank()) {
                    Text(
                        text = subtitulo,
                        color = cores.textoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!detalhe.isNullOrBlank()) {
                    Text(
                        text = detalhe,
                        color = cores.textoSecundario,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (!habilitado && !motivoDesabilitado.isNullOrBlank()) {
                    Text(
                        text = motivoDesabilitado,
                        color = cores.textoSecundario,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = EspacoPcFlow.x4)
                    )
                }
            }
            if (fim != null) fim()
        }
    }
}

// --------------------------------------------------------- estados de tela

@Composable
fun EstadoCarregando(
    mensagem: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(EspacoPcFlow.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = PcFlow.cores.acao,
            strokeWidth = 3.dp
        )
        Text(
            text = mensagem,
            color = PcFlow.cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EstadoVazio(
    titulo: String,
    descricao: String,
    modifier: Modifier = Modifier,
    icone: Icone = Icone.INFO,
    textoAcao: String? = null,
    aoAgir: (() -> Unit)? = null
) {
    EstadoBase(
        modifier = modifier,
        icone = icone,
        corIcone = PcFlow.cores.textoSecundario,
        titulo = titulo,
        descricao = descricao,
        textoAcao = textoAcao,
        aoAgir = aoAgir
    )
}

@Composable
fun EstadoErro(
    titulo: String,
    descricao: String,
    modifier: Modifier = Modifier,
    textoAcao: String? = null,
    aoAgir: (() -> Unit)? = null
) {
    EstadoBase(
        modifier = modifier,
        icone = Icone.ALERTA,
        corIcone = PcFlow.cores.erro,
        titulo = titulo,
        descricao = descricao,
        textoAcao = textoAcao,
        aoAgir = aoAgir
    )
}

@Composable
private fun EstadoBase(
    modifier: Modifier,
    icone: Icone,
    corIcone: Color,
    titulo: String,
    descricao: String,
    textoAcao: String?,
    aoAgir: (() -> Unit)?
) {
    val cores = PcFlow.cores
    Column(
        modifier
            .fillMaxWidth()
            .padding(EspacoPcFlow.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(PcFlow.formas.pilula)
                .background(cores.tingir(corIcone, 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            IconePcFlow(icone, corIcone, tamanho = 26.dp)
        }
        Text(
            text = titulo,
            color = cores.texto,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = descricao,
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (textoAcao != null && aoAgir != null) {
            Spacer(Modifier.height(EspacoPcFlow.x4))
            BotaoSecundario(
                texto = textoAcao,
                aoClicar = aoAgir,
                modifier = Modifier.widthIn(min = 180.dp)
            )
        }
    }
}
