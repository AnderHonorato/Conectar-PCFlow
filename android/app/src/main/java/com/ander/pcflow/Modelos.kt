package com.ander.pcflow

data class PcEncontrado(
    val nome: String,
    val host: String,
    val porta: Int = 45456,
    val portaTela: Int = 45457,
    val portaArquivos: Int = 45458,
    val maquinaId: String = "",
    val tls: String = "",
    val monitores: Int = 1,
    /**
     * Quando preenchido, a conexão não vai direto ao PC: passa por um servidor
     * de retransmissão do PCFlow. Formato "host" ou "host:porta".
     */
    val servidorRelay: String = "",
    /** Código do PC dentro do servidor de retransmissão. */
    val codigoRelay: String = "",
    /** Este destino veio de um código de acesso, e não da busca na rede. */
    val porCodigo: Boolean = false
) {
    val viaRelay: Boolean get() = servidorRelay.isNotBlank() && codigoRelay.isNotBlank()
}

enum class EstadoConexao { DESCONECTADO, CONECTANDO, CONECTADO, ERRO }

data class PermissoesRemotas(
    val tela: Boolean = true,
    val entrada: Boolean = true,
    val clipboard: Boolean = true,
    val energia: Boolean = true,
    val arquivos: Boolean = false
)

data class EstadoSessao(
    val estado: EstadoConexao = EstadoConexao.DESCONECTADO,
    val pc: PcEncontrado? = null,
    val mensagem: String = "",
    val sessaoId: String? = null,
    val quantidadeMonitores: Int = 1,
    val permissoes: PermissoesRemotas = PermissoesRemotas()
)

data class ArquivoRemoto(
    val nome: String,
    val caminho: String,
    val pasta: Boolean,
    val tamanho: Long = 0,
    val modificado: String = "",
    val raiz: Boolean = false
)

data class EstadoArquivos(
    val carregando: Boolean = false,
    val caminho: String = "",
    val pai: String = "",
    val itens: List<ArquivoRemoto> = emptyList(),
    val mensagem: String = ""
)
