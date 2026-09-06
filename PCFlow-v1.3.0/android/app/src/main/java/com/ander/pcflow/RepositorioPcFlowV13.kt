package com.ander.pcflow

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Estado local da experiência V1.3.
 * Mantém histórico, favoritos, apelidos e contatos sem depender de um backend em nuvem.
 */
object RepositorioPcFlowV13 {
    private const val PREFS = "pcflow_v13_ui"
    private const val CHAVE_DISPOSITIVOS = "dispositivos"
    private const val CHAVE_RECENTES = "recentes"
    private const val CHAVE_CONTATOS = "contatos"
    private const val CHAVE_CONVITES = "convites"

    private var contexto: Context? = null
    private val _dispositivos = MutableStateFlow<List<DispositivoSalvoV13>>(emptyList())
    val dispositivos: StateFlow<List<DispositivoSalvoV13>> = _dispositivos.asStateFlow()

    private val _recentes = MutableStateFlow<List<RegistroConexaoV13>>(emptyList())
    val recentes: StateFlow<List<RegistroConexaoV13>> = _recentes.asStateFlow()

    private val _contatos = MutableStateFlow<List<ContatoV13>>(emptyList())
    val contatos: StateFlow<List<ContatoV13>> = _contatos.asStateFlow()

    private val _convites = MutableStateFlow<List<ConviteV13>>(emptyList())
    val convites: StateFlow<List<ConviteV13>> = _convites.asStateFlow()

    fun inicializar(context: Context) {
        if (contexto != null) return
        contexto = context.applicationContext
        carregar()
    }

    fun absorverDescoberta(pcs: List<PcEncontrado>) {
        if (pcs.isEmpty()) return
        val atuais = _dispositivos.value.associateBy { it.chave }.toMutableMap()
        var mudou = false
        pcs.forEach { pc ->
            val chave = chave(pc)
            val anterior = atuais[chave]
            val novo = DispositivoSalvoV13(
                chave = chave,
                nome = anterior?.nome ?: pc.nome,
                host = pc.host,
                porta = pc.porta,
                portaTela = pc.portaTela,
                portaArquivos = pc.portaArquivos,
                maquinaId = pc.maquinaId,
                tls = pc.tls,
                monitores = pc.monitores,
                favorito = anterior?.favorito ?: false,
                ultimoVisto = System.currentTimeMillis(),
                sistema = anterior?.sistema ?: "Windows"
            )
            if (novo != anterior) {
                atuais[chave] = novo
                mudou = true
            }
        }
        if (mudou) atualizarDispositivos(atuais.values.sortedByDescending { it.ultimoVisto })
    }

    fun registrarConexao(pc: PcEncontrado) {
        absorverDescoberta(listOf(pc))
        val agora = System.currentTimeMillis()
        val chave = chave(pc)
        val lista = _recentes.value.toMutableList()
        lista.add(0, RegistroConexaoV13(UUID.randomUUID().toString(), chave, pc.nome, agora, true))
        atualizarRecentes(lista.take(80))
    }

    fun registrarFalha(pc: PcEncontrado) {
        val agora = System.currentTimeMillis()
        val chave = chave(pc)
        val lista = _recentes.value.toMutableList()
        lista.add(0, RegistroConexaoV13(UUID.randomUUID().toString(), chave, pc.nome, agora, false))
        atualizarRecentes(lista.take(80))
    }

    fun alternarFavorito(chave: String) {
        atualizarDispositivos(_dispositivos.value.map {
            if (it.chave == chave) it.copy(favorito = !it.favorito) else it
        })
    }

    fun renomearDispositivo(chave: String, novoNome: String) {
        val nome = novoNome.trim()
        if (nome.isBlank()) return
        atualizarDispositivos(_dispositivos.value.map {
            if (it.chave == chave) it.copy(nome = nome) else it
        })
        atualizarContatos(_contatos.value.map {
            if (it.dispositivoChave == chave && it.nomeGerenciadoAutomaticamente) it.copy(nome = nome) else it
        })
    }

    fun removerDispositivo(chave: String) {
        atualizarDispositivos(_dispositivos.value.filterNot { it.chave == chave })
        atualizarContatos(_contatos.value.filterNot { it.dispositivoChave == chave })
    }

    fun pcPorChave(chave: String): PcEncontrado? = _dispositivos.value.firstOrNull { it.chave == chave }?.paraPc()

    fun garantirContato(dispositivo: DispositivoSalvoV13, grupo: String = "Casa"): ContatoV13 {
        val existente = _contatos.value.firstOrNull { it.dispositivoChave == dispositivo.chave }
        if (existente != null) return existente
        val novo = ContatoV13(
            id = UUID.randomUUID().toString(),
            nome = dispositivo.nome,
            dispositivoChave = dispositivo.chave,
            grupo = grupo,
            favorito = dispositivo.favorito,
            nomeGerenciadoAutomaticamente = true
        )
        atualizarContatos((_contatos.value + novo).sortedBy { it.nome.lowercase() })
        return novo
    }

    fun criarContato(nome: String, dispositivoChave: String?, grupo: String) {
        val limpo = nome.trim()
        if (limpo.isBlank()) return
        val novo = ContatoV13(
            id = UUID.randomUUID().toString(),
            nome = limpo,
            dispositivoChave = dispositivoChave,
            grupo = grupo.ifBlank { "Equipe" },
            favorito = false,
            nomeGerenciadoAutomaticamente = false
        )
        atualizarContatos((_contatos.value + novo).sortedBy { it.nome.lowercase() })
    }

    fun alternarFavoritoContato(id: String) {
        atualizarContatos(_contatos.value.map { if (it.id == id) it.copy(favorito = !it.favorito) else it })
    }

    fun removerContato(id: String) = atualizarContatos(_contatos.value.filterNot { it.id == id })

    fun registrarConvite(uriTexto: String, remetente: String = "Compartilhamento") {
        val pc = lerDeepLink(uriTexto) ?: return
        val novo = ConviteV13(
            id = UUID.randomUUID().toString(),
            remetente = remetente,
            recebidoEm = System.currentTimeMillis(),
            link = uriTexto,
            nomeDispositivo = pc.nome,
            maquinaId = pc.maquinaId
        )
        atualizarConvites((_convites.value + novo).distinctBy { it.link }.sortedByDescending { it.recebidoEm })
    }

    fun aceitarConvite(id: String): PcEncontrado? {
        val convite = _convites.value.firstOrNull { it.id == id } ?: return null
        val pc = lerDeepLink(convite.link) ?: return null
        absorverDescoberta(listOf(pc))
        garantirContato(_dispositivos.value.first { it.chave == chave(pc) }, "Compartilhados")
        atualizarConvites(_convites.value.filterNot { it.id == id })
        return pc
    }

    fun rejeitarConvite(id: String) = atualizarConvites(_convites.value.filterNot { it.id == id })

    fun compartilharDispositivo(context: Context, dispositivo: DispositivoSalvoV13) {
        val uri = deepLink(dispositivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Convite PCFlow")
            putExtra(Intent.EXTRA_TEXT, "Conecte-se ao meu computador pelo PCFlow:\n$uri")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar acesso PCFlow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun deepLink(dispositivo: DispositivoSalvoV13): String = Uri.Builder()
        .scheme("pcflow")
        .authority("connect")
        .appendQueryParameter("host", dispositivo.host)
        .appendQueryParameter("port", dispositivo.porta.toString())
        .appendQueryParameter("id", dispositivo.maquinaId)
        .appendQueryParameter("tls", dispositivo.tls)
        .build().toString()

    fun lerDeepLink(texto: String): PcEncontrado? {
        return try {
            val uri = Uri.parse(texto)
            if (!uri.scheme.equals("pcflow", true)) return null
            val host = uri.getQueryParameter("host") ?: return null
            val porta = uri.getQueryParameter("port")?.toIntOrNull() ?: 45456
            PcEncontrado(
                nome = uri.getQueryParameter("nome") ?: "PCFlow compartilhado",
                host = host,
                porta = porta,
                portaTela = uri.getQueryParameter("portaTela")?.toIntOrNull() ?: 45457,
                portaArquivos = uri.getQueryParameter("portaArquivos")?.toIntOrNull() ?: 45458,
                maquinaId = uri.getQueryParameter("id").orEmpty(),
                tls = uri.getQueryParameter("tls").orEmpty(),
                monitores = uri.getQueryParameter("monitores")?.toIntOrNull() ?: 1
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun carregar() {
        val p = prefs()
        _dispositivos.value = lerArray(p.getString(CHAVE_DISPOSITIVOS, null)) { DispositivoSalvoV13.deJson(it) }
        _recentes.value = lerArray(p.getString(CHAVE_RECENTES, null)) { RegistroConexaoV13.deJson(it) }
        _contatos.value = lerArray(p.getString(CHAVE_CONTATOS, null)) { ContatoV13.deJson(it) }
        _convites.value = lerArray(p.getString(CHAVE_CONVITES, null)) { ConviteV13.deJson(it) }
    }

    private fun atualizarDispositivos(valor: List<DispositivoSalvoV13>) {
        _dispositivos.value = valor
        salvarArray(CHAVE_DISPOSITIVOS, valor.map { it.json() })
    }

    private fun atualizarRecentes(valor: List<RegistroConexaoV13>) {
        _recentes.value = valor
        salvarArray(CHAVE_RECENTES, valor.map { it.json() })
    }

    private fun atualizarContatos(valor: List<ContatoV13>) {
        _contatos.value = valor
        salvarArray(CHAVE_CONTATOS, valor.map { it.json() })
    }

    private fun atualizarConvites(valor: List<ConviteV13>) {
        _convites.value = valor
        salvarArray(CHAVE_CONVITES, valor.map { it.json() })
    }

    private fun salvarArray(chave: String, objetos: List<JSONObject>) {
        val array = JSONArray()
        objetos.forEach(array::put)
        prefs().edit().putString(chave, array.toString()).apply()
    }

    private fun <T> lerArray(texto: String?, conversor: (JSONObject) -> T?): List<T> {
        if (texto.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(texto)
            buildList {
                for (i in 0 until array.length()) conversor(array.getJSONObject(i))?.let(::add)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun prefs() = requireNotNull(contexto).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun chave(pc: PcEncontrado) = pc.maquinaId.ifBlank { "${pc.host}:${pc.porta}" }
}

data class DispositivoSalvoV13(
    val chave: String,
    val nome: String,
    val host: String,
    val porta: Int,
    val portaTela: Int,
    val portaArquivos: Int,
    val maquinaId: String,
    val tls: String,
    val monitores: Int,
    val favorito: Boolean = false,
    val ultimoVisto: Long = 0,
    val sistema: String = "Windows"
) {
    fun paraPc() = PcEncontrado(nome, host, porta, portaTela, portaArquivos, maquinaId, tls, monitores)
    fun json() = JSONObject()
        .put("chave", chave).put("nome", nome).put("host", host).put("porta", porta)
        .put("portaTela", portaTela).put("portaArquivos", portaArquivos).put("maquinaId", maquinaId)
        .put("tls", tls).put("monitores", monitores).put("favorito", favorito)
        .put("ultimoVisto", ultimoVisto).put("sistema", sistema)

    companion object {
        fun deJson(j: JSONObject) = DispositivoSalvoV13(
            chave = j.optString("chave"), nome = j.optString("nome", "PCFlow"), host = j.optString("host"),
            porta = j.optInt("porta", 45456), portaTela = j.optInt("portaTela", 45457),
            portaArquivos = j.optInt("portaArquivos", 45458), maquinaId = j.optString("maquinaId"),
            tls = j.optString("tls"), monitores = j.optInt("monitores", 1).coerceAtLeast(1),
            favorito = j.optBoolean("favorito", false), ultimoVisto = j.optLong("ultimoVisto", 0),
            sistema = j.optString("sistema", "Windows")
        ).takeIf { it.host.isNotBlank() && it.chave.isNotBlank() }
    }
}

data class RegistroConexaoV13(
    val id: String,
    val dispositivoChave: String,
    val nome: String,
    val quando: Long,
    val sucesso: Boolean
) {
    fun json() = JSONObject().put("id", id).put("dispositivoChave", dispositivoChave).put("nome", nome).put("quando", quando).put("sucesso", sucesso)
    companion object {
        fun deJson(j: JSONObject) = RegistroConexaoV13(j.optString("id"), j.optString("dispositivoChave"), j.optString("nome"), j.optLong("quando"), j.optBoolean("sucesso", true))
            .takeIf { it.id.isNotBlank() && it.dispositivoChave.isNotBlank() }
    }
}

data class ContatoV13(
    val id: String,
    val nome: String,
    val dispositivoChave: String?,
    val grupo: String,
    val favorito: Boolean,
    val nomeGerenciadoAutomaticamente: Boolean
) {
    fun json() = JSONObject().put("id", id).put("nome", nome).put("dispositivoChave", dispositivoChave).put("grupo", grupo).put("favorito", favorito).put("auto", nomeGerenciadoAutomaticamente)
    companion object {
        fun deJson(j: JSONObject) = ContatoV13(
            j.optString("id"), j.optString("nome"), j.optString("dispositivoChave").takeIf { it.isNotBlank() },
            j.optString("grupo", "Equipe"), j.optBoolean("favorito", false), j.optBoolean("auto", false)
        ).takeIf { it.id.isNotBlank() && it.nome.isNotBlank() }
    }
}

data class ConviteV13(
    val id: String,
    val remetente: String,
    val recebidoEm: Long,
    val link: String,
    val nomeDispositivo: String,
    val maquinaId: String
) {
    fun json() = JSONObject().put("id", id).put("remetente", remetente).put("recebidoEm", recebidoEm).put("link", link).put("nomeDispositivo", nomeDispositivo).put("maquinaId", maquinaId)
    companion object {
        fun deJson(j: JSONObject) = ConviteV13(j.optString("id"), j.optString("remetente"), j.optLong("recebidoEm"), j.optString("link"), j.optString("nomeDispositivo"), j.optString("maquinaId"))
            .takeIf { it.id.isNotBlank() && it.link.isNotBlank() }
    }
}
