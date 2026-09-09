package com.ander.pcflow.telas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.design.BotaoIcone
import com.ander.pcflow.design.CampoTexto
import com.ander.pcflow.design.DimensaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.EstiloIdentidadeMenor
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.IconePcFlow
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.RaioPcFlow

/**
 * A marca do PCFlow, desenhada aqui mesmo: um monitor de traço dourado com o
 * cursor que dá nome ao produto. Nada de imagem importada — o mesmo desenho
 * serve em qualquer tamanho e acompanha a cor do tema.
 */
@Composable
fun MarcaPcFlow(modifier: Modifier = Modifier, tamanho: Dp = 40.dp) {
    val cores = PcFlow.cores
    Canvas(
        modifier
            .size(tamanho)
            .clearAndSetSemantics { }
    ) {
        val w = size.width
        val h = size.height
        val traco = w * 0.085f
        val estilo = Stroke(width = traco, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawRoundRect(
            color = cores.acao.copy(alpha = 0.12f),
            cornerRadius = CornerRadius(w * 0.26f)
        )
        drawRoundRect(
            color = cores.acao,
            topLeft = Offset(w * 0.14f, h * 0.20f),
            size = Size(w * 0.72f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.10f),
            style = estilo
        )
        drawLine(
            cores.acao,
            Offset(w * 0.50f, h * 0.66f),
            Offset(w * 0.50f, h * 0.78f),
            traco,
            StrokeCap.Round
        )
        drawLine(
            cores.acao,
            Offset(w * 0.32f, h * 0.80f),
            Offset(w * 0.68f, h * 0.80f),
            traco,
            StrokeCap.Round
        )
        // O cursor dentro da tela: é o que diferencia a marca de um ícone de monitor.
        drawPath(
            Path().apply {
                moveTo(w * 0.40f, h * 0.28f)
                lineTo(w * 0.62f, h * 0.44f)
                lineTo(w * 0.51f, h * 0.46f)
                lineTo(w * 0.56f, h * 0.58f)
                lineTo(w * 0.48f, h * 0.60f)
                lineTo(w * 0.44f, h * 0.48f)
                lineTo(w * 0.36f, h * 0.55f)
                close()
            },
            cores.acao
        )
    }
}

/** Cabeçalho fixo: menu à esquerda, marca no meio, ajustes à direita. */
@Composable
fun CabecalhoCasca(
    aoAbrirMenu: () -> Unit,
    aoAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = EspacoPcFlow.x8, vertical = EspacoPcFlow.x4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotaoIcone(Icone.MENU, "Abrir o menu do aplicativo", aoAbrirMenu)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
            ) {
                MarcaPcFlow(tamanho = 34.dp)
                Text(
                    "PCFlow",
                    color = cores.texto,
                    style = MaterialTheme.typography.headlineSmall
                )
                PilulaVersao()
            }
            Text(
                "Acesso remoto simples e seguro",
                color = cores.textoSecundario,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BotaoIcone(Icone.AJUSTES, "Abrir os ajustes", aoAbrirAjustes)
    }
}

@Composable
fun PilulaVersao(modifier: Modifier = Modifier) {
    val cores = PcFlow.cores
    Text(
        text = "v${SessaoPcFlow.VERSAO_APP}",
        color = cores.acaoTexto,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(PcFlow.formas.controle)
            .background(cores.tingir(cores.acao, 0.14f))
            .border(DimensaoPcFlow.borda, cores.contornar(cores.acao), PcFlow.formas.controle)
            .padding(horizontal = EspacoPcFlow.x8, vertical = 2.dp)
    )
}

/** Título e linha de apoio de cada destino. */
@Composable
fun TituloTela(titulo: String, apoio: String, modifier: Modifier = Modifier) {
    val cores = PcFlow.cores
    Column(modifier.fillMaxWidth()) {
        Text(titulo, color = cores.texto, style = MaterialTheme.typography.displaySmall)
        Text(apoio, color = cores.textoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Faixa de versão do rodapé. A coroa é o "uso pessoal" do mockup — e é só
 * isso mesmo: o PCFlow não tem conta, plano nem cobrança.
 */
@Composable
fun FaixaVersao(modifier: Modifier = Modifier) {
    val cores = PcFlow.cores
    Row(
        modifier
            .fillMaxWidth()
            .clip(PcFlow.formas.cartao)
            .background(cores.superficie)
            .border(DimensaoPcFlow.borda, cores.borda, PcFlow.formas.cartao)
            .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        IconePcFlow(Icone.COROA, cores.acaoTexto, tamanho = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(
                "PCFlow v${SessaoPcFlow.VERSAO_APP} · uso pessoal",
                color = cores.texto,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Sem conta, sem nuvem e sem coleta de dados.",
                color = cores.textoSecundario,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Miniatura do computador. O padrão vem do ID (determinístico), então a mesma
 * máquina tem sempre a mesma cara — decisão 4 de TELAS_V13.
 */
@Composable
fun MiniaturaDispositivo(
    identificador: String,
    modifier: Modifier = Modifier,
    largura: Dp = 76.dp,
    altura: Dp = 56.dp
) {
    val padrao = Miniatura.de(identificador)
    Canvas(
        modifier
            .size(largura, altura)
            .clip(PcFlow.formas.controle)
            .background(Color(padrao.corFundo))
            .border(DimensaoPcFlow.borda, PcFlow.cores.borda, PcFlow.formas.controle)
            .clearAndSetSemantics { }
    ) {
        val folga = size.minDimension * 0.16f
        val larguraUtil = size.width - folga * 2
        val alturaUtil = size.height - folga * 2
        val passoX = larguraUtil / PadraoMiniatura.COLUNAS
        val passoY = alturaUtil / PadraoMiniatura.LINHAS
        val corTraco = Color(padrao.corTraco)
        for (linha in 0 until PadraoMiniatura.LINHAS) {
            for (coluna in 0 until PadraoMiniatura.COLUNAS) {
                if (!padrao.aceso(coluna, linha)) continue
                drawRoundRect(
                    color = corTraco.copy(alpha = if ((linha + coluna) % 2 == 0) 0.95f else 0.65f),
                    topLeft = Offset(folga + coluna * passoX, folga + linha * passoY),
                    size = Size(passoX * 0.82f, passoY * 0.82f),
                    cornerRadius = CornerRadius(passoX * 0.28f)
                )
            }
        }
    }
}

/** Estado do computador em uma linha: ponto colorido e a palavra escrita. */
@Composable
fun EstadoDispositivo(online: Boolean, modifier: Modifier = Modifier) {
    val cores = PcFlow.cores
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(PcFlow.formas.pilula)
                .background(if (online) cores.seguro else cores.textoDesabilitado)
        )
        Text(
            text = if (online) "Online" else "Offline",
            color = if (online) cores.seguro else cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Campo de ID com a seta dourada ao lado, como no mockup. A seta fica
 * desabilitada com o motivo escrito enquanto o ID não estiver completo —
 * botão que não faz nada não existe no PCFlow.
 */
@Composable
fun CampoIdComAcao(
    valor: String,
    aoMudar: (String) -> Unit,
    aoConfirmar: () -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null
) {
    val cores = PcFlow.cores
    val validacao = Identidade.validarId(valor)
    val pronto = validacao.valido
    val apoio = when {
        !erro.isNullOrBlank() -> null
        valor.isEmpty() -> "O ID aparece na tela inicial do PCFlow do computador."
        else -> validacao.mensagem
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12),
            verticalAlignment = Alignment.Bottom
        ) {
            CampoTexto(
                valor = Identidade.formatarId(valor),
                aoMudar = { aoMudar(Identidade.aoDigitarId(it)) },
                rotulo = "ID do computador",
                modifier = Modifier.weight(1f),
                dica = "000 000 000",
                erro = erro,
                estiloValor = EstiloIdentidadeMenor,
                opcoesTeclado = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Box(
                Modifier
                    .padding(bottom = if (apoio != null || !erro.isNullOrBlank()) 21.dp else 0.dp)
                    .size(DimensaoPcFlow.alvoMinimo)
                    .clip(PcFlow.formas.cartao)
                    .background(if (pronto) cores.acao else cores.superficie)
                    .border(
                        DimensaoPcFlow.borda,
                        if (pronto) cores.acao else cores.borda,
                        PcFlow.formas.cartao
                    )
                    .semantics { contentDescription = "Conectar ao ID digitado" },
                contentAlignment = Alignment.Center
            ) {
                BotaoIcone(
                    icone = Icone.SETA_DIREITA,
                    descricao = "Conectar ao ID digitado",
                    aoClicar = aoConfirmar,
                    cor = if (pronto) cores.sobreAcao else cores.textoDesabilitado,
                    habilitado = pronto
                )
            }
        }
        if (apoio != null) {
            Spacer(Modifier.height(EspacoPcFlow.x4))
            Text(
                text = apoio,
                color = cores.textoSecundario,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Botão flutuante do mockup: círculo dourado com o alvo bem acima de 48 dp. */
@Composable
fun BotaoFlutuante(
    icone: Icone,
    descricao: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    Box(
        modifier
            .size(60.dp)
            .clip(PcFlow.formas.pilula)
            .background(cores.acao)
            .border(DimensaoPcFlow.borda, cores.contornar(cores.acao, 0.6f), PcFlow.formas.pilula),
        contentAlignment = Alignment.Center
    ) {
        BotaoIcone(
            icone = icone,
            descricao = descricao,
            aoClicar = aoClicar,
            cor = cores.sobreAcao
        )
    }
}

/** Espaço padrão entre blocos de uma tela. */
@Composable
fun Respiro(altura: Dp = EspacoPcFlow.x16) = Spacer(Modifier.height(altura))

@Composable
fun LarguraDe(largura: Dp) = Spacer(Modifier.width(largura))

/** Raio usado pelas miniaturas quando desenhadas fora de um cartão. */
val raioMiniatura: Dp get() = RaioPcFlow.controle
