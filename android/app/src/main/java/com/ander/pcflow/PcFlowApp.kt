package com.ander.pcflow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.ander.pcflow.design.CoresClaroPcFlow
import com.ander.pcflow.design.CoresEscuroPcFlow
import com.ander.pcflow.design.TemaPcFlow
import com.ander.pcflow.telas.CascaPcFlow
import com.ander.pcflow.telas.TemaApp
import com.ander.pcflow.telas.rememberEstadoCasca

/**
 * Entrada do aplicativo.
 *
 * O tema é resolvido aqui, fora da casca, porque a escolha do usuário fica
 * guardada no estado da casca e precisa valer para tudo — inclusive para os
 * diálogos, que são desenhados pelo PCFlow e não pelo sistema.
 */
@Composable
fun PcFlowApp() {
    val casca = rememberEstadoCasca()
    val escuroDoSistema = isSystemInDarkTheme()
    val cores = when (casca.ajustes.tema) {
        TemaApp.ESCURO -> CoresEscuroPcFlow
        TemaApp.CLARO -> CoresClaroPcFlow
        TemaApp.SISTEMA -> if (escuroDoSistema) CoresEscuroPcFlow else CoresClaroPcFlow
    }
    TemaPcFlow(cores = cores) { CascaPcFlow() }
}
