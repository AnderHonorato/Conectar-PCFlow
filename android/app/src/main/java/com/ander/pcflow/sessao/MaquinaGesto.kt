package com.ander.pcflow.sessao

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Medidas do gesto, em pixels. Quem monta a tela converte de dp para px com a
 * densidade do aparelho; os valores aqui são só o ponto de partida dos testes.
 */
data class ConfigGesto(
    /** Quanto o dedo pode escorregar sem que o toque deixe de ser um toque. */
    val folgaToque: Float = 24f,
    /** A partir daqui é pressão longa: clique direito ou início de arrasto. */
    val tempoPressaoLongaMs: Long = 420L,
    /** Janela para o segundo toque virar clique duplo de verdade. */
    val tempoDuploToqueMs: Long = 300L,
    /** O segundo toque precisa cair perto do primeiro para contar como duplo. */
    val raioDuploToque: Float = 96f,
    val aceleracao: Aceleracao = Aceleracao()
)

/**
 * Aceleração do modo relativo. Sem ela, atravessar a tela do PC exige varrer o
 * celular várias vezes; com ela, gesto curto e rápido anda muito, gesto lento
 * anda pouco — que é como um touchpad de notebook se comporta.
 */
data class Aceleracao(
    val base: Float = 1.0f,
    val extra: Float = 1.8f,
    /** Deslocamento de um evento em que a aceleração já está no máximo. */
    val deslocamentoPleno: Float = 28f
) {
    fun fator(deslocamento: Float): Float {
        if (deslocamentoPleno <= 0f) return base
        val proporcao = (abs(deslocamento) / deslocamentoPleno).coerceIn(0f, 1f)
        return base + extra * proporcao
    }
}

/**
 * Máquina de estados do gesto de um dedo.
 *
 * Ela existe separada do Composable por dois motivos: dá para testar sem
 * aparelho, e concentra num lugar só a regra que o contrato exige — todo
 * `Pressionar` tem o seu `Soltar`, mesmo se o gesto for cancelado, o modo
 * mudar no meio, chegar um segundo dedo ou a sessão cair.
 */
class MaquinaGesto(private val config: ConfigGesto = ConfigGesto()) {

    private enum class Fase { OCIOSO, TOQUE, MULTITOQUE }

    var modo: ModoEntrada = ModoEntrada.TOQUE_DIRETO
        private set

    /** Multiplica o movimento relativo; a tela usa 1/zoom para manter a precisão. */
    var escalaMovimento: Float = 1f

    /** Botão preso de propósito pelo usuário, para arrastar sem segurar o dedo. */
    var travaArraste: Boolean = false
        private set

    private var fase = Fase.OCIOSO
    private val segurados = linkedSetOf<BotaoMouse>()

    private var xInicial = 0f
    private var yInicial = 0f
    private var xAtual = 0f
    private var yAtual = 0f
    private var instanteDescida = 0L
    private var moveu = false
    /** O gesto já virou outra coisa (clique direito, arrasto): soltar não clica. */
    private var consumido = false
    private var arrastando = false
    private var candidatoDuplo = false

    private var instanteUltimaSolta = 0L
    private var xUltimaSolta = 0f
    private var yUltimaSolta = 0f

    /** Verdadeiro enquanto vale a pena esperar a pressão longa estourar. */
    val esperandoPressaoLonga: Boolean
        get() = fase == Fase.TOQUE && !moveu && !consumido

    val emGesto: Boolean get() = fase != Fase.OCIOSO

    val botoesSegurados: Set<BotaoMouse> get() = segurados

    // ------------------------------------------------------------------ modo

    /** Trocar de modo no meio de um arrasto largaria o botão preso no PC. */
    fun definirModo(novo: ModoEntrada): List<ComandoEntrada> {
        if (novo == modo) return emptyList()
        val saida = cancelar().toMutableList()
        modo = novo
        return saida
    }

    // ---------------------------------------------------------------- gestos

    fun descer(x: Float, y: Float, instante: Long): List<ComandoEntrada> {
        val saida = mutableListOf<ComandoEntrada>()
        if (fase != Fase.OCIOSO) saida += cancelar()

        fase = Fase.TOQUE
        xInicial = x; yInicial = y
        xAtual = x; yAtual = y
        instanteDescida = instante
        moveu = false
        consumido = false
        arrastando = false
        candidatoDuplo = instanteUltimaSolta > 0L &&
            instante - instanteUltimaSolta <= config.tempoDuploToqueMs &&
            hypot(x - xUltimaSolta, y - yUltimaSolta) <= config.raioDuploToque

        // No toque direto o ponteiro salta já na descida: é o que faz o dedo
        // "ser" o ponteiro em vez de o cursor aparecer só depois do clique.
        if (modo == ModoEntrada.TOQUE_DIRETO) saida += ComandoEntrada.PosicionarEm(x, y)
        return saida
    }

    fun mover(x: Float, y: Float, instante: Long): List<ComandoEntrada> {
        if (fase != Fase.TOQUE) { xAtual = x; yAtual = y; return emptyList() }

        val dx = x - xAtual
        val dy = y - yAtual
        xAtual = x; yAtual = y

        if (!moveu && hypot(x - xInicial, y - yInicial) < config.folgaToque) return emptyList()

        val saida = mutableListOf<ComandoEntrada>()
        val primeiroMovimento = !moveu
        moveu = true

        // Tocar, soltar e tocar de novo já arrastando é como se arrasta num
        // touchpad sem botão — e no toque direto poupa a pressão longa.
        if (primeiroMovimento && candidatoDuplo && !arrastando &&
            modo != ModoEntrada.MOUSE && !travaArraste
        ) {
            arrastando = true
            consumido = true
            saida += pressionar(BotaoMouse.ESQUERDO)
        }

        when (modo) {
            ModoEntrada.TOQUE_DIRETO -> saida += ComandoEntrada.PosicionarEm(x, y)
            ModoEntrada.TOUCHPAD, ModoEntrada.MOUSE -> {
                val fator = config.aceleracao.fator(hypot(dx, dy)) * escalaMovimento
                if (dx != 0f || dy != 0f) saida += ComandoEntrada.Mover(dx * fator, dy * fator)
            }
        }
        return saida
    }

    /**
     * Chamado quando o dedo ficou parado além de `tempoPressaoLongaMs`.
     * No toque direto vira clique direito (é o menu de contexto que falta no
     * celular); nos modos relativos vira início de arrasto.
     */
    fun pressaoLonga(instante: Long): List<ComandoEntrada> {
        if (!esperandoPressaoLonga) return emptyList()
        consumido = true
        return when (modo) {
            ModoEntrada.TOQUE_DIRETO -> listOf(
                ComandoEntrada.PosicionarEm(xAtual, yAtual),
                ComandoEntrada.Clicar(BotaoMouse.DIREITO, 1)
            )
            ModoEntrada.TOUCHPAD -> {
                if (arrastando || travaArraste) emptyList() else {
                    arrastando = true
                    listOf(pressionar(BotaoMouse.ESQUERDO))
                }
            }
            ModoEntrada.MOUSE -> emptyList()
        }
    }

    fun subir(instante: Long): List<ComandoEntrada> {
        if (fase == Fase.OCIOSO) return emptyList()
        val saida = mutableListOf<ComandoEntrada>()

        if (arrastando && !travaArraste) {
            arrastando = false
            saida += soltar(BotaoMouse.ESQUERDO)
        }

        // Com a trava ligada o botão já está pressionado: clicar por cima
        // mandaria um clique solto no meio do arrasto.
        val toqueLimpo = fase == Fase.TOQUE && !moveu && !consumido && !travaArraste
        if (toqueLimpo && modo != ModoEntrada.MOUSE) {
            if (modo == ModoEntrada.TOQUE_DIRETO) saida += ComandoEntrada.PosicionarEm(xAtual, yAtual)
            val cliques = if (candidatoDuplo) 2 else 1
            saida += ComandoEntrada.Clicar(BotaoMouse.ESQUERDO, cliques)
            // Depois de um duplo a contagem recomeça: três toques seguidos são
            // um duplo e um simples, nunca dois duplos encavalados.
            if (cliques == 2) {
                instanteUltimaSolta = 0L
            } else {
                instanteUltimaSolta = instante
                xUltimaSolta = xAtual; yUltimaSolta = yAtual
            }
        } else {
            instanteUltimaSolta = 0L
        }

        fase = Fase.OCIOSO
        moveu = false
        consumido = false
        candidatoDuplo = false
        return saida
    }

    /** Gesto abortado (segundo dedo, cancelamento do sistema, painel abrindo). */
    fun cancelar(): List<ComandoEntrada> {
        val saida = mutableListOf<ComandoEntrada>()
        if (arrastando && !travaArraste) {
            arrastando = false
            saida += soltar(BotaoMouse.ESQUERDO)
        }
        fase = Fase.OCIOSO
        moveu = false
        consumido = false
        candidatoDuplo = false
        instanteUltimaSolta = 0L
        return saida
    }

    /** Chegou um segundo dedo: o gesto de um dedo acaba aqui, sem clicar. */
    fun entrarMultitoque(): List<ComandoEntrada> {
        val saida = cancelar().toMutableList()
        fase = Fase.MULTITOQUE
        return saida
    }

    fun sairMultitoque() {
        if (fase == Fase.MULTITOQUE) fase = Fase.OCIOSO
    }

    /**
     * Fim de tudo: sessão caiu, tela fechou, monitor trocou. Solta cada botão
     * que ainda estiver preso, inclusive o da trava de arrasto.
     */
    fun encerrar(): List<ComandoEntrada> {
        val saida = cancelar().toMutableList()
        travaArraste = false
        arrastando = false
        for (botao in segurados.toList()) saida += soltar(botao)
        return saida
    }

    // ------------------------------------------------------------- comandos

    /** Trava de arrasto: prende o botão esquerdo até o usuário desligar. */
    fun definirTravaArraste(ativa: Boolean): List<ComandoEntrada> {
        if (ativa == travaArraste) return emptyList()
        travaArraste = ativa
        return if (ativa) {
            arrastando = false
            listOf(pressionar(BotaoMouse.ESQUERDO))
        } else {
            listOf(soltar(BotaoMouse.ESQUERDO))
        }
    }

    /** Clique disparado por botão da barra, não por gesto. */
    fun cliqueManual(botao: BotaoMouse, cliques: Int = 1): List<ComandoEntrada> =
        listOf(ComandoEntrada.Clicar(botao, cliques.coerceIn(1, 2)))

    private fun pressionar(botao: BotaoMouse): ComandoEntrada {
        segurados += botao
        return ComandoEntrada.Pressionar(botao)
    }

    private fun soltar(botao: BotaoMouse): ComandoEntrada {
        segurados -= botao
        return ComandoEntrada.Soltar(botao)
    }
}

/**
 * Escolhe o eixo da rolagem de dois dedos e não fica trocando.
 *
 * Sem histerese o dedo humano — que nunca anda reto — faz o eixo piscar entre
 * vertical e horizontal, e a página treme. Aqui o eixo só troca quando o outro
 * sentido acumula bem mais que o atual.
 */
class DecisorEixo(
    private val limiar: Float = 28f,
    private val razaoTroca: Float = 2.5f
) {
    var eixo: Eixo = Eixo.INDEFINIDO
        private set

    private var somaX = 0f
    private var somaY = 0f

    fun atualizar(dx: Float, dy: Float): Eixo {
        somaX += abs(dx)
        somaY += abs(dy)
        when (eixo) {
            Eixo.INDEFINIDO ->
                if (somaX >= limiar || somaY >= limiar) fixar(if (somaX > somaY) Eixo.HORIZONTAL else Eixo.VERTICAL)
            Eixo.VERTICAL ->
                if (somaX >= limiar && somaX > somaY * razaoTroca) fixar(Eixo.HORIZONTAL)
            Eixo.HORIZONTAL ->
                if (somaY >= limiar && somaY > somaX * razaoTroca) fixar(Eixo.VERTICAL)
        }
        return eixo
    }

    private fun fixar(novo: Eixo) {
        eixo = novo
        somaX = 0f
        somaY = 0f
    }

    fun reiniciar() {
        eixo = Eixo.INDEFINIDO
        somaX = 0f
        somaY = 0f
    }
}

/**
 * Junta pixels de dedo até fechar um "clique" de roda. O Windows conta roda em
 * múltiplos de 120; mandar valor quebrado faz aplicativo nenhum rolar direito.
 */
class AcumuladorRolagem(private val pixelsPorEntalhe: Float = 42f) {
    private var resto = 0f

    fun acumular(deslocamento: Float): Int {
        if (pixelsPorEntalhe <= 0f) return 0
        resto += deslocamento
        val entalhes = (resto / pixelsPorEntalhe).toInt()
        if (entalhes == 0) return 0
        resto -= entalhes * pixelsPorEntalhe
        return entalhes * 120
    }

    fun reiniciar() { resto = 0f }
}
