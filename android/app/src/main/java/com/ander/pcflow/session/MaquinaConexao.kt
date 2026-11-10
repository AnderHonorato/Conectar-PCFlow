package com.ander.pcflow.session

enum class Fase { Ociosa, Descobrindo, Selecionado, Conectando, Autenticando, Negociando, Pronto, Reconectando, Erro }
enum class TipoErro { Nenhum, Permissao, Rede, Auth, Versao, NaoSuportado }
enum class EventoConexao {
    IniciarDescoberta, Selecionar, IniciarConexao, SocketAberto, AuthOk, AuthFalhou,
    CapabilitiesOk, HeartbeatPerdido, SocketCaiu, PermissaoNegada, VersaoRejeitada, AuthRevogada, Reset
}

data class FotoConexao(val fase: Fase, val erro: TipoErro = TipoErro.Nenhum) {
    val pronta get() = fase == Fase.Pronto
    val aceitaDestrutivo get() = fase == Fase.Pronto
}

class MaquinaConexao {
    var foto = FotoConexao(Fase.Ociosa)
        private set

    fun ao(evento: EventoConexao): FotoConexao {
        val fase = foto.fase
        foto = when (fase to evento) {
            Fase.Ociosa to EventoConexao.IniciarDescoberta -> ok(Fase.Descobrindo)
            Fase.Descobrindo to EventoConexao.Selecionar -> ok(Fase.Selecionado)
            Fase.Descobrindo to EventoConexao.PermissaoNegada -> falha(TipoErro.Permissao)
            Fase.Selecionado to EventoConexao.IniciarConexao -> ok(Fase.Conectando)
            Fase.Conectando to EventoConexao.SocketAberto -> ok(Fase.Autenticando)
            Fase.Conectando to EventoConexao.SocketCaiu -> falha(TipoErro.Rede)
            Fase.Conectando to EventoConexao.PermissaoNegada -> falha(TipoErro.Permissao)
            Fase.Autenticando to EventoConexao.AuthOk -> ok(Fase.Negociando)
            Fase.Autenticando to EventoConexao.AuthFalhou -> falha(TipoErro.Auth)
            Fase.Autenticando to EventoConexao.AuthRevogada -> falha(TipoErro.Auth)
            Fase.Autenticando to EventoConexao.VersaoRejeitada -> falha(TipoErro.Versao)
            Fase.Autenticando to EventoConexao.SocketCaiu -> falha(TipoErro.Rede)
            Fase.Negociando to EventoConexao.CapabilitiesOk -> ok(Fase.Pronto)
            Fase.Negociando to EventoConexao.VersaoRejeitada -> falha(TipoErro.Versao)
            Fase.Negociando to EventoConexao.SocketCaiu -> falha(TipoErro.Rede)
            Fase.Pronto to EventoConexao.HeartbeatPerdido -> ok(Fase.Reconectando)
            Fase.Pronto to EventoConexao.SocketCaiu -> ok(Fase.Reconectando)
            Fase.Pronto to EventoConexao.AuthRevogada -> falha(TipoErro.Auth)
            Fase.Reconectando to EventoConexao.SocketAberto -> ok(Fase.Autenticando)
            Fase.Reconectando to EventoConexao.AuthRevogada -> falha(TipoErro.Auth)
            Fase.Reconectando to EventoConexao.Reset -> FotoConexao(Fase.Ociosa)
            Fase.Erro to EventoConexao.Reset -> FotoConexao(Fase.Ociosa)
            Fase.Erro to EventoConexao.IniciarDescoberta -> ok(Fase.Descobrindo)
            else -> foto
        }
        return foto
    }

    private fun ok(fase: Fase) = FotoConexao(fase)
    private fun falha(erro: TipoErro) = FotoConexao(Fase.Erro, erro)
}

class MonitorHeartbeat(private val intervaloMs: Long, private val limite: Int) {
    private var visto = 0L
    private var faltas = 0
    private var iniciado = false

    fun visto(agora: Long) {
        visto = agora
        faltas = 0
        iniciado = true
    }

    fun observar(agora: Long): Boolean {
        if (!iniciado) visto(agora)
        while (agora - visto >= intervaloMs) {
            faltas++
            visto += intervaloMs
            if (faltas >= limite) return true
        }
        return false
    }
}

class FilaSaida {
    var movesAgregados = 0
        private set
    private val discretos = ArrayDeque<String>()
    private var movePendente: String? = null

    fun enfileirarMove(json: String) {
        if (movePendente != null) movesAgregados++
        movePendente = json
    }

    fun enfileirarDiscreto(json: String) {
        discretos.addLast(json)
    }

    fun descartarMovesAtrasados() {
        movePendente = null
    }

    fun drenar(): List<String> {
        val saida = mutableListOf<String>()
        movePendente?.let { saida += it }
        movePendente = null
        while (discretos.isNotEmpty()) saida += discretos.removeFirst()
        return saida
    }
}

class SegurancaEntrada {
    private val presos = linkedSetOf<String>()
    fun down(tipo: String, codigo: String) { presos += "$tipo:$codigo" }
    fun up(tipo: String, codigo: String) { presos -= "$tipo:$codigo" }
    fun soltarTudo(): List<Pair<String, String>> {
        val itens = presos.map { val p = it.split(':', limit = 2); p[0] to p.getOrElse(1) { "" } }
        presos.clear()
        return itens
    }
    fun vazio() = presos.isEmpty()
}

class PoliticaReconexao {
    var tentativa = 0
        private set
    fun proximoAtrasoMs(): Long {
        tentativa++
        val shift = minOf(tentativa, 4)
        return minOf(10_000L, 750L * (1L shl shift))
    }
    fun reset() { tentativa = 0 }
}

interface ChaveValor {
    fun get(chave: String): String?
    fun put(chave: String, valor: String)
}

class DiretorioToken(private val store: ChaveValor) {
    fun salvar(deviceId: String, token: String) = store.put("token_$deviceId", token)
    fun ler(deviceId: String): String? = store.get("token_$deviceId")
}
