package com.ander.pcflow.telas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ander.pcflow.EstadoConexao
import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PcEncontrado
import com.ander.pcflow.PerfilVideo
import com.ander.pcflow.SessaoPcFlow

/**
 * Preferências de sessão que a casca guarda e a tela de sessão consome.
 *
 * A qualidade de vídeo e o modo da área de transferência são aplicados aqui
 * mesmo, chamando o transporte. O modo de toque é lido pela tela de sessão no
 * momento em que ela abre — é o único ajuste que depende de outra frente.
 */
object PreferenciasSessaoPcFlow {
    @Volatile
    var modoToquePadrao: ModoToquePadrao = ModoToquePadrao.DIRETO
        internal set
}

/**
 * Estado da casca: favoritos, apelidos, computadores conhecidos, histórico e
 * ajustes. Guarda em disco a cada mudança, porque o usuário não salva nada
 * à mão neste aplicativo.
 */
class EstadoCasca(
    private val repositorio: RepositorioCasca,
    private val agora: () -> Long = System::currentTimeMillis
) {
    var favoritos by mutableStateOf(repositorio.favoritos())
        private set
    var apelidos by mutableStateOf(repositorio.apelidos())
        private set
    var lembrados by mutableStateOf(repositorio.lembrados())
        private set
    var historico by mutableStateOf(repositorio.historico())
        private set
    var ajustes by mutableStateOf(repositorio.ajustes())
        private set

    init {
        PreferenciasSessaoPcFlow.modoToquePadrao = ajustes.modoToque
    }

    fun dispositivos(descobertos: List<PcEncontrado>): List<Dispositivo> =
        ListaDispositivos.combinar(descobertos, lembrados, favoritos, apelidos)

    fun alternarFavorito(chave: String) {
        favoritos = repositorio.alternarFavorito(chave)
    }

    fun renomear(chave: String, apelido: String) {
        apelidos = repositorio.definirApelido(chave, apelido)
    }

    fun esquecer(chave: String) {
        lembrados = repositorio.esquecer(chave)
        apelidos = repositorio.definirApelido(chave, "")
        favoritos = repositorio.definirFavorito(chave, false)
    }

    fun salvarAjustes(novos: AjustesCasca) {
        ajustes = repositorio.salvarAjustes(novos)
        PreferenciasSessaoPcFlow.modoToquePadrao = ajustes.modoToque
        aplicarNaSessao()
    }

    /** Vale para a sessão de agora e para as próximas. */
    fun aplicarNaSessao() {
        SessaoPcFlow.definirPerfilVideo(ajustes.qualidade)
        SessaoPcFlow.definirModoClipboard(ajustes.modoClipboard)
        SessaoPcFlow.salvarServidor(ajustes.servidorRetransmissao)
    }

    fun limparHistorico() {
        repositorio.limparHistorico()
        historico = emptyList()
    }

    fun lembrar(pc: PcEncontrado) {
        val chave = pc.maquinaId.ifBlank { pc.host }
        if (chave.isBlank()) return
        lembrados = repositorio.lembrar(
            Dispositivo.de(pc, naRede = false).copy(apelido = apelidos[chave].orEmpty()),
            agora()
        )
    }

    fun registrar(registro: RegistroSessao) {
        historico = repositorio.registrarSessao(registro)
    }

    fun nomeDe(pc: PcEncontrado): String {
        val chave = pc.maquinaId.ifBlank { pc.host }
        return apelidos[chave]?.takeIf { it.isNotBlank() } ?: pc.nome
    }
}

@Composable
fun rememberEstadoCasca(): EstadoCasca {
    val contexto = LocalContext.current
    return remember(contexto) {
        EstadoCasca(RepositorioCasca(ArmazemPreferencias(contexto)))
    }
}

/**
 * Traduz a virada de estado da conexão em uma linha do histórico.
 *
 * Fica separado dos Composables para poder ser testado: é a regra que decide
 * se a sessão terminou por escolha, por queda ou por recusa do computador.
 */
object CronometroSessao {

    fun resultadoDe(anterior: EstadoConexao, novo: EstadoConexao, mensagem: String): ResultadoSessao? =
        when {
            anterior == EstadoConexao.CONECTADO && novo == EstadoConexao.DESCONECTADO ->
                ResultadoSessao.CONCLUIDA
            anterior == EstadoConexao.CONECTADO && novo == EstadoConexao.ERRO ->
                ResultadoSessao.INTERROMPIDA
            anterior == EstadoConexao.CONECTANDO && novo == EstadoConexao.ERRO ->
                if (recusa(mensagem)) ResultadoSessao.RECUSADA else ResultadoSessao.FALHOU
            else -> null
        }

    /** O computador responde "Conexão recusada" quando alguém nega o pedido lá. */
    fun recusa(mensagem: String): Boolean {
        val texto = mensagem.lowercase()
        return texto.contains("recusad") || texto.contains("negad") || texto.contains("bloquead")
    }

    fun duracaoSegundos(inicioMs: Long, fimMs: Long): Long =
        if (inicioMs <= 0L) 0L else ((fimMs - inicioMs).coerceAtLeast(0L)) / 1000L
}

/** Perfil de vídeo e modo de área de transferência aplicados ao conectar. */
fun aplicarPreferencias(qualidade: PerfilVideo, clipboard: ModoClipboard) {
    SessaoPcFlow.definirPerfilVideo(qualidade)
    SessaoPcFlow.definirModoClipboard(clipboard)
}
