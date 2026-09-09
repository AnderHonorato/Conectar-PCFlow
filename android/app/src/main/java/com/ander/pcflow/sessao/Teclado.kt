package com.ander.pcflow.sessao

/** Modificadores, com o nome que o protocolo do contrato aceita. */
enum class Modificador(val nome: String, val rotulo: String) {
    CTRL("ctrl", "Ctrl"),
    ALT("alt", "Alt"),
    SHIFT("shift", "Shift"),
    WIN("win", "Win")
}

enum class EstadoModificador {
    /** Não participa da próxima tecla. */
    SOLTO,

    /** Vale para a próxima tecla e depois se solta sozinho. */
    ARMADO,

    /** Fica valendo até o usuário desligar — é a trava pedida no contrato. */
    FIXO
}

/**
 * Modificadores travados do teclado da tela.
 *
 * Nada aqui pressiona tecla no PC: o modificador viaja junto da tecla em
 * `teclaEspecial(tecla, modificadores)`. É de propósito — assim é impossível
 * deixar Ctrl preso no Windows se o teclado fechar, o aplicativo for para
 * segundo plano ou a sessão cair. O único estado que existe é este, local, e
 * `limpar()` acaba com ele.
 */
class TravaModificadores {
    private val estados = linkedMapOf<Modificador, EstadoModificador>()

    fun estado(modificador: Modificador): EstadoModificador =
        estados[modificador] ?: EstadoModificador.SOLTO

    /** Toque curto: solto → armado → solto. */
    fun alternar(modificador: Modificador) {
        estados[modificador] = when (estado(modificador)) {
            EstadoModificador.SOLTO -> EstadoModificador.ARMADO
            else -> EstadoModificador.SOLTO
        }
    }

    /** Pressão longa: liga ou desliga a trava. */
    fun fixar(modificador: Modificador) {
        estados[modificador] = when (estado(modificador)) {
            EstadoModificador.FIXO -> EstadoModificador.SOLTO
            else -> EstadoModificador.FIXO
        }
    }

    fun ativos(): List<Modificador> =
        Modificador.entries.filter { estado(it) != EstadoModificador.SOLTO }

    fun nomes(): List<String> = ativos().map { it.nome }

    val algumAtivo: Boolean get() = ativos().isNotEmpty()

    /** Depois de acionar uma tecla, os armados caem e os fixos continuam. */
    fun consumir() {
        for (modificador in Modificador.entries) {
            if (estado(modificador) == EstadoModificador.ARMADO) estados[modificador] = EstadoModificador.SOLTO
        }
    }

    fun limpar() = estados.clear()
}

/** Uma tecla do painel: o rótulo que aparece e o nome que o PC entende. */
data class TeclaEspecial(val rotulo: String, val nome: String, val largura: Float = 1f)

/** Atalho pronto, com o nome em português e a combinação real que vai ao PC. */
data class AtalhoPronto(
    val rotulo: String,
    val tecla: String,
    val modificadores: List<String> = emptyList(),
    val observacao: String = ""
)

object CatalogoTeclas {

    val navegacao = listOf(
        TeclaEspecial("Esc", "ESC"),
        TeclaEspecial("Tab", "TAB"),
        TeclaEspecial("Enter", "ENTER"),
        TeclaEspecial("Backspace", "BACKSPACE", largura = 1.4f),
        TeclaEspecial("Delete", "DELETE")
    )

    val setas = listOf(
        TeclaEspecial("←", "LEFT"),
        TeclaEspecial("↑", "UP"),
        TeclaEspecial("↓", "DOWN"),
        TeclaEspecial("→", "RIGHT")
    )

    val paginacao = listOf(
        TeclaEspecial("Home", "HOME"),
        TeclaEspecial("End", "END"),
        TeclaEspecial("PgUp", "PAGEUP"),
        TeclaEspecial("PgDn", "PAGEDOWN"),
        TeclaEspecial("Insert", "INSERT", largura = 1.2f)
    )

    val funcao = (1..12).map { TeclaEspecial("F$it", "F$it") }

    val sistema = listOf(
        TeclaEspecial("Windows", "START_MENU", largura = 1.6f),
        TeclaEspecial("Espaço", "SPACE", largura = 1.6f)
    )

    val atalhos = listOf(
        AtalhoPronto("Copiar", "C", listOf("ctrl")),
        AtalhoPronto("Colar", "V", listOf("ctrl")),
        AtalhoPronto("Recortar", "X", listOf("ctrl")),
        AtalhoPronto("Desfazer", "Z", listOf("ctrl")),
        AtalhoPronto("Refazer", "Y", listOf("ctrl")),
        AtalhoPronto("Selecionar tudo", "A", listOf("ctrl")),
        AtalhoPronto("Salvar", "S", listOf("ctrl")),
        AtalhoPronto("Alternar janela", "ALT_TAB", observacao = "Alt+Tab"),
        AtalhoPronto("Mostrar a área de trabalho", "SHOW_DESKTOP", observacao = "Win+D"),
        AtalhoPronto("Explorador de arquivos", "WIN_E", observacao = "Win+E"),
        AtalhoPronto("Executar", "WIN_R", observacao = "Win+R"),
        AtalhoPronto(
            "Gerenciador de tarefas", "TASK_MANAGER",
            observacao = "Ctrl+Shift+Esc"
        ),
        AtalhoPronto(
            "Ctrl+Alt+Del", "DELETE", listOf("ctrl", "alt"),
            // O Windows só deixa a tela de segurança aparecer por tecla física;
            // prometer que este botão a abre seria mentira.
            observacao = "O Windows costuma ignorar em programa; use o Gerenciador de tarefas."
        )
    )
}
