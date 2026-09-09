package com.ander.pcflow.telas

import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PerfilVideo

/** O aplicativo nasceu escuro; claro só existe porque foi implementado de verdade. */
enum class TemaApp(val rotulo: String, val descricao: String) {
    ESCURO("Escuro", "O visual padrão do PCFlow."),
    CLARO("Claro", "Mesmas cores, invertidas para ambiente iluminado."),
    SISTEMA("Seguir o sistema", "Muda junto com o tema do aparelho.")
}

/** Como o dedo controla o ponteiro quando a sessão abre. */
enum class ModoToquePadrao(val rotulo: String, val descricao: String) {
    DIRETO("Toque direto", "O cursor vai para onde o dedo tocar."),
    TRACKPAD("Touchpad", "A tela vira um trackpad e o cursor segue o arrasto.")
}

val PerfilVideo.rotulo: String
    get() = when (this) {
        PerfilVideo.RESPOSTA -> "Resposta"
        PerfilVideo.EQUILIBRADO -> "Equilibrada"
        PerfilVideo.IMAGEM -> "Imagem"
    }

val PerfilVideo.descricao: String
    get() = when (this) {
        PerfilVideo.RESPOSTA -> "$fps quadros por segundo, largura até $larguraMaxima px. Prioriza fluidez."
        PerfilVideo.EQUILIBRADO -> "$fps quadros por segundo, largura até $larguraMaxima px. Meio-termo."
        PerfilVideo.IMAGEM -> "$fps quadros por segundo, resolução nativa. Prioriza nitidez do texto."
    }

val ModoClipboard.rotulo: String
    get() = when (this) {
        ModoClipboard.DESLIGADO -> "Desligada"
        ModoClipboard.AUTOMATICO -> "Automática"
        ModoClipboard.MANUAL -> "Manual"
    }

val ModoClipboard.descricao: String
    get() = when (this) {
        ModoClipboard.DESLIGADO -> "Nada é copiado entre o celular e o computador."
        ModoClipboard.AUTOMATICO -> "O computador avisa sozinho quando o texto copiado muda."
        ModoClipboard.MANUAL -> "O texto só vai e volta quando você pedir."
    }

data class AjustesCasca(
    val tema: TemaApp = TemaApp.ESCURO,
    val qualidade: PerfilVideo = PerfilVideo.EQUILIBRADO,
    val modoToque: ModoToquePadrao = ModoToquePadrao.DIRETO,
    val modoClipboard: ModoClipboard = ModoClipboard.MANUAL,
    val servidorRetransmissao: String = ""
)

/**
 * Leitura tolerante: preferência gravada por uma versão anterior (ou corrompida)
 * vira o padrão em vez de derrubar o aplicativo na abertura.
 */
internal inline fun <reified T : Enum<T>> lerEnum(texto: String?, padrao: T): T =
    enumValues<T>().firstOrNull { it.name == texto } ?: padrao
