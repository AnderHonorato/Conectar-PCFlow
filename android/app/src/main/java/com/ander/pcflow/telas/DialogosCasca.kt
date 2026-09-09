package com.ander.pcflow.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ander.pcflow.PcEncontrado
import com.ander.pcflow.SessaoPcFlow
import com.ander.pcflow.design.BotaoSecundario
import com.ander.pcflow.design.CampoSenha
import com.ander.pcflow.design.CampoTexto
import com.ander.pcflow.design.DialogoPcFlow
import com.ander.pcflow.design.EspacoPcFlow
import com.ander.pcflow.design.EstiloIdentidadeMenor
import com.ander.pcflow.design.Icone
import com.ander.pcflow.design.ItemLista
import com.ander.pcflow.design.NumeroIdentidade
import com.ander.pcflow.design.PcFlow
import com.ander.pcflow.design.SeparadorPcFlow

/**
 * Pedido de conexão a um computador conhecido.
 *
 * Sem código e sem senha, o computador pergunta antes de aceitar. Os dois
 * campos existem porque o PC pode estar exigindo um deles — e o texto diz
 * exatamente isso, em vez de deixar a pessoa adivinhar.
 */
@Composable
fun DialogoConectar(
    dispositivo: Dispositivo,
    aoFechar: () -> Unit,
    aoConectar: (PcEncontrado, String?, String?) -> Unit
) {
    var codigo by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    DialogoPcFlow(
        titulo = "Conectar a ${dispositivo.rotulo}",
        aoFechar = aoFechar,
        texto = "Sem código nem senha, o pedido aparece na tela do computador " +
            "para alguém aceitar. Se o PCFlow de lá estiver exigindo código ou " +
            "senha de acesso, preencha abaixo.",
        textoConfirmar = "Conectar",
        aoConfirmar = {
            aoConectar(
                dispositivo.paraConexao(),
                codigo.ifBlank { null },
                senha.ifBlank { null }
            )
        }
    ) {
        Column(Modifier.padding(top = EspacoPcFlow.x16)) {
            NumeroIdentidade(Identidade.formatarId(dispositivo.id).ifBlank { dispositivo.host })
            Column(Modifier.padding(top = EspacoPcFlow.x16)) {
                CampoTexto(
                    valor = codigo,
                    aoMudar = { codigo = it.filter(Char::isDigit).take(6) },
                    rotulo = "Código de 6 dígitos (opcional)",
                    dica = "000000",
                    estiloValor = EstiloIdentidadeMenor,
                    opcoesTeclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
            Column(Modifier.padding(top = EspacoPcFlow.x12)) {
                CampoSenha(
                    valor = senha,
                    aoMudar = { senha = it },
                    rotulo = "Senha de acesso (opcional)",
                    apoio = "Definida no computador, em Segurança."
                )
            }
        }
    }
}

/**
 * Conexão de fora da rede: o código do computador carrega endereço e
 * identidade, então a verificação do certificado continua valendo.
 */
@Composable
fun DialogoConectarPorCodigo(
    servidorInicial: String,
    aoFechar: () -> Unit,
    aoConectar: (PcEncontrado, String, String) -> Unit
) {
    var codigo by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var servidor by remember { mutableStateOf(servidorInicial) }
    var erroCodigo by remember { mutableStateOf<String?>(null) }
    var erroSenha by remember { mutableStateOf<String?>(null) }

    DialogoPcFlow(
        titulo = "Conectar por código",
        aoFechar = aoFechar,
        texto = "Abra o PCFlow no computador, vá em \"Pela internet\" e copie o " +
            "código de acesso.",
        textoConfirmar = "Conectar",
        aoConfirmar = {
            val validacao = Identidade.validarCodigo(codigo, servidor)
            erroCodigo = validacao.mensagem
            erroSenha = if (senha.isBlank())
                "Conexão de fora da rede sempre pede a senha definida no computador."
            else null
            val destino = if (validacao.valido) {
                SessaoPcFlow.destinoDoCodigo(codigo.trim(), servidor.trim())
            } else null
            if (destino != null && senha.isNotBlank()) aoConectar(destino, senha, servidor.trim())
        },
        textoCancelar = "Cancelar"
    ) {
        Column(Modifier.padding(top = EspacoPcFlow.x16)) {
            CampoTexto(
                valor = codigo,
                aoMudar = { codigo = it; erroCodigo = null },
                rotulo = "Código de acesso",
                dica = "Cole aqui o código do computador",
                erro = erroCodigo,
                linhaUnica = false,
                minLinhas = 2
            )
            Column(Modifier.padding(top = EspacoPcFlow.x12)) {
                CampoSenha(
                    valor = senha,
                    aoMudar = { senha = it; erroSenha = null },
                    rotulo = "Senha de acesso do computador",
                    erro = erroSenha,
                    apoio = "Definida no computador, em Segurança."
                )
            }
            Column(Modifier.padding(top = EspacoPcFlow.x12)) {
                CampoTexto(
                    valor = servidor,
                    aoMudar = { servidor = it; erroCodigo = null },
                    rotulo = "Servidor de retransmissão",
                    dica = "endereco:porta",
                    apoio = "Só é necessário quando o computador avisou que usa retransmissão."
                )
            }
        }
    }
}

/** Apelido do computador. Em branco, volta a valer o nome de fábrica. */
@Composable
fun DialogoRenomear(
    dispositivo: Dispositivo,
    aoFechar: () -> Unit,
    aoSalvar: (String) -> Unit
) {
    var apelido by remember { mutableStateOf(dispositivo.apelido) }
    DialogoPcFlow(
        titulo = "Renomear computador",
        aoFechar = aoFechar,
        texto = "O apelido vale só neste aparelho. Deixe em branco para voltar " +
            "a usar \"${dispositivo.nome}\".",
        textoConfirmar = "Salvar",
        aoConfirmar = { aoSalvar(apelido) }
    ) {
        Column(Modifier.padding(top = EspacoPcFlow.x16)) {
            CampoTexto(
                valor = apelido,
                aoMudar = { apelido = it.take(60) },
                rotulo = "Apelido",
                dica = dispositivo.nome
            )
        }
    }
}

/** Menu de três pontos do item, com as ações que existem de verdade. */
@Composable
fun DialogoMenuDispositivo(
    dispositivo: Dispositivo,
    aoFechar: () -> Unit,
    aoConectar: () -> Unit,
    aoRenomear: () -> Unit,
    aoAlternarFavorito: () -> Unit,
    aoCopiarId: () -> Unit,
    aoAbrirArquivos: () -> Unit,
    aoEsquecer: () -> Unit
) {
    DialogoPcFlow(
        titulo = dispositivo.rotulo,
        aoFechar = aoFechar,
        texto = Identidade.formatarId(dispositivo.id).ifBlank { dispositivo.host },
        textoCancelar = "Fechar"
    ) {
        Column(
            Modifier.padding(top = EspacoPcFlow.x16),
            verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
        ) {
            ItemLista(
                titulo = "Conectar",
                subtitulo = "Abre a sessão neste computador",
                icone = Icone.COMPUTADOR,
                aoClicar = aoConectar
            )
            ItemLista(
                titulo = "Transferência de arquivos",
                subtitulo = "Conecta e abre as pastas do computador",
                icone = Icone.PASTA,
                aoClicar = aoAbrirArquivos
            )
            ItemLista(
                titulo = "Renomear",
                subtitulo = dispositivo.apelido.ifBlank { dispositivo.nome },
                icone = Icone.LAPIS,
                aoClicar = aoRenomear
            )
            ItemLista(
                titulo = if (dispositivo.favorito) "Tirar dos favoritos" else "Marcar como favorito",
                icone = if (dispositivo.favorito) Icone.ESTRELA_CHEIA else Icone.ESTRELA,
                aoClicar = aoAlternarFavorito
            )
            ItemLista(
                titulo = "Copiar ID",
                subtitulo = Identidade.formatarId(dispositivo.id).ifBlank { "Este destino não tem ID" },
                icone = Icone.PRANCHETA,
                habilitado = dispositivo.id.isNotBlank(),
                motivoDesabilitado = "Destino aberto por código não tem ID para copiar.",
                aoClicar = aoCopiarId
            )
            SeparadorPcFlow(Modifier.padding(vertical = EspacoPcFlow.x4))
            ItemLista(
                titulo = "Esquecer computador",
                subtitulo = "Apaga apelido, favorito e o endereço guardado",
                icone = Icone.LIXEIRA,
                corIcone = PcFlow.cores.erro,
                aoClicar = aoEsquecer
            )
        }
    }
}

/** Confirmação de ação destrutiva: vermelho, e não fecha por toque no véu. */
@Composable
fun DialogoConfirmar(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit
) {
    DialogoPcFlow(
        titulo = titulo,
        aoFechar = aoFechar,
        texto = texto,
        destrutivo = true,
        textoConfirmar = textoConfirmar,
        aoConfirmar = aoConfirmar
    )
}

/** As três formas de chegar a um computador novo. */
@Composable
fun DialogoAdicionarDispositivo(
    aoFechar: () -> Unit,
    aoDigitarId: () -> Unit,
    aoEscanear: () -> Unit,
    aoPorCodigo: () -> Unit,
    aoBuscarNaRede: () -> Unit
) {
    DialogoPcFlow(
        titulo = "Adicionar dispositivo",
        aoFechar = aoFechar,
        texto = "Escolha como chegar até o computador.",
        textoCancelar = "Fechar"
    ) {
        Column(
            Modifier.padding(top = EspacoPcFlow.x16),
            verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
        ) {
            ItemLista(
                titulo = "Buscar na rede",
                subtitulo = "Procura computadores no mesmo Wi‑Fi",
                icone = Icone.REDE,
                aoClicar = aoBuscarNaRede
            )
            ItemLista(
                titulo = "Digitar o ID",
                subtitulo = "Os 9 dígitos que aparecem no computador",
                icone = Icone.TECLADO_NUMERICO,
                aoClicar = aoDigitarId
            )
            ItemLista(
                titulo = "Escanear QR",
                subtitulo = "Aponta a câmera para o QR do PCFlow",
                icone = Icone.QR,
                aoClicar = aoEscanear
            )
            ItemLista(
                titulo = "Conectar por código",
                subtitulo = "Para fora do Wi‑Fi, com senha de acesso",
                icone = Icone.CADEADO,
                aoClicar = aoPorCodigo
            )
        }
    }
}

/** Menu do cabeçalho. Leva aos mesmos destinos da barra de baixo, e mais nada. */
@Composable
fun DialogoMenuAplicativo(
    aoFechar: () -> Unit,
    aoIr: (Destino) -> Unit,
    aoBuscarNaRede: () -> Unit,
    aoSobre: () -> Unit
) {
    DialogoPcFlow(
        titulo = "Menu",
        aoFechar = aoFechar,
        textoCancelar = "Fechar"
    ) {
        Column(
            Modifier.padding(top = EspacoPcFlow.x16),
            verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x8)
        ) {
            Destino.entries.forEach { destino ->
                ItemLista(
                    titulo = destino.rotulo,
                    subtitulo = destino.apoio,
                    icone = destino.icone,
                    aoClicar = { aoIr(destino) }
                )
            }
            SeparadorPcFlow(Modifier.padding(vertical = EspacoPcFlow.x4))
            ItemLista(
                titulo = "Buscar na rede agora",
                subtitulo = "Procura computadores no Wi‑Fi atual",
                icone = Icone.ATUALIZAR,
                aoClicar = aoBuscarNaRede
            )
            ItemLista(
                titulo = "Sobre o PCFlow",
                subtitulo = "Versão, segurança e privacidade",
                icone = Icone.INFO,
                aoClicar = aoSobre
            )
        }
    }
}

@Composable
fun DialogoSobre(aoFechar: () -> Unit) {
    val cores = PcFlow.cores
    DialogoPcFlow(
        titulo = "Sobre o PCFlow",
        aoFechar = aoFechar,
        textoCancelar = "Fechar"
    ) {
        Column(
            Modifier.padding(top = EspacoPcFlow.x16),
            verticalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EspacoPcFlow.x12)
            ) {
                MarcaPcFlow(tamanho = 48.dp)
                Column {
                    Text(
                        "PCFlow para Android",
                        color = cores.texto,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Versão ${SessaoPcFlow.VERSAO_APP}",
                        color = cores.textoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LinhaSobre(
                "Conexão verificada",
                "TLS com pinagem do certificado do computador, na rede local ou por código de acesso."
            )
            LinhaSobre(
                "Sem conta e sem nuvem",
                "Nada é enviado para servidores do PCFlow. Favoritos, apelidos e histórico ficam neste aparelho."
            )
            LinhaSobre(
                "Sem coleta de dados",
                "Não existe telemetria, mesmo desligada. Nada de uso é medido nem enviado."
            )
        }
    }
}

@Composable
private fun LinhaSobre(titulo: String, texto: String) {
    val cores = PcFlow.cores
    Column {
        Text(titulo, color = cores.texto, style = MaterialTheme.typography.titleMedium)
        Text(texto, color = cores.textoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}
