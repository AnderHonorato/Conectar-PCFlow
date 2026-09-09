package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ander.pcflow.EstadoConexao
import com.ander.pcflow.EstadoSessao
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.CabecalhoSecao
import com.ander.pcflow.design.CartaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.EstadoVazio
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.PcFlow

/**
 * Início: conectar a um computador e ver os que já são conhecidos.
 *
 * A ordem segue o mockup — primeiro o caminho de conectar, depois a lista.
 * Quem abre o aplicativo quase sempre quer conectar, não administrar.
 */
@Composable
fun TelaInicio(
    dispositivos: List<Dispositivo>,
    estado: EstadoSessao,
    acoes: AcoesCasca,
    modifier: Modifier = Modifier
) {
    var id by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    val cores = PcFlow.cores
    val conhecidos = remember(dispositivos) { ListaDispositivos.ordenar(dispositivos).take(6) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = EspacoPcFlow.x16,
            end = EspacoPcFlow.x16,
            top = EspacoPcFlow.x8,
            bottom = EspacoPcFlow.x24
        ),
        verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x16)
    ) {
        item {
            CartaoPcFlow(Modifier.fillMaxWidth()) {
                Text(
                    "Conectar a um computador",
                    color = cores.texto,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Digite o ID do dispositivo para iniciar a conexão",
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = EspacoPcFlow.x4)
                )
                Respiro(EspacoPcFlow.x12)
                CampoIdComAcao(
                    valor = id,
                    aoMudar = { id = Identidade.aoDigitarId(it); erro = null },
                    aoConfirmar = { conectarPorId(id, dispositivos, acoes) { erro = it } },
                    erro = erro
                )
                Respiro(EspacoPcFlow.x12)
                Row(horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)) {
                    BotaoSecundario(
                        texto = "Escanear QR",
                        aoClicar = acoes.escanearQr,
                        icone = Icone.CELULAR,
                        modifier = Modifier.weight(1f)
                    )
                    BotaoSecundario(
                        texto = "Por código",
                        aoClicar = acoes.conectarPorCodigo,
                        icone = Icone.CADEADO,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "O código funciona de qualquer internet, fora do Wi‑Fi de casa.",
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = EspacoPcFlow.x8)
                )
            }
        }

        if (estado.estado == EstadoConexao.ERRO && estado.mensagem.isNotBlank()) {
            item {
                com.ander.pcflow.design.EstadoErro(
                    titulo = "A última tentativa falhou",
                    descricao = estado.mensagem,
                    textoAcao = "Procurar na rede",
                    aoAgir = acoes.buscarNaRede
                )
            }
        }

        item {
            CabecalhoSecao(
                titulo = "Meus dispositivos",
                icone = Icone.COMPUTADOR,
                corIcone = cores.acao,
                contagem = dispositivos.size.takeIf { it > 0 },
                acao = {
                    BotaoSecundario(
                        texto = "Ver todos",
                        aoClicar = { acoes.irPara(Destino.DISPOSITIVOS) }
                    )
                }
            )
        }

        if (conhecidos.isEmpty()) {
            item {
                EstadoVazio(
                    titulo = "Nenhum computador ainda",
                    descricao = "Abra o PCFlow no computador e toque em Procurar na rede, " +
                        "ou escaneie o QR que aparece na tela dele.",
                    icone = Icone.COMPUTADOR,
                    textoAcao = "Procurar na rede",
                    aoAgir = acoes.buscarNaRede
                )
            }
        } else {
            items(conhecidos, key = { it.chave }) { dispositivo ->
                ItemDispositivo(
                    dispositivo = dispositivo,
                    aoConectar = { acoes.pedirConexao(dispositivo) },
                    aoRenomear = { acoes.renomear(dispositivo) },
                    aoAlternarFavorito = { acoes.alternarFavorito(dispositivo) },
                    aoAbrirMenu = { acoes.abrirMenu(dispositivo) }
                )
            }
        }

        item { FaixaVersao() }
    }
}

/**
 * O ID digitado só vira conexão quando bate com um computador conhecido: o ID
 * sozinho não carrega endereço nem identidade TLS, e conectar sem identidade
 * seria abrir mão da verificação do certificado.
 */
private fun conectarPorId(
    id: String,
    dispositivos: List<Dispositivo>,
    acoes: AcoesCasca,
    aoFalhar: (String) -> Unit
) {
    val validacao = Identidade.validarId(id)
    if (!validacao.valido) {
        aoFalhar(validacao.mensagem ?: "ID incompleto.")
        return
    }
    val digitos = Identidade.somenteDigitos(id)
    val alvo = dispositivos.firstOrNull { Identidade.somenteDigitos(it.id) == digitos }
    if (alvo == null) {
        aoFalhar(
            "Esse ID não está entre os computadores conhecidos. Procure na rede " +
                "ou use o código de acesso, que já traz o endereço e a identidade."
        )
        return
    }
    acoes.pedirConexao(alvo)
}
