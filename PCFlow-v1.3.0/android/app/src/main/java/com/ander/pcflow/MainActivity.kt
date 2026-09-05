package com.ander.pcflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessaoPcFlow.inicializar(this)
        setContent { PcFlowV13App() }
    }
}
