package com.ander.pcflow.sessao

/** Como o dedo controla o ponteiro do PC. O modo vale para a sessão inteira. */
enum class ModoEntrada(val rotulo: String, val explicacao: String) {
    /** O dedo é o ponteiro: onde encosta, o cursor vai. Bom para tocar em botão grande. */
    TOQUE_DIRETO(
        "Toque direto",
        "O dedo é o ponteiro: onde você toca, o cursor vai."
    ),

    /** A tela vira um touchpad de notebook: o cursor continua de onde parou. */
    TOUCHPAD(
        "Touchpad",
        "Arraste em qualquer lugar; o cursor continua de onde parou."
    ),

    /** Igual ao touchpad para mover, mas quem clica são os botões da barra. */
    MOUSE(
        "Mouse",
        "Arraste move o cursor; os cliques saem pelos botões."
    );

    val proximo: ModoEntrada get() = entries[(ordinal + 1) % entries.size]
}

/** Botões do mouse com o nome que o protocolo do contrato usa. */
enum class BotaoMouse(val nome: String, val rotulo: String) {
    ESQUERDO("esquerdo", "Esquerdo"),
    DIREITO("direito", "Direito"),
    MEIO("meio", "Meio")
}

/**
 * O que a máquina de gesto decidiu que precisa chegar ao PC.
 *
 * A máquina não conhece `SessaoPcFlow` nem o tamanho da imagem: ela devolve
 * intenção. Quem traduz para a rede é a camada de tela — é isso que deixa a
 * regra de gesto testável sem Android nem socket.
 */
sealed interface ComandoEntrada {
    /** Posição em pixels da área de vídeo; a tela converte para 0..1 do monitor. */
    data class PosicionarEm(val x: Float, val y: Float) : ComandoEntrada

    data class Mover(val dx: Float, val dy: Float) : ComandoEntrada
    data class Clicar(val botao: BotaoMouse, val cliques: Int = 1) : ComandoEntrada
    data class Pressionar(val botao: BotaoMouse) : ComandoEntrada
    data class Soltar(val botao: BotaoMouse) : ComandoEntrada
    data class Rolar(val delta: Int, val horizontal: Boolean) : ComandoEntrada
}

/** Eixo escolhido para a rolagem de dois dedos. */
enum class Eixo { INDEFINIDO, VERTICAL, HORIZONTAL }
