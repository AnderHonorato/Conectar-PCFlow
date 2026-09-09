package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ander.pcflow.design.BotaoFavorito
import com.ander.pcflow.design.BotaoIcone
import com.ander.pcflow.design.BotaoPrimario
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.CartaoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.NumeroIdentidade
import com.ander.pcflow.design.PcFlow

/**
 * O cartão de computador dos mockups: miniatura à esquerda, nome, ID em
 * grupos e estado; estrela e menu à direita; Conectar e Renomear no próprio
 * item, sem precisar abrir outra tela.
 */
@Composable
fun ItemDispositivo(
    dispositivo: Dispositivo,
    aoConectar: () -> Unit,
    aoRenomear: () -> Unit,
    aoAlternarFavorito: () -> Unit,
    aoAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = PcFlow.cores
    CartaoPcFlow(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
        ) {
            MiniaturaDispositivo(dispositivo.chave)
            Column(Modifier.weight(1f)) {
                Text(
                    text = dispositivo.rotulo,
                    color = cores.texto,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NumeroIdentidade(
                    texto = Identidade.formatarId(dispositivo.id).ifBlank { dispositivo.host },
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = detalheDe(dispositivo),
                    color = cores.textoSecundario,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                EstadoDispositivo(dispositivo.online, Modifier.padding(top = EspacoPcFlow.x4))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BotaoFavorito(
                    favorito = dispositivo.favorito,
                    aoAlternar = aoAlternarFavorito,
                    nomeDoDispositivo = dispositivo.rotulo
                )
                BotaoIcone(
                    icone = Icone.MAIS_OPCOES,
                    descricao = "Mais ações para ${dispositivo.rotulo}",
                    aoClicar = aoAbrirMenu,
                    cor = cores.textoSecundario
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = EspacoPcFlow.x12),
            horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
        ) {
            BotaoPrimario(
                texto = "Conectar",
                aoClicar = aoConectar,
                modifier = Modifier.weight(1f),
                icone = Icone.COMPUTADOR
            )
            BotaoSecundario(
                texto = "Renomear",
                aoClicar = aoRenomear,
                modifier = Modifier.weight(1f),
                icone = Icone.LAPIS
            )
        }
    }
}

/**
 * O que se sabe de verdade sobre a máquina. O sistema operacional do mockup
 * ainda não vem no handshake, então mostramos endereço e monitores em vez de
 * inventar "Windows 11".
 */
private fun detalheDe(dispositivo: Dispositivo): String = buildString {
    if (dispositivo.host.isNotBlank()) append(dispositivo.host)
    if (dispositivo.online && dispositivo.monitores > 0) {
        if (isNotEmpty()) append(" · ")
        append(if (dispositivo.monitores == 1) "1 monitor" else "${dispositivo.monitores} monitores")
    }
    if (isEmpty()) append("Guardado neste aparelho")
}
