package com.ander.pcflow.sessao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PerfilVideo
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.design.BotaoPrimario
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.BotaoPerigo
import com.ander.pcflow.design.CampoTexto
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.ItemLista
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.SeparadorPcFlow

// ------------------------------------------------------------------- entrada

/** Escolha do modo de entrada e os botões de mouse que gesto nenhum substitui. */
@Composable
fun BoxScope.PainelEntrada(
    visivel: Boolean,
    modo: ModoEntrada,
    aoMudarModo: (ModoEntrada) -> Unit,
    travaArraste: Boolean,
    aoMudarTrava: (Boolean) -> Unit,
    aoClicar: (BotaoMouse, Int) -> Unit,
    ampliada: Boolean,
    aoTamanhoNormal: () -> Unit,
    aoFechar: () -> Unit
) {
    val cores = PcFlow.cores
    PainelSessao(
        visivel = visivel,
        titulo = "Entrada",
        subtitulo = modo.explicacao,
        aoFechar = aoFechar
    ) {
        SeletorSegmentado(
            opcoes = ModoEntrada.entries.toList(),
            selecionado = modo,
            rotulo = { it.rotulo },
            aoSelecionar = aoMudarModo,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Toque = clique. Toque duplo = clique duplo. Pressão longa = " +
                if (modo == ModoEntrada.TOQUE_DIRETO) "clique direito." else "começar a arrastar.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Dois dedos rolam a página, para cima ou para o lado. Pinça amplia " +
                "só no celular, sem mexer no computador.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )

        SeparadorPcFlow()

        Text("Botões do mouse", color = cores.textoSecundario, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
            TeclaSessao("Esquerdo", { aoClicar(BotaoMouse.ESQUERDO, 1) }, Modifier.weight(1f))
            TeclaSessao("Direito", { aoClicar(BotaoMouse.DIREITO, 1) }, Modifier.weight(1f))
            TeclaSessao("Meio", { aoClicar(BotaoMouse.MEIO, 1) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
            TeclaSessao("Clique duplo", { aoClicar(BotaoMouse.ESQUERDO, 2) }, Modifier.weight(1f))
            TeclaSessao(
                rotulo = if (travaArraste) "Soltar o arrasto" else "Travar arrasto",
                aoTocar = { aoMudarTrava(!travaArraste) },
                modifier = Modifier.weight(1f),
                travada = travaArraste
            )
        }
        Text(
            if (travaArraste) "O botão esquerdo está preso: arraste com o dedo e toque de novo para soltar."
            else "A trava prende o botão esquerdo para arrastar sem precisar segurar o dedo.",
            color = if (travaArraste) cores.acao else cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall
        )

        if (ampliada) {
            BotaoSecundario(
                texto = "Voltar ao tamanho normal",
                aoClicar = aoTamanhoNormal,
                icone = Icone.EXPANDIR
            )
        }
    }
}

// ------------------------------------------------------------------ monitores

@Composable
fun BoxScope.PainelMonitores(
    visivel: Boolean,
    quantidade: Int,
    atual: Int,
    aoEscolher: (Int) -> Unit,
    aoFechar: () -> Unit
) {
    PainelSessao(
        visivel = visivel,
        titulo = "Monitores",
        subtitulo = if (quantidade > 1) "Escolha qual tela do computador aparece aqui."
        else "Este computador informou apenas uma tela.",
        aoFechar = aoFechar
    ) {
        for (indice in 0 until quantidade.coerceAtLeast(1)) {
            ItemLista(
                titulo = "Monitor ${indice + 1}",
                subtitulo = if (indice == atual) "Em exibição" else null,
                icone = Icone.COMPUTADOR,
                selecionado = indice == atual,
                aoClicar = { aoEscolher(indice) }
            )
        }
    }
}

// -------------------------------------------------------- área de transferência

@Composable
fun BoxScope.PainelClipboard(
    visivel: Boolean,
    permitido: Boolean,
    aoAvisar: (String) -> Unit,
    aoFechar: () -> Unit
) {
    val cores = PcFlow.cores
    val areaDoCelular = LocalClipboardManager.current
    val remoto by SessaoPcFlow.clipboardRemoto.collectAsStateWithLifecycle()
    val modo by SessaoPcFlow.modoClipboard.collectAsStateWithLifecycle()
    var rascunho by remember { mutableStateOf("") }
    var editado by remember { mutableStateOf(false) }

    // Enquanto a pessoa não mexeu no texto, o campo espelha o que veio do PC.
    LaunchedEffect(remoto, visivel) {
        if (visivel && !editado) rascunho = remoto
    }
    LaunchedEffect(visivel) { if (visivel && permitido) SessaoPcFlow.puxarClipboardDoPc() }

    PainelSessao(
        visivel = visivel,
        titulo = "Área de transferência",
        subtitulo = if (permitido) "Copie e cole entre o celular e o computador."
        else "O computador está com a área de transferência bloqueada.",
        aoFechar = aoFechar
    ) {
        SeletorSegmentado(
            opcoes = listOf(ModoClipboard.DESLIGADO, ModoClipboard.AUTOMATICO, ModoClipboard.MANUAL),
            selecionado = modo,
            rotulo = {
                when (it) {
                    ModoClipboard.DESLIGADO -> "Desligado"
                    ModoClipboard.AUTOMATICO -> "Automático"
                    ModoClipboard.MANUAL -> "Manual"
                }
            },
            aoSelecionar = { SessaoPcFlow.definirModoClipboard(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            when (modo) {
                ModoClipboard.DESLIGADO -> "Nada é trocado sem você pedir aqui."
                ModoClipboard.AUTOMATICO -> "O computador avisa sozinho quando o texto copiado lá muda."
                ModoClipboard.MANUAL -> "A troca acontece só nos botões abaixo."
            },
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )

        CampoTexto(
            valor = rascunho,
            aoMudar = { rascunho = it; editado = true },
            rotulo = "Texto",
            dica = "Sem texto ainda — traga do PC ou escreva aqui",
            linhaUnica = false,
            minLinhas = 3,
            habilitado = permitido,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
            Column(Modifier.weight(1f)) {
                BotaoSecundario(
                    texto = "Trazer do PC",
                    aoClicar = { editado = false; SessaoPcFlow.puxarClipboardDoPc(); aoAvisar("Pedindo o texto do computador…") },
                    icone = Icone.BAIXAR,
                    habilitado = permitido,
                    motivoDesabilitado = "O computador bloqueou a área de transferência."
                )
            }
            Column(Modifier.weight(1f)) {
                BotaoPrimario(
                    texto = "Mandar para o PC",
                    aoClicar = {
                        SessaoPcFlow.enviarClipboardParaPc(rascunho)
                        editado = false
                        aoAvisar("Texto colocado na área de transferência do PC")
                    },
                    icone = Icone.ENVIAR,
                    habilitado = permitido && rascunho.isNotEmpty(),
                    motivoDesabilitado = if (!permitido) "O computador bloqueou a área de transferência."
                    else "Escreva ou traga um texto primeiro."
                )
            }
        }

        BotaoSecundario(
            texto = "Colar no PC agora (Ctrl+V)",
            aoClicar = {
                if (rascunho.isNotEmpty()) SessaoPcFlow.enviarClipboardParaPc(rascunho)
                SessaoPcFlow.teclaEspecial("V", listOf("ctrl"))
                aoAvisar("Colado no computador")
            },
            icone = Icone.PRANCHETA,
            habilitado = permitido,
            motivoDesabilitado = "O computador bloqueou a área de transferência."
        )

        SeparadorPcFlow()

        Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
            Column(Modifier.weight(1f)) {
                BotaoSecundario(
                    texto = "Copiar no celular",
                    aoClicar = {
                        areaDoCelular.setText(AnnotatedString(rascunho))
                        aoAvisar("Copiado para o celular")
                    },
                    habilitado = rascunho.isNotEmpty(),
                    motivoDesabilitado = "Não há texto para copiar."
                )
            }
            Column(Modifier.weight(1f)) {
                BotaoSecundario(
                    texto = "Colar do celular",
                    aoClicar = {
                        val texto = areaDoCelular.getText()?.text.orEmpty()
                        if (texto.isEmpty()) aoAvisar("Não há texto copiado no celular")
                        else { rascunho = texto; editado = true }
                    }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ qualidade

@Composable
fun BoxScope.PainelQualidade(
    visivel: Boolean,
    aoAtualizarImagem: () -> Unit,
    aoFechar: () -> Unit
) {
    val cores = PcFlow.cores
    val perfil by SessaoPcFlow.perfilVideo.collectAsStateWithLifecycle()
    val estatisticas by SessaoPcFlow.estatisticas.collectAsStateWithLifecycle()

    PainelSessao(
        visivel = visivel,
        titulo = "Qualidade da imagem",
        subtitulo = "Menos nitidez responde mais rápido em Wi-Fi ruim.",
        aoFechar = aoFechar
    ) {
        SeletorSegmentado(
            opcoes = listOf(PerfilVideo.RESPOSTA, PerfilVideo.EQUILIBRADO, PerfilVideo.IMAGEM),
            selecionado = perfil,
            rotulo = { rotuloPerfil(it) },
            aoSelecionar = { SessaoPcFlow.definirPerfilVideo(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            when (perfil) {
                PerfilVideo.RESPOSTA -> "Até 30 imagens por segundo, resolução menor. O cursor anda junto com o dedo."
                PerfilVideo.EQUILIBRADO -> "24 imagens por segundo com boa leitura de texto."
                PerfilVideo.IMAGEM -> "Resolução cheia e mais nitidez, com menos imagens por segundo."
            },
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )

        SeparadorPcFlow()

        LinhaValor(
            "Imagens por segundo",
            if (estatisticas.quadrosPorSegundo > 0) "${estatisticas.quadrosPorSegundo}" else "—",
            corValor = if (estatisticas.quadrosPorSegundo >= 15) cores.seguro else cores.texto
        )
        LinhaValor(
            "Atraso até o computador",
            if (estatisticas.latenciaMs > 0) "${estatisticas.latenciaMs} ms" else "—",
            corValor = when {
                estatisticas.latenciaMs in 1..80 -> cores.seguro
                estatisticas.latenciaMs > 200 -> cores.erro
                else -> cores.texto
            }
        )
        LinhaValor("Dados recebidos", formatarTaxa(estatisticas.bytesPorSegundo))

        BotaoSecundario(
            texto = "Atualizar imagem",
            aoClicar = aoAtualizarImagem,
            icone = Icone.ATUALIZAR
        )
        Text(
            "Use quando a tela congelar: o computador manda um quadro novo do zero.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

fun rotuloPerfil(perfil: PerfilVideo): String = when (perfil) {
    PerfilVideo.RESPOSTA -> "Melhor resposta"
    PerfilVideo.EQUILIBRADO -> "Equilibrado"
    PerfilVideo.IMAGEM -> "Melhor imagem"
}

private fun formatarTaxa(bytesPorSegundo: Long): String = when {
    bytesPorSegundo <= 0 -> "—"
    bytesPorSegundo < 1024 -> "$bytesPorSegundo B/s"
    bytesPorSegundo < 1024 * 1024 -> "${bytesPorSegundo / 1024} kB/s"
    else -> String.format("%.1f MB/s", bytesPorSegundo / 1048576.0)
}

// --------------------------------------------------------------------- ações

@Composable
fun BoxScope.PainelAcoes(
    visivel: Boolean,
    ampliada: Boolean,
    aoAtualizarImagem: () -> Unit,
    aoTamanhoNormal: () -> Unit,
    aoAvisar: (String) -> Unit,
    aoFechar: () -> Unit
) {
    PainelSessao(
        visivel = visivel,
        titulo = "Ações",
        subtitulo = "Atalhos do Windows que valem um botão.",
        aoFechar = aoFechar
    ) {
        ItemLista(
            titulo = "Atualizar imagem",
            subtitulo = "Pede um quadro novo quando a tela congela",
            icone = Icone.ATUALIZAR,
            aoClicar = aoAtualizarImagem
        )
        ItemLista(
            titulo = "Tamanho normal",
            subtitulo = if (ampliada) "Desfaz a ampliação da pinça" else "A imagem já está no tamanho normal",
            icone = Icone.EXPANDIR,
            habilitado = ampliada,
            motivoDesabilitado = "A imagem já está no tamanho normal.",
            aoClicar = aoTamanhoNormal
        )
        for (atalho in CatalogoTeclas.atalhos.take(6)) {
            ItemLista(
                titulo = atalho.rotulo,
                subtitulo = atalho.observacao.ifBlank { null },
                icone = Icone.TECLADO,
                aoClicar = {
                    SessaoPcFlow.teclaEspecial(atalho.tecla, atalho.modificadores)
                    aoAvisar("${atalho.rotulo} enviado")
                }
            )
        }
        ItemLista(
            titulo = "Alternar janela",
            subtitulo = "Alt+Tab",
            icone = Icone.APRESENTACAO,
            aoClicar = { SessaoPcFlow.teclaEspecial("ALT_TAB"); aoAvisar("Alt+Tab enviado") }
        )
        ItemLista(
            titulo = "Mostrar a área de trabalho",
            subtitulo = "Win+D",
            icone = Icone.COMPUTADOR,
            aoClicar = { SessaoPcFlow.teclaEspecial("SHOW_DESKTOP"); aoAvisar("Win+D enviado") }
        )
    }
}

// ------------------------------------------------------------------ encerrar

@Composable
fun BoxScope.PainelEncerrar(
    visivel: Boolean,
    aoConfirmar: () -> Unit,
    aoFechar: () -> Unit
) {
    val cores = PcFlow.cores
    PainelSessao(
        visivel = visivel,
        titulo = "Encerrar a sessão?",
        aoFechar = aoFechar
    ) {
        Text(
            "A tela do computador para de aparecer aqui e nada fica pressionado lá. " +
                "O computador continua ligado.",
            color = cores.textoSecundario,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = EspacoPcFlow.x8)
        )
        BotaoSecundario(texto = "Continuar na sessão", aoClicar = aoFechar)
        BotaoPerigo(texto = "Encerrar", aoClicar = aoConfirmar, icone = Icone.SAIR)
    }
}
