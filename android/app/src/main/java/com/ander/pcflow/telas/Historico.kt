package com.ander.pcflow.telas

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Como a sessão terminou. É isso que a aba Recentes mostra na direita. */
enum class ResultadoSessao(val rotulo: String) {
    CONCLUIDA("Concluída"),
    INTERROMPIDA("Interrompida"),
    RECUSADA("Recusada"),
    FALHOU("Falhou")
}

data class RegistroSessao(
    val inicioMs: Long,
    val duracaoSegundos: Long,
    val dispositivoId: String,
    val nome: String,
    val resultado: ResultadoSessao
)

object Historico {

    /** Guardar mais que isso só engorda as preferências sem serventia. */
    const val LIMITE = 60

    /** O mais recente primeiro — é a ordem em que a tela lê. */
    fun registrar(atual: List<RegistroSessao>, novo: RegistroSessao): List<RegistroSessao> =
        (listOf(novo) + atual).take(LIMITE)

    fun formatarDuracao(segundos: Long): String {
        if (segundos <= 0) return "menos de 1 s"
        val horas = segundos / 3600
        val minutos = (segundos % 3600) / 60
        val restantes = segundos % 60
        return when {
            horas > 0 -> "${horas} h ${minutos.toString().padStart(2, '0')} min"
            minutos > 0 -> "${minutos} min ${restantes.toString().padStart(2, '0')} s"
            else -> "$restantes s"
        }
    }

    /**
     * "Hoje, 14:27" e "Ontem, 19:52" dizem mais que uma data completa; mais
     * atrás disso a data volta a ser necessária.
     */
    fun formatarQuando(
        inicioMs: Long,
        agoraMs: Long,
        zona: TimeZone = TimeZone.getDefault()
    ): String {
        val quando = calendario(inicioMs, zona)
        val agora = calendario(agoraMs, zona)
        val hora = "%02d:%02d".format(
            Locale.forLanguageTag("pt-BR"),
            quando.get(Calendar.HOUR_OF_DAY),
            quando.get(Calendar.MINUTE)
        )
        val diferencaDias = diasDesdeEpoca(agora) - diasDesdeEpoca(quando)
        return when {
            diferencaDias == 0L -> "Hoje, $hora"
            diferencaDias == 1L -> "Ontem, $hora"
            quando.get(Calendar.YEAR) == agora.get(Calendar.YEAR) -> "%02d/%02d, %s".format(
                Locale.forLanguageTag("pt-BR"),
                quando.get(Calendar.DAY_OF_MONTH),
                quando.get(Calendar.MONTH) + 1,
                hora
            )
            else -> "%02d/%02d/%d".format(
                Locale.forLanguageTag("pt-BR"),
                quando.get(Calendar.DAY_OF_MONTH),
                quando.get(Calendar.MONTH) + 1,
                quando.get(Calendar.YEAR)
            )
        }
    }

    private fun calendario(instante: Long, zona: TimeZone): Calendar =
        Calendar.getInstance(zona, Locale.forLanguageTag("pt-BR")).apply { timeInMillis = instante }

    /** Dia do calendário, não janela de 24 h: 23:59 e 00:01 são dias diferentes. */
    private fun diasDesdeEpoca(calendario: Calendar): Long {
        val copia = calendario.clone() as Calendar
        copia.set(Calendar.HOUR_OF_DAY, 0)
        copia.set(Calendar.MINUTE, 0)
        copia.set(Calendar.SECOND, 0)
        copia.set(Calendar.MILLISECOND, 0)
        val deslocamento = copia.get(Calendar.ZONE_OFFSET) + copia.get(Calendar.DST_OFFSET)
        return (copia.timeInMillis + deslocamento) / 86_400_000L
    }
}
