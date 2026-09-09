package com.ander.pcflow.telas

import com.ander.pcflow.PcEncontrado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispositivosTest {

    private fun pc(id: String, nome: String, host: String = "192.168.0.$id".take(15)) =
        PcEncontrado(nome = nome, host = host, maquinaId = id, tls = "aa")

    @Test
    fun `descoberto na rede fica online e o guardado sozinho fica offline`() {
        val lista = ListaDispositivos.combinar(
            descobertos = listOf(pc("145929922", "PC de Casa")),
            lembrados = listOf(Dispositivo(id = "892771502", nome = "PC do Escritório"))
        )
        assertEquals(2, lista.size)
        assertTrue(lista.first { it.id == "145929922" }.online)
        assertFalse(lista.first { it.id == "892771502" }.online)
    }

    @Test
    fun `apelido e favorito guardados vencem o nome de fabrica`() {
        val lista = ListaDispositivos.combinar(
            descobertos = listOf(pc("145929922", "DESKTOP-8F2K")),
            lembrados = emptyList(),
            favoritos = setOf("145929922"),
            apelidos = mapOf("145929922" to "PC de Casa")
        )
        val unico = lista.single()
        assertEquals("PC de Casa", unico.rotulo)
        assertEquals("DESKTOP-8F2K", unico.nome)
        assertTrue(unico.favorito)
    }

    @Test
    fun `o mesmo computador nao aparece duas vezes`() {
        val lista = ListaDispositivos.combinar(
            descobertos = listOf(pc("145929922", "PC de Casa")),
            lembrados = listOf(Dispositivo(id = "145929922", nome = "PC de Casa", ultimoAcessoMs = 500))
        )
        assertEquals(1, lista.size)
        assertTrue(lista.single().online)
        assertEquals(500L, lista.single().ultimoAcessoMs)
    }

    @Test
    fun `busca aceita nome apelido endereco e id com ou sem espacos`() {
        val lista = listOf(
            Dispositivo(id = "145929922", nome = "DESKTOP-8F2K", apelido = "PC de Casa", host = "192.168.0.31"),
            Dispositivo(id = "892771502", nome = "Notebook Trabalho", host = "192.168.0.44")
        )
        assertEquals(1, ListaDispositivos.filtrar(lista, "casa").size)
        assertEquals(1, ListaDispositivos.filtrar(lista, "DESKTOP").size)
        assertEquals(1, ListaDispositivos.filtrar(lista, "0.44").size)
        assertEquals(1, ListaDispositivos.filtrar(lista, "145 929 922").size)
        assertEquals(2, ListaDispositivos.filtrar(lista, "   ").size)
        assertEquals(0, ListaDispositivos.filtrar(lista, "impressora").size)
    }

    @Test
    fun `filtro de online e de favoritos separa a lista`() {
        val lista = listOf(
            Dispositivo(id = "1", nome = "A", naRede = true),
            Dispositivo(id = "2", nome = "B", favorito = true),
            Dispositivo(id = "3", nome = "C")
        )
        assertEquals(listOf("1"), ListaDispositivos.filtrar(lista, filtro = FiltroDispositivos.ONLINE).map { it.id })
        assertEquals(listOf("2"), ListaDispositivos.filtrar(lista, filtro = FiltroDispositivos.FAVORITOS).map { it.id })
        assertEquals(3, ListaDispositivos.filtrar(lista, filtro = FiltroDispositivos.TODOS).size)
    }

    @Test
    fun `ordena favorito depois online depois alfabetico`() {
        val lista = listOf(
            Dispositivo(id = "4", nome = "Zebra", naRede = false),
            Dispositivo(id = "3", nome = "Alfa", naRede = false),
            Dispositivo(id = "2", nome = "Beta", naRede = true),
            Dispositivo(id = "1", nome = "Ômega", naRede = true, favorito = true)
        )
        assertEquals(listOf("1", "2", "3", "4"), ListaDispositivos.ordenar(lista).map { it.id })
    }

    @Test
    fun `secoes nao repetem dispositivo e a contagem bate com a lista`() {
        val lista = listOf(
            Dispositivo(id = "1", nome = "Favorito online", naRede = true, favorito = true),
            Dispositivo(id = "2", nome = "Favorito offline", favorito = true),
            Dispositivo(id = "3", nome = "Na rede", naRede = true),
            Dispositivo(id = "4", nome = "Desligado")
        )
        val secoes = ListaDispositivos.secoes(lista)
        assertEquals(
            listOf(TipoSecao.FAVORITOS, TipoSecao.NA_REDE, TipoSecao.TODOS),
            secoes.map { it.tipo }
        )
        assertEquals(2, secoes[0].contagem)
        assertEquals(1, secoes[1].contagem)
        assertEquals(1, secoes[2].contagem)
        val todos = secoes.flatMap { it.itens }.map { it.id }
        assertEquals(todos.size, todos.toSet().size)
        assertEquals(lista.size, todos.size)
    }

    @Test
    fun `secao vazia nao aparece`() {
        val secoes = ListaDispositivos.secoes(listOf(Dispositivo(id = "1", nome = "Só ele", naRede = true)))
        assertEquals(listOf(TipoSecao.NA_REDE), secoes.map { it.tipo })
    }

    @Test
    fun `sem id o endereco vira a chave`() {
        val semId = Dispositivo(id = "", nome = "PC por código", host = "203.0.113.7")
        assertEquals("203.0.113.7", semId.chave)
    }
}
