package com.ander.pcflow.telas

import com.ander.pcflow.ModoClipboard
import com.ander.pcflow.PerfilVideo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Onde a casca guarda o que precisa sobreviver a fechar o aplicativo.
 * É uma interface para a lógica de persistência poder ser testada sem Android.
 */
interface Armazem {
    fun ler(chave: String, padrao: String = ""): String
    fun escrever(chave: String, valor: String)
}

/** Usado no teste e como reserva quando o contexto ainda não chegou. */
class ArmazemMemoria(inicial: Map<String, String> = emptyMap()) : Armazem {
    private val dados = HashMap<String, String>(inicial)
    override fun ler(chave: String, padrao: String): String = dados[chave] ?: padrao
    override fun escrever(chave: String, valor: String) { dados[chave] = valor }
    fun cru(): Map<String, String> = dados.toMap()
}

/**
 * Favoritos, apelidos, dispositivos conhecidos, histórico de sessões e ajustes.
 *
 * Tudo em JSON dentro das preferências: é pouca coisa, não vale um banco, e
 * assim continua legível quando alguém precisar depurar.
 */
class RepositorioCasca(private val armazem: Armazem) {

    fun favoritos(): Set<String> = lerLista(CHAVE_FAVORITOS).toSet()

    fun definirFavorito(chave: String, favorito: Boolean): Set<String> {
        if (chave.isBlank()) return favoritos()
        val atual = favoritos().toMutableSet()
        if (favorito) atual.add(chave) else atual.remove(chave)
        escreverLista(CHAVE_FAVORITOS, atual.toList())
        return atual
    }

    fun alternarFavorito(chave: String): Set<String> =
        definirFavorito(chave, !favoritos().contains(chave))

    fun apelidos(): Map<String, String> {
        val objeto = lerObjeto(CHAVE_APELIDOS)
        return objeto.keys().asSequence().associateWith { objeto.optString(it, "") }
            .filterValues { it.isNotBlank() }
    }

    /** Apelido em branco volta ao nome de fábrica em vez de gravar vazio. */
    fun definirApelido(chave: String, apelido: String): Map<String, String> {
        if (chave.isBlank()) return apelidos()
        val objeto = lerObjeto(CHAVE_APELIDOS)
        val limpo = apelido.trim().take(60)
        if (limpo.isBlank()) objeto.remove(chave) else objeto.put(chave, limpo)
        armazem.escrever(CHAVE_APELIDOS, objeto.toString())
        return apelidos()
    }

    fun lembrados(): List<Dispositivo> {
        val lista = ArrayList<Dispositivo>()
        val array = lerArray(CHAVE_LEMBRADOS)
        for (indice in 0 until array.length()) {
            val item = array.optJSONObject(indice) ?: continue
            val id = item.optString("id")
            val host = item.optString("host")
            if (id.isBlank() && host.isBlank()) continue
            lista.add(
                Dispositivo(
                    id = id,
                    nome = item.optString("nome").ifBlank { "Computador" },
                    apelido = item.optString("apelido"),
                    host = host,
                    porta = item.optInt("porta", 45456),
                    portaTela = item.optInt("portaTela", 45457),
                    portaArquivos = item.optInt("portaArquivos", 45458),
                    tls = item.optString("tls"),
                    monitores = item.optInt("monitores", 1),
                    ultimoAcessoMs = item.optLong("ultimoAcessoMs", 0L)
                )
            )
        }
        return lista
    }

    /** Chamado quando a conexão dá certo: é o que faz o PC desligado continuar na lista. */
    fun lembrar(dispositivo: Dispositivo, quandoMs: Long): List<Dispositivo> {
        if (dispositivo.chave.isBlank()) return lembrados()
        val atualizado = lembrados()
            .filterNot { it.chave == dispositivo.chave }
            .toMutableList()
        atualizado.add(0, dispositivo.copy(ultimoAcessoMs = quandoMs, naRede = false))
        val cortado = atualizado.take(LIMITE_LEMBRADOS)
        escreverDispositivos(cortado)
        return cortado
    }

    fun esquecer(chave: String): List<Dispositivo> {
        val restante = lembrados().filterNot { it.chave == chave }
        escreverDispositivos(restante)
        return restante
    }

    fun historico(): List<RegistroSessao> {
        val lista = ArrayList<RegistroSessao>()
        val array = lerArray(CHAVE_HISTORICO)
        for (indice in 0 until array.length()) {
            val item = array.optJSONObject(indice) ?: continue
            lista.add(
                RegistroSessao(
                    inicioMs = item.optLong("inicioMs", 0L),
                    duracaoSegundos = item.optLong("duracaoSegundos", 0L),
                    dispositivoId = item.optString("dispositivoId"),
                    nome = item.optString("nome").ifBlank { "Computador" },
                    resultado = lerEnum(item.optString("resultado"), ResultadoSessao.CONCLUIDA)
                )
            )
        }
        return lista
    }

    fun registrarSessao(registro: RegistroSessao): List<RegistroSessao> {
        val novo = Historico.registrar(historico(), registro)
        val array = JSONArray()
        novo.forEach { sessao ->
            array.put(
                JSONObject()
                    .put("inicioMs", sessao.inicioMs)
                    .put("duracaoSegundos", sessao.duracaoSegundos)
                    .put("dispositivoId", sessao.dispositivoId)
                    .put("nome", sessao.nome)
                    .put("resultado", sessao.resultado.name)
            )
        }
        armazem.escrever(CHAVE_HISTORICO, array.toString())
        return novo
    }

    fun limparHistorico() {
        armazem.escrever(CHAVE_HISTORICO, JSONArray().toString())
    }

    fun ajustes(): AjustesCasca {
        val objeto = lerObjeto(CHAVE_AJUSTES)
        return AjustesCasca(
            tema = lerEnum(objeto.optString("tema"), TemaApp.ESCURO),
            qualidade = lerEnum(objeto.optString("qualidade"), PerfilVideo.EQUILIBRADO),
            modoToque = lerEnum(objeto.optString("modoToque"), ModoToquePadrao.DIRETO),
            modoClipboard = lerEnum(objeto.optString("modoClipboard"), ModoClipboard.MANUAL),
            servidorRetransmissao = objeto.optString("servidor")
        )
    }

    fun salvarAjustes(ajustes: AjustesCasca): AjustesCasca {
        armazem.escrever(
            CHAVE_AJUSTES,
            JSONObject()
                .put("tema", ajustes.tema.name)
                .put("qualidade", ajustes.qualidade.name)
                .put("modoToque", ajustes.modoToque.name)
                .put("modoClipboard", ajustes.modoClipboard.name)
                .put("servidor", ajustes.servidorRetransmissao.trim())
                .toString()
        )
        return ajustes()
    }

    private fun escreverDispositivos(dispositivos: List<Dispositivo>) {
        val array = JSONArray()
        dispositivos.forEach { dispositivo ->
            array.put(
                JSONObject()
                    .put("id", dispositivo.id)
                    .put("nome", dispositivo.nome)
                    .put("apelido", dispositivo.apelido)
                    .put("host", dispositivo.host)
                    .put("porta", dispositivo.porta)
                    .put("portaTela", dispositivo.portaTela)
                    .put("portaArquivos", dispositivo.portaArquivos)
                    .put("tls", dispositivo.tls)
                    .put("monitores", dispositivo.monitores)
                    .put("ultimoAcessoMs", dispositivo.ultimoAcessoMs)
            )
        }
        armazem.escrever(CHAVE_LEMBRADOS, array.toString())
    }

    private fun lerLista(chave: String): List<String> {
        val array = lerArray(chave)
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    private fun escreverLista(chave: String, valores: List<String>) {
        val array = JSONArray()
        valores.forEach { array.put(it) }
        armazem.escrever(chave, array.toString())
    }

    private fun lerArray(chave: String): JSONArray =
        runCatching { JSONArray(armazem.ler(chave, "[]")) }.getOrDefault(JSONArray())

    private fun lerObjeto(chave: String): JSONObject =
        runCatching { JSONObject(armazem.ler(chave, "{}")) }.getOrDefault(JSONObject())

    companion object {
        const val CHAVE_FAVORITOS = "casca_favoritos"
        const val CHAVE_APELIDOS = "casca_apelidos"
        const val CHAVE_LEMBRADOS = "casca_dispositivos"
        const val CHAVE_HISTORICO = "casca_historico"
        const val CHAVE_AJUSTES = "casca_ajustes"
        const val LIMITE_LEMBRADOS = 40
    }
}
