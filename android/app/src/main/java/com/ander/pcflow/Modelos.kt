package com.ander.pcflow

data class PcEncontrado(
    val nome: String,
    val host: String,
    val porta: Int = 45456,
    val portaTela: Int = 45457,
    val maquinaId: String = "",
    val tls: String = "",
    val monitores: Int = 1
)

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
