package com.ander.pcflow.telas

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricoTest {

    private val zona = TimeZone.getTimeZone("America/Sao_Paulo")

    private fun instante(ano: Int, mes: Int, dia: Int, hora: Int, minuto: Int): Long =
        Calendar.getInstance(zona, Locale.forLanguageTag("pt-BR")).apply {
            clear()
            set(ano, mes - 1, dia, hora, minuto, 0)
        }.timeInMillis

    @Test
    fun `duracao aparece em hora minuto e segundo`() {
        assertEquals("44 s", Historico.formatarDuracao(44))
        assertEquals("12 min 03 s", Historico.formatarDuracao(723))
        assertEquals("1 h 05 min", Historico.formatarDuracao(3900))
        assertEquals("menos de 1 s", Historico.formatarDuracao(0))
    }

    @Test
    fun `hoje e ontem sao ditos por extenso`() {
        val agora = instante(2026, 3, 12, 20, 10)
        assertEquals("Hoje, 14:27", Historico.formatarQuando(instante(2026, 3, 12, 14, 27), agora, zona))
        assertEquals("Ontem, 19:52", Historico.formatarQuando(instante(2026, 3, 11, 19, 52), agora, zona))
    }

    @Test
    fun `ontem conta por dia do calendario e nao por vinte e quatro horas`() {
        val agora = instante(2026, 3, 12, 0, 30)
        assertEquals("Ontem, 23:50", Historico.formatarQuando(instante(2026, 3, 11, 23, 50), agora, zona))
    }

    @Test
    fun `mais antigo mostra data e outro ano mostra o ano`() {
        val agora = instante(2026, 3, 12, 20, 10)
        assertEquals("05/03, 09:15", Historico.formatarQuando(instante(2026, 3, 5, 9, 15), agora, zona))
        assertEquals("28/12/2025", Historico.formatarQuando(instante(2025, 12, 28, 9, 15), agora, zona))
    }

    @Test
    fun `registro novo entra na frente e a lista tem limite`() {
        var lista = emptyList<RegistroSessao>()
        repeat(Historico.LIMITE + 5) { indice ->
            lista = Historico.registrar(
                lista,
                RegistroSessao(indice.toLong(), 10, "1", "PC", ResultadoSessao.CONCLUIDA)
            )
        }
        assertEquals(Historico.LIMITE, lista.size)
        assertEquals((Historico.LIMITE + 4).toLong(), lista.first().inicioMs)
    }
}
