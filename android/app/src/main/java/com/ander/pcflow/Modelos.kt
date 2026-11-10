package com.ander.pcflow

import com.ander.pcflow.session.Fase

data class PcEncontrado(
    val nome: String,
    val host: String,
    val porta: Int = 45456,
    val deviceId: String = ""
)

data class EstadoSessao(
    val fase: Fase = Fase.Ociosa,
    val pc: PcEncontrado? = null,
    val mensagem: String = "",
    val codigoErro: String = ""
)
