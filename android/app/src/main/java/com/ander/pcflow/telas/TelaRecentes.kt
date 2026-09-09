package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.CabecalhoSecao
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.EstadoVazio
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.ItemLista
import com.ander.pcflow.design.PcFlow

/**
 * Recentes: histórico das sessões deste celular.
 *
 * Guarda só metadado — computador, quando, quanto durou e como terminou.
 * Nada do que foi digitado, visto ou transferido entra aqui.
 */
@Composable
fun TelaRecentes(
    historico: List<RegistroSessao>,
    dispositivos: List<Dispositivo>,
    acoes: AcoesCasca,
    aoLimpar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    val agora = remember(historico) { System.currentTimeMillis() }
    val porChave = remember(dispositivos) { dispositivos.associateBy { it.chave } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = EspacoPcFlow.x16,
            end = EspacoPcFlow.x16,
            top = EspacoPcFlow.x8,
            bottom = EspacoPcFlow.x24
        ),
        verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
    ) {
        item { TituloTela("Recentes", "Suas últimas sessões neste celular.") }

        if (historico.isEmpty()) {
            item {
                EstadoVazio(
                    titulo = "Nenhuma sessão ainda",
                    descricao = "Depois da primeira conexão, cada sessão aparece aqui com " +
                        "o computador, a duração e como ela terminou.",
                    icone = Icone.RELOGIO,
                    textoAcao = "Ir para Início",
                    aoAgir = { acoes.irPara(Destino.INICIO) }
                )
            }
            return@LazyColumn
        }

        item {
            CabecalhoSecao(
                titulo = "Histórico",
                icone = Icone.RELOGIO,
                contagem = historico.size,
                acao = { BotaoSecundario(texto = "Limpar", aoClicar = aoLimpar, icone = Icone.LIXEIRA) }
            )
        }

        items(historico, key = { "${it.inicioMs}-${it.dispositivoId}" }) { registro ->
            val conhecido = porChave[registro.dispositivoId]
            ItemLista(
                titulo = conhecido?.rotulo ?: registro.nome,
                subtitulo = Historico.formatarQuando(registro.inicioMs, agora),
                detalhe = "${registro.resultado.rotulo} · ${Historico.formatarDuracao(registro.duracaoSegundos)}",
                icone = iconeDoResultado(registro.resultado),
                corIcone = corDoResultado(registro.resultado, cores),
                descricaoIcone = registro.resultado.rotulo,
                // Reconectar só faz sentido quando ainda sabemos como chegar lá.
                aoClicar = conhecido?.let { alvo -> { acoes.pedirConexao(alvo) } }
            )
        }
    }
}

private fun iconeDoResultado(resultado: ResultadoSessao): Icone = when (resultado) {
    ResultadoSessao.CONCLUIDA -> Icone.CHECK
    ResultadoSessao.INTERROMPIDA -> Icone.ALERTA
    ResultadoSessao.RECUSADA -> Icone.CADEADO
    ResultadoSessao.FALHOU -> Icone.ALERTA
}

private fun corDoResultado(
    resultado: ResultadoSessao,
    cores: com.ander.pcflow.design.CoresPcFlow
) = when (resultado) {
    ResultadoSessao.CONCLUIDA -> cores.seguro
    ResultadoSessao.INTERROMPIDA -> cores.acao
    else -> cores.erro
}
