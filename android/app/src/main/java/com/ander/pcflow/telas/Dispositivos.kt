package com.ander.pcflow.telas

import com.ander.pcflow.PcEncontrado

/**
 * Um computador como a casca do aplicativo enxerga: o que a busca na rede
 * achou agora, somado ao que já foi usado antes e continua guardado.
 */
data class Dispositivo(
    val id: String,
    val nome: String,
    val apelido: String = "",
    val host: String = "",
    val porta: Int = 45456,
    val portaTela: Int = 45457,
    val portaArquivos: Int = 45458,
    val tls: String = "",
    val monitores: Int = 1,
    /** Apareceu na busca desta rede agora. É o que define online/offline. */
    val naRede: Boolean = false,
    val favorito: Boolean = false,
    val ultimoAcessoMs: Long = 0L
) {
    /** Sem ID (destino vindo de código), o endereço é o que identifica. */
    val chave: String get() = id.ifBlank { host }

    val rotulo: String get() = apelido.ifBlank { nome }

    val online: Boolean get() = naRede

    fun paraConexao(): PcEncontrado = PcEncontrado(
        nome = rotulo,
        host = host,
        porta = porta,
        portaTela = portaTela,
        portaArquivos = portaArquivos,
        maquinaId = id,
        tls = tls,
        monitores = monitores
    )

    companion object {
        fun de(pc: PcEncontrado, naRede: Boolean = true): Dispositivo = Dispositivo(
            id = pc.maquinaId,
            nome = pc.nome,
            host = pc.host,
            porta = pc.porta,
            portaTela = pc.portaTela,
            portaArquivos = pc.portaArquivos,
            tls = pc.tls,
            monitores = pc.monitores,
            naRede = naRede
        )
    }
}

enum class FiltroDispositivos(val rotulo: String) {
    TODOS("Todos"),
    ONLINE("Na rede"),
    FAVORITOS("Favoritos")
}

enum class TipoSecao(val titulo: String) {
    FAVORITOS("Favoritos"),
    NA_REDE("Na mesma rede"),
    TODOS("Todos os dispositivos")
}

data class SecaoDispositivos(val tipo: TipoSecao, val itens: List<Dispositivo>) {
    val contagem: Int get() = itens.size
}

object ListaDispositivos {

    /**
     * Junta as três origens numa lista só. O que está na rede agora manda no
     * endereço e no estado; o que está guardado manda no apelido e no último
     * acesso — assim um computador desligado continua aparecendo com o nome
     * que a pessoa deu.
     */
    fun combinar(
        descobertos: List<PcEncontrado>,
        lembrados: List<Dispositivo>,
        favoritos: Set<String> = emptySet(),
        apelidos: Map<String, String> = emptyMap()
    ): List<Dispositivo> {
        val porChave = LinkedHashMap<String, Dispositivo>()

        for (guardado in lembrados) {
            if (guardado.chave.isBlank()) continue
            porChave[guardado.chave] = guardado.copy(naRede = false)
        }
        for (pc in descobertos) {
            val novo = Dispositivo.de(pc, naRede = true)
            if (novo.chave.isBlank()) continue
            val anterior = porChave[novo.chave]
            porChave[novo.chave] = novo.copy(
                apelido = anterior?.apelido.orEmpty(),
                ultimoAcessoMs = anterior?.ultimoAcessoMs ?: 0L
            )
        }
        return porChave.values.map { dispositivo ->
            dispositivo.copy(
                apelido = apelidos[dispositivo.chave] ?: dispositivo.apelido,
                favorito = favoritos.contains(dispositivo.chave)
            )
        }
    }

    /** Busca por apelido, nome, endereço ou ID — com ou sem os espaços do ID. */
    fun filtrar(
        dispositivos: List<Dispositivo>,
        busca: String = "",
        filtro: FiltroDispositivos = FiltroDispositivos.TODOS
    ): List<Dispositivo> {
        val termo = busca.trim().lowercase()
        val digitos = Identidade.somenteDigitos(busca)
        return dispositivos
            .filter { dispositivo ->
                when (filtro) {
                    FiltroDispositivos.TODOS -> true
                    FiltroDispositivos.ONLINE -> dispositivo.online
                    FiltroDispositivos.FAVORITOS -> dispositivo.favorito
                }
            }
            .filter { dispositivo ->
                if (termo.isEmpty()) true
                else dispositivo.rotulo.lowercase().contains(termo) ||
                    dispositivo.nome.lowercase().contains(termo) ||
                    dispositivo.host.lowercase().contains(termo) ||
                    (digitos.isNotEmpty() && dispositivo.id.contains(digitos))
            }
    }

    /** Favorito antes, depois quem está na rede, depois ordem alfabética. */
    fun ordenar(dispositivos: List<Dispositivo>): List<Dispositivo> =
        dispositivos.sortedWith(
            compareByDescending<Dispositivo> { it.favorito }
                .thenByDescending { it.online }
                .thenByDescending { it.ultimoAcessoMs }
                .thenBy { it.rotulo.lowercase() }
                .thenBy { it.chave }
        )

    /**
     * Seções do mockup. Cada dispositivo aparece uma vez só: favorito manda
     * sobre estar na rede, e "Todos" recolhe o que sobrou — contagem sempre
     * igual ao que está listado embaixo dela.
     */
    fun agrupar(dispositivos: List<Dispositivo>): List<SecaoDispositivos> {
        val ordenados = ordenar(dispositivos)
        val favoritos = ordenados.filter { it.favorito }
        val naRede = ordenados.filter { !it.favorito && it.online }
        val resto = ordenados.filter { !it.favorito && !it.online }
        return listOf(
            SecaoDispositivos(TipoSecao.FAVORITOS, favoritos),
            SecaoDispositivos(TipoSecao.NA_REDE, naRede),
            SecaoDispositivos(TipoSecao.TODOS, resto)
        ).filter { it.itens.isNotEmpty() }
    }

    fun secoes(
        dispositivos: List<Dispositivo>,
        busca: String = "",
        filtro: FiltroDispositivos = FiltroDispositivos.TODOS
    ): List<SecaoDispositivos> = agrupar(filtrar(dispositivos, busca, filtro))
}
