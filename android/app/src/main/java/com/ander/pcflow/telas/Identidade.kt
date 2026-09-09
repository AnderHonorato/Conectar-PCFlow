package com.ander.pcflow.telas

import com.ander.pcflow.CodigoAcesso

/**
 * Regras do número que identifica um computador e do código de acesso.
 *
 * Fica fora dos Composables de propósito: é a parte que dá para provar com
 * teste de unidade, e é a que erra silenciosamente quando alguém digita.
 */
object Identidade {

    /** O PCFlow do Windows mostra o ID sempre com nove dígitos. */
    const val TAMANHO_ID = 9

    fun somenteDigitos(texto: String): String = texto.filter(Char::isDigit)

    /**
     * Quebra o número em grupos de três a partir da direita — é assim que se
     * lê um ID em voz alta para outra pessoa digitar.
     */
    fun formatarId(id: String): String {
        val digitos = somenteDigitos(id)
        if (digitos.isEmpty()) return ""
        val grupos = ArrayList<String>()
        var fim = digitos.length
        while (fim > 0) {
            val inicio = maxOf(0, fim - 3)
            grupos.add(digitos.substring(inicio, fim))
            fim = inicio
        }
        return grupos.reversed().joinToString(" ")
    }

    /** Recorta o que o usuário digita no campo de ID enquanto ele digita. */
    fun aoDigitarId(texto: String): String = somenteDigitos(texto).take(TAMANHO_ID)

    enum class MotivoId { OK, VAZIO, NAO_NUMERICO, CURTO, LONGO }

    data class ResultadoId(val motivo: MotivoId, val id: String, val mensagem: String?) {
        val valido: Boolean get() = motivo == MotivoId.OK
    }

    fun validarId(texto: String): ResultadoId {
        val cru = texto.trim()
        val digitos = somenteDigitos(cru)
        return when {
            cru.isEmpty() -> ResultadoId(
                MotivoId.VAZIO, "",
                "Digite o ID de $TAMANHO_ID dígitos que aparece no computador."
            )
            digitos.length != cru.replace(" ", "").length -> ResultadoId(
                MotivoId.NAO_NUMERICO, digitos,
                "O ID tem só números — confira o que foi digitado."
            )
            digitos.length < TAMANHO_ID -> ResultadoId(
                MotivoId.CURTO, digitos,
                "Faltam ${TAMANHO_ID - digitos.length} dígitos para completar o ID."
            )
            digitos.length > TAMANHO_ID -> ResultadoId(
                MotivoId.LONGO, digitos,
                "O ID tem $TAMANHO_ID dígitos; você digitou ${digitos.length}."
            )
            else -> ResultadoId(MotivoId.OK, digitos, null)
        }
    }

    enum class MotivoCodigo { OK, VAZIO, INVALIDO, SERVIDOR_FALTANDO }

    data class ResultadoCodigo(val motivo: MotivoCodigo, val mensagem: String?) {
        val valido: Boolean get() = motivo == MotivoCodigo.OK
    }

    /**
     * O código de acesso já carrega endereço e identidade do PC. Quando ele é
     * do tipo que passa por retransmissão, sem o endereço do servidor não há
     * para onde ligar — por isso os dois erros são separados.
     */
    fun validarCodigo(codigo: String, servidor: String): ResultadoCodigo {
        val limpo = codigo.trim()
        if (limpo.isEmpty()) return ResultadoCodigo(
            MotivoCodigo.VAZIO,
            "Cole o código que o computador mostra em \"Pela internet\"."
        )
        val destino = CodigoAcesso.ler(limpo) ?: return ResultadoCodigo(
            MotivoCodigo.INVALIDO,
            "Código não reconhecido. Copie de novo no computador, inteiro."
        )
        if (!destino.direto && servidor.isBlank()) return ResultadoCodigo(
            MotivoCodigo.SERVIDOR_FALTANDO,
            "Esse código passa por retransmissão: preencha o servidor abaixo."
        )
        return ResultadoCodigo(MotivoCodigo.OK, null)
    }
}
