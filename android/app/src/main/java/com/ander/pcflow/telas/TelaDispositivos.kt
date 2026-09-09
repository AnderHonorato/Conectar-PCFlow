package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ander.pcflow.design.CabecalhoSecao
import com.ander.pcflow.design.ChipPcFlow
import com.ander.pcflow.design.CampoBusca
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.EstadoVazio
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.PcFlow

/**
 * Dispositivos: busca, filtro e as seções do mockup (Favoritos, Na mesma rede,
 * Todos). Conectar e renomear ficam no próprio item, sem abrir outra tela.
 */
@Composable
fun TelaDispositivos(
    dispositivos: List<Dispositivo>,
    acoes: AcoesCasca,
    modifier: Modifier = Modifier
) {
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(FiltroDispositivos.TODOS) }
    val cores = PcFlow.cores
    val secoes = remember(dispositivos, busca, filtro) {
        ListaDispositivos.secoes(dispositivos, busca, filtro)
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = EspacoPcFlow.x16,
                end = EspacoPcFlow.x16,
                top = EspacoPcFlow.x8,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
        ) {
            item {
                TituloTela("Dispositivos", "Gerencie e conecte aos seus computadores.")
            }
            item {
                CampoBusca(
                    valor = busca,
                    aoMudar = { busca = it },
                    dica = "Buscar dispositivo, nome ou ID…"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)) {
                    FiltroDispositivos.entries.forEach { opcao ->
                        ChipPcFlow(
                            texto = opcao.rotulo,
                            selecionado = filtro == opcao,
                            aoClicar = { filtro = opcao },
                            contagem = ListaDispositivos.filtrar(dispositivos, "", opcao).size
                        )
                    }
                }
            }

            if (secoes.isEmpty()) {
                item {
                    EstadoVazio(
                        titulo = if (busca.isBlank()) "Nenhum computador conhecido"
                                 else "Nada encontrado para essa busca",
                        descricao = if (busca.isBlank())
                            "Procure na rede local ou adicione um computador pelo ID, QR ou código de acesso."
                        else "Confira o texto digitado, ou limpe a busca para ver todos.",
                        icone = Icone.COMPUTADOR,
                        textoAcao = if (busca.isBlank()) "Procurar na rede" else "Limpar busca",
                        aoAgir = { if (busca.isBlank()) acoes.buscarNaRede() else busca = "" }
                    )
                }
            }

            secoes.forEach { secao ->
                item(key = "cabecalho-${secao.tipo.name}") {
                    CabecalhoSecao(
                        titulo = secao.tipo.titulo,
                        icone = iconeDaSecao(secao.tipo),
                        corIcone = if (secao.tipo == TipoSecao.FAVORITOS) cores.acao else cores.textoSecundario,
                        contagem = secao.contagem
                    )
                }
                items(secao.itens, key = { "${secao.tipo.name}-${it.chave}" }) { dispositivo ->
                    ItemDispositivo(
                        dispositivo = dispositivo,
                        aoConectar = { acoes.pedirConexao(dispositivo) },
                        aoRenomear = { acoes.renomear(dispositivo) },
                        aoAlternarFavorito = { acoes.alternarFavorito(dispositivo) },
                        aoAbrirMenu = { acoes.abrirMenu(dispositivo) }
                    )
                }
            }
        }

        BotaoFlutuante(
            icone = Icone.MAIS,
            descricao = "Adicionar dispositivo",
            aoClicar = acoes.adicionarDispositivo,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(EspacoPcFlow.x16)
        )
    }
}

private fun iconeDaSecao(tipo: TipoSecao): Icone = when (tipo) {
    TipoSecao.FAVORITOS -> Icone.ESTRELA_CHEIA
    TipoSecao.NA_REDE -> Icone.CASA
    TipoSecao.TODOS -> Icone.LISTA
}
