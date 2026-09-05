package com.ander.pcflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessaoPcFlow.inicializar(this)
        RepositorioPcFlowV13.inicializar(this)
        processarIntent(intent)
        setContent { PcFlowEntradaFinalV13() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processarIntent(intent)
    }

    private fun processarIntent(intent: Intent?) {
        val uri = intent?.data?.toString() ?: return
        if (uri.startsWith("pcflow://", ignoreCase = true)) {
            RepositorioPcFlowV13.registrarConvite(uri, intent.getStringExtra(Intent.EXTRA_TITLE) ?: "Convite PCFlow")
        }
    }
}
