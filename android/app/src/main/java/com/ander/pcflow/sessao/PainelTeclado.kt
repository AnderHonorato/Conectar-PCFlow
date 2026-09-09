package com.ander.pcflow.sessao

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.design.DimensaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.PcFlow

/** Como o teclado da sessão está sendo usado agora. */
enum class ModoTeclado(val rotulo: String) {
    AO_VIVO("Ao vivo"),
    ESCREVER("Escrever e enviar"),
    ESPECIAIS("Teclas e atalhos")
}

/**
 * Teclado da sessão.
 *
 * "Ao vivo" usa o teclado do próprio celular — com correção, acento, ç e emoji
 * — e manda ao PC só a diferença do que mudou no campo. "Escrever e enviar"
 * existe porque digitar letra por letra numa conexão lenta é sofrido: dá para
 * redigir com calma e mandar tudo de uma vez. "Teclas e atalhos" cobre o que
 * teclado de celular não tem: F5, setas, Ctrl+C, Alt+Tab.
 */
@Composable
fun BoxScope.PainelTeclado(
    visivel: Boolean,
    aoFechar: () -> Unit,
    aoAvisar: (String) -> Unit
) {
    val cores = PcFlow.cores
    var modo by remember { mutableStateOf(ModoTeclado.AO_VIVO) }
    val trava = remember { TravaModificadores() }
    var revisao by remember { mutableIntStateOf(0) }
    val tecladoDoSistema = LocalSoftwareKeyboardController.current

    // Modificador nunca fica preso: fechar o teclado zera a trava. Como o
    // modificador viaja junto da tecla, não existe Ctrl pendurado no PC.
    LaunchedEffect(visivel) {
        if (!visivel) {
            trava.limpar()
            revisao++
            tecladoDoSistema?.hide()
        }
    }

    fun acionar(tecla: String, extras: List<String> = emptyList()) {
        val modificadores = (trava.nomes() + extras).distinct()
        SessaoPcFlow.teclaEspecial(tecla, modificadores)
        trava.consumir()
        revisao++
    }

    PainelSessao(
        visivel = visivel,
        titulo = "Teclado",
        subtitulo = when (modo) {
            ModoTeclado.AO_VIVO -> "Cada tecla vai direto para o computador."
            ModoTeclado.ESCREVER -> "Escreva com calma e mande tudo de uma vez."
            ModoTeclado.ESPECIAIS -> "O que o teclado do celular não tem."
        },
        aoFechar = aoFechar
    ) {
        SeletorSegmentado(
            opcoes = ModoTeclado.entries.toList(),
            selecionado = modo,
            rotulo = { it.rotulo },
            aoSelecionar = { modo = it },
            modifier = Modifier.fillMaxWidth()
        )

        // Lida para forçar a recomposição quando a trava muda de estado.
        val marcaRevisao = revisao
        LinhaModificadores(trava, marcaRevisao) { revisao++ }

        when (modo) {
            ModoTeclado.AO_VIVO -> CampoAoVivo(
                ativo = visivel,
                modificadoresAtivos = { trava.nomes() },
                aoUsarModificadores = { trava.consumir(); revisao++ }
            )
            ModoTeclado.ESCREVER -> CampoEscreverEnviar(aoAvisar = aoAvisar)
            ModoTeclado.ESPECIAIS -> Unit
        }

        LinhaDeTeclas(CatalogoTeclas.navegacao) { acionar(it) }
        LinhaDeTeclas(CatalogoTeclas.setas) { acionar(it) }

        if (modo == ModoTeclado.ESPECIAIS) {
            LinhaDeTeclas(CatalogoTeclas.paginacao) { acionar(it) }
            LinhaDeTeclas(CatalogoTeclas.sistema) { acionar(it) }
            LinhaDeTeclas(CatalogoTeclas.funcao.subList(0, 4)) { acionar(it) }
            LinhaDeTeclas(CatalogoTeclas.funcao.subList(4, 8)) { acionar(it) }
            LinhaDeTeclas(CatalogoTeclas.funcao.subList(8, 12)) { acionar(it) }

            Text(
                "Atalhos prontos",
                color = cores.textoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            for (par in CatalogoTeclas.atalhos.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
                    for (atalho in par) {
                        Column(Modifier.weight(1f)) {
                            TeclaSessao(
                                rotulo = atalho.rotulo,
                                modifier = Modifier.fillMaxWidth(),
                                aoTocar = {
                                    acionar(atalho.tecla, atalho.modificadores)
                                    aoAvisar(atalho.rotulo + " enviado")
                                }
                            )
                            if (atalho.observacao.isNotBlank()) {
                                Text(
                                    atalho.observacao,
                                    color = cores.textoSecundario,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = EspacoPcFlow.x4)
                                )
                            }
                        }
                    }
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LinhaModificadores(trava: TravaModificadores, revisao: Int, aoMudar: () -> Unit) {
    val cores = PcFlow.cores
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
            for (modificador in Modificador.entries) {
                val estado = trava.estado(modificador)
                TeclaSessao(
                    rotulo = modificador.rotulo,
                    modifier = Modifier.weight(1f),
                    destaque = estado == EstadoModificador.ARMADO,
                    travada = estado == EstadoModificador.FIXO,
                    aoTocar = { trava.alternar(modificador); aoMudar() },
                    aoPressionarLongo = { trava.fixar(modificador); aoMudar() }
                )
            }
        }
        Text(
            text = if (revisao >= 0 && trava.algumAtivo)
                "Vale para a próxima tecla. Pressão longa trava até você desligar."
            else "Toque para usar na próxima tecla; segure para travar.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = EspacoPcFlow.x4)
        )
    }
}

@Composable
private fun LinhaDeTeclas(teclas: List<TeclaEspecial>, aoAcionar: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
    ) {
        for (tecla in teclas) {
            TeclaSessao(
                rotulo = tecla.rotulo,
                modifier = Modifier.weight(tecla.largura),
                aoTocar = { aoAcionar(tecla.nome) }
            )
        }
    }
}

/**
 * Digitação ao vivo.
 *
 * O campo é um espelho do que já foi mandado. A cada mudança sai só a
 * diferença: mandar o texto inteiro duplicaria tudo, e mandar o último
 * caractere quebraria com IME, que reescreve a palavra em composição inteira.
 */
@Composable
private fun CampoAoVivo(
    ativo: Boolean,
    modificadoresAtivos: () -> List<String>,
    aoUsarModificadores: () -> Unit
) {
    val cores = PcFlow.cores
    var valor by remember { mutableStateOf(TextFieldValue("")) }
    var espelho by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    val tecladoDoSistema = LocalSoftwareKeyboardController.current

    LaunchedEffect(ativo) {
        if (ativo) {
            valor = TextFieldValue("")
            espelho = ""
            foco.requestFocus()
            tecladoDoSistema?.show()
        }
    }

    fun enviar(acoes: List<AcaoTeclado>) {
        val modificadores = modificadoresAtivos()
        // Ctrl armado + uma letra é o Ctrl+C de sempre; sem isso, atalho no
        // teclado do celular só existiria pela lista de atalhos prontos.
        val unica = acoes.singleOrNull()
        if (modificadores.isNotEmpty() && unica is AcaoTeclado.Digitar &&
            unica.texto.length == 1 && unica.texto[0].isLetterOrDigit()
        ) {
            SessaoPcFlow.teclaEspecial(unica.texto.uppercase(), modificadores)
            aoUsarModificadores()
            return
        }
        for (acao in acoes) when (acao) {
            is AcaoTeclado.Digitar -> SessaoPcFlow.digitar(acao.texto)
            is AcaoTeclado.Apagar -> repeat(acao.quantidade) { SessaoPcFlow.teclaEspecial("BACKSPACE") }
            is AcaoTeclado.Tecla -> SessaoPcFlow.teclaEspecial(acao.nome)
        }
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DimensaoPcFlow.alvoMinimo)
                .clip(PcFlow.formas.cartao)
                .background(cores.superficieElevada)
                .border(DimensaoPcFlow.borda, cores.acao, PcFlow.formas.cartao)
                .padding(horizontal = EspacoPcFlow.x16, vertical = EspacoPcFlow.x12)
        ) {
            if (valor.text.isEmpty()) {
                Text(
                    "Digite aqui — vai direto para o PC",
                    color = cores.textoDesabilitado,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            BasicTextField(
                value = valor,
                onValueChange = { novo ->
                    enviar(calcularDeltaTexto(espelho, novo.text))
                    espelho = novo.text
                    valor = novo
                    // Zerar durante a composição faz o IME repetir letra.
                    if (deveZerarCampoAoVivo(novo.text, novo.composition != null)) {
                        valor = TextFieldValue("")
                        espelho = ""
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cores.texto),
                cursorBrush = SolidColor(cores.acao),
                singleLine = false,
                maxLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(foco)
                    // Com o campo vazio o IME não tem o que apagar e a tecla
                    // some; aqui ela vira BACKSPACE no PC do mesmo jeito.
                    .onPreviewKeyEvent { evento ->
                        if (evento.type == KeyEventType.KeyDown &&
                            evento.key == Key.Backspace && valor.text.isEmpty()
                        ) {
                            SessaoPcFlow.teclaEspecial("BACKSPACE")
                            true
                        } else false
                    }
            )
        }
        Text(
            "O que aparece aqui já chegou no computador.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = EspacoPcFlow.x4)
        )
    }
}

@Composable
private fun CampoEscreverEnviar(aoAvisar: (String) -> Unit) {
    val cores = PcFlow.cores
    var texto by remember { mutableStateOf("") }

    fun enviar(comEnter: Boolean) {
        if (texto.isBlank() && !comEnter) return
        for (acao in acoesDeTextoLivre(texto)) when (acao) {
            is AcaoTeclado.Digitar -> SessaoPcFlow.digitar(acao.texto)
            is AcaoTeclado.Apagar -> repeat(acao.quantidade) { SessaoPcFlow.teclaEspecial("BACKSPACE") }
            is AcaoTeclado.Tecla -> SessaoPcFlow.teclaEspecial(acao.nome)
        }
        if (comEnter) SessaoPcFlow.teclaEspecial("ENTER")
        aoAvisar(if (comEnter) "Texto enviado com Enter" else "Texto enviado")
        texto = ""
    }

    Column {
        com.ander.pcflow.design.CampoTexto(
            valor = texto,
            aoMudar = { texto = it },
            rotulo = "Texto para o computador",
            dica = "Escreva aqui e toque em Enviar",
            linhaUnica = false,
            minLinhas = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.width(EspacoPcFlow.x8))
        Row(
            Modifier.padding(top = EspacoPcFlow.x8),
            horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
        ) {
            BotaoLinha("Enviar", Modifier.weight(1f), habilitado = texto.isNotEmpty()) { enviar(false) }
            BotaoLinha("Enviar e Enter", Modifier.weight(1f), habilitado = texto.isNotEmpty()) { enviar(true) }
            BotaoSessao(
                icone = Icone.LIXEIRA,
                descricao = "Limpar o texto",
                cor = cores.textoSecundario,
                aoTocar = { texto = "" }
            )
        }
    }
}

@Composable
private fun BotaoLinha(
    rotulo: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean,
    aoTocar: () -> Unit
) {
    val cores = PcFlow.cores
    Box(
        modifier
            .heightIn(min = DimensaoPcFlow.alvoMinimo)
            .clip(PcFlow.formas.cartao)
            .background(if (habilitado) cores.acao else cores.superficieElevada)
            .border(DimensaoPcFlow.borda, if (habilitado) cores.acao else cores.borda, PcFlow.formas.cartao)
            .clickable(enabled = habilitado, onClick = aoTocar),
        contentAlignment = Alignment.Center
    ) {
        Text(
            rotulo,
            color = if (habilitado) cores.sobreAcao else cores.textoDesabilitado,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = EspacoPcFlow.x12)
        )
    }
}
