package com.ander.pcflow

data class PcEncontrado(
    val nome: String,
    val host: String,
    val porta: Int = 45456
)

enum class EstadoConexao { DESCONECTADO, CONECTANDO, CONECTADO, ERRO }

data class EstadoSessao(
    val estado: EstadoConexao = EstadoConexao.DESCONECTADO,
    val pc: PcEncontrado? = null,
    val mensagem: String = ""
)
