package com.ander.pcflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.pcflow.rede.EstadoConexao
import com.ander.pcflow.rede.EstadoSessao
import com.ander.pcflow.rede.PcEncontrado
import com.ander.pcflow.rede.RedeLocal

@Composable
fun TelaConectar(
    modifier: Modifier = Modifier,
    pcs: List<PcEncontrado>,
    estado: EstadoSessao,
    aoConectar: (PcEncontrado, String?) -> Unit,
    aoAtualizar: () -> Unit,
    aoEsquecer: (PcEncontrado) -> Unit,
    aoEscanear: () -> Unit,
    aoAdicionarManual: (String) -> Unit
) {
    var pcParaParear by remember { mutableStateOf<PcEncontrado?>(null) }
    var mostrarManual by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("PCFlow", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = TextoPrimario)
        Text(
            "Controle seu computador pela rede local",
            color = TextoSecundario,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        val ipCelular = remember { RedeLocal.enderecoDoCelular() }
        Cartao {
            Text(
                if (ipCelular != null) "Seu celular está em $ipCelular"
                else "Celular sem rede Wi‑Fi ativa",
                color = TextoPrimario, fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ipCelular != null)
                    "O PC precisa aparecer na mesma faixa (${RedeLocal.prefixo(ipCelular)}.x). " +
                        "Redes de convidados e VPN separam os aparelhos."
                else "Conecte o celular ao mesmo Wi‑Fi do computador para continuar.",
                color = TextoSecundario, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = aoEscanear,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Escanear QR", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = { mostrarManual = true },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Digitar IP", color = Dourado) }
        }

        Spacer(Modifier.height(22.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Computadores encontrados", fontSize = 17.sp, color = TextoPrimario)
            if (estado.estado == EstadoConexao.PROCURANDO) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Dourado, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = aoAtualizar) { Text("Atualizar", color = Dourado) }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (pcs.isEmpty()) {
            Cartao {
                Text(
                    if (estado.estado == EstadoConexao.PROCURANDO)
                        "Procurando o PCFlow na sua rede…"
                    else "Nenhum computador encontrado ainda.",
                    color = TextoPrimario
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Confira se o PCFlow está aberto no Windows (pode estar na bandeja) e se o " +
                        "celular está no mesmo Wi‑Fi. Se a rede bloquear a busca automática, " +
                        "use “Digitar IP” com o endereço que aparece na tela do PC.",
                    color = TextoSecundario, fontSize = 13.sp
                )
            }
        } else {
            pcs.forEach { pc ->
                Cartao(
                    Modifier
                        .padding(bottom = 10.dp)
                        .clickable { pcParaParear = pc }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeMonitor()
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pc.nome, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium, color = TextoPrimario
                            )
                            Text(
                                pc.endereco + if (pc.salvo) " · já pareado" else "",
                                color = TextoSecundario, fontSize = 12.sp
                            )
                        }
                        Text("Conectar", color = Dourado, fontSize = 14.sp)
                    }
                    if (!RedeLocal.mesmaRede(ipCelular, pc.host)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Este PC está em outra faixa de IP. Se a conexão falhar, " +
                                "coloque os dois no mesmo Wi‑Fi.",
                            color = Vermelho, fontSize = 11.sp
                        )
                    }
                    if (pc.salvo) {
                        TextButton(
                            onClick = { aoEsquecer(pc) },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Esquecer este PC", color = TextoSecundario, fontSize = 12.sp) }
                    }
                }
            }
        }

        if (estado.estado == EstadoConexao.ERRO && estado.mensagem.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Cartao(corBorda = Vermelho) {
                Text("Não deu para conectar", color = Vermelho, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(estado.mensagem, color = TextoSecundario, fontSize = 13.sp)
            }
        }

        if (estado.estado == EstadoConexao.CONECTANDO ||
            estado.estado == EstadoConexao.RECONECTANDO
        ) {
            Spacer(Modifier.height(14.dp))
            Cartao {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Dourado, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(estado.mensagem, color = TextoPrimario, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Sem conta · sem nuvem · sem anúncios",
            color = TextoSecundario, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
    }

    pcParaParear?.let { pc ->
        DialogoPin(
            pc = pc,
            aoCancelar = { pcParaParear = null },
            aoConfirmar = { pin ->
                pcParaParear = null
                aoConectar(pc, pin)
            }
        )
    }

    if (mostrarManual) {
        DialogoEndereco(
            aoCancelar = { mostrarManual = false },
            aoConfirmar = { texto ->
                mostrarManual = false
                aoAdicionarManual(texto)
            }
        )
    }
}

@Composable
private fun DialogoPin(pc: PcEncontrado, aoCancelar: () -> Unit, aoConfirmar: (String?) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Painel,
        title = { Text("Conectar a ${pc.nome}", color = TextoPrimario) },
        text = {
            Column {
                Text(
                    if (pc.salvo)
                        "Este PC já autorizou o celular. Toque em Conectar; o código só é " +
                            "necessário se o pareamento tiver sido removido no computador."
                    else
                        "Digite o código de 6 dígitos que aparece na tela do PCFlow no computador.",
                    color = TextoSecundario, fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("Código de pareamento") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { aoConfirmar(pin.ifBlank { null }) }) { Text("Conectar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = TextoSecundario) } }
    )
}

@Composable
private fun DialogoEndereco(aoCancelar: () -> Unit, aoConfirmar: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Painel,
        title = { Text("Endereço do computador", color = TextoPrimario) },
        text = {
            Column {
                Text(
                    "Use quando a busca automática não funcionar. O endereço aparece na tela " +
                        "inicial do PCFlow no Windows, algo como 192.168.0.12.",
                    color = TextoSecundario, fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.trim() },
                    label = { Text("IP ou IP:porta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { aoConfirmar(texto) }, enabled = texto.isNotBlank()) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = TextoSecundario) } }
    )
}

@Composable
fun IconeMonitor(tamanho: Int = 40) {
    Box(
        Modifier
            .size(tamanho.dp)
            .background(PainelClaro, RoundedCornerShape(12.dp))
            .border(1.dp, DouradoFraco, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width((tamanho * 0.46f).dp)
                .height((tamanho * 0.34f).dp)
                .border(2.dp, Dourado, RoundedCornerShape(3.dp))
        )
    }
}
