package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PerfilVideo
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.CampoTexto
import com.ander.pcflow.design.CartaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.OpcaoPcFlow
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.Rotulo
import com.ander.pcflow.design.TomAviso

/**
 * Ajustes: só o que muda comportamento de verdade.
 *
 * Cada escolha aqui é gravada e aplicada na hora — não existe botão de salvar,
 * e não existe interruptor decorativo. Sem telemetria, por decisão de produto.
 */
@Composable
fun TelaAjustes(
    ajustes: AjustesCasca,
    aoMudar: (AjustesCasca) -> Unit,
    aoSobre: () -> Unit,
    conectado: Boolean,
    avisar: (String, TomAviso) -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    var servidor by remember(ajustes.servidorRetransmissao) {
        mutableStateOf(ajustes.servidorRetransmissao)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = EspacoPcFlow.x16,
            end = EspacoPcFlow.x16,
            top = EspacoPcFlow.x8,
            bottom = EspacoPcFlow.x24
        ),
        verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x16)
    ) {
        item { TituloTela("Ajustes", "Personalize o PCFlow do seu jeito.") }

        item {
            GrupoAjuste("Aparência", Icone.SOL) {
                TemaApp.entries.forEach { tema ->
                    OpcaoPcFlow(
                        titulo = tema.rotulo,
                        descricao = tema.descricao,
                        selecionado = ajustes.tema == tema,
                        aoSelecionar = { aoMudar(ajustes.copy(tema = tema)) }
                    )
                }
            }
        }

        item {
            GrupoAjuste("Qualidade da imagem", Icone.COMPUTADOR) {
                PerfilVideo.entries.forEach { perfil ->
                    OpcaoPcFlow(
                        titulo = perfil.rotulo,
                        descricao = perfil.descricao,
                        selecionado = ajustes.qualidade == perfil,
                        aoSelecionar = {
                            aoMudar(ajustes.copy(qualidade = perfil))
                            if (conectado) avisar("Qualidade aplicada nesta sessão.", TomAviso.INFO)
                        }
                    )
                }
            }
        }

        item {
            GrupoAjuste("Controle por toque", Icone.PONTEIRO) {
                ModoToquePadrao.entries.forEach { modo ->
                    OpcaoPcFlow(
                        titulo = modo.rotulo,
                        descricao = modo.descricao,
                        selecionado = ajustes.modoToque == modo,
                        aoSelecionar = { aoMudar(ajustes.copy(modoToque = modo)) }
                    )
                }
                Text(
                    "Vale para a próxima sessão; dentro dela o modo continua trocável.",
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = EspacoPcFlow.x8)
                )
            }
        }

        item {
            GrupoAjuste("Área de transferência", Icone.PRANCHETA) {
                ModoClipboard.entries.forEach { modo ->
                    OpcaoPcFlow(
                        titulo = modo.rotulo,
                        descricao = modo.descricao,
                        selecionado = ajustes.modoClipboard == modo,
                        aoSelecionar = { aoMudar(ajustes.copy(modoClipboard = modo)) }
                    )
                }
            }
        }

        item {
            GrupoAjuste("Servidor de retransmissão", Icone.REDE) {
                Rotulo(
                    "Só é preciso quando o computador avisou que está atrás de CGNAT. " +
                        "Em branco, a conexão é direta."
                )
                Respiro(EspacoPcFlow.x8)
                CampoTexto(
                    valor = servidor,
                    aoMudar = { servidor = it },
                    rotulo = "Endereço do servidor",
                    dica = "meuservidor.com ou meuservidor.com:45460",
                    modifier = Modifier.fillMaxWidth()
                )
                Respiro(EspacoPcFlow.x12)
                BotaoSecundario(
                    texto = "Salvar servidor",
                    aoClicar = {
                        aoMudar(ajustes.copy(servidorRetransmissao = servidor.trim()))
                        avisar(
                            if (servidor.isBlank()) "Servidor removido. As conexões voltam a ser diretas."
                            else "Servidor salvo.",
                            TomAviso.SUCESSO
                        )
                    }
                )
            }
        }

        item {
            GrupoAjuste("Sobre", Icone.INFO) {
                Rotulo("PCFlow para Android · sem conta, sem nuvem e sem coleta de dados.")
                Respiro(EspacoPcFlow.x12)
                BotaoSecundario(texto = "Sobre o PCFlow", aoClicar = aoSobre, icone = Icone.INFO)
            }
        }

        item { FaixaVersao() }
    }
}

@Composable
private fun GrupoAjuste(
    titulo: String,
    icone: Icone,
    conteudo: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    CartaoPcFlow(Modifier.fillMaxWidth()) {
        com.ander.pcflow.design.CabecalhoSecao(titulo = titulo, icone = icone)
        Respiro(EspacoPcFlow.x12)
        conteudo()
    }
}
