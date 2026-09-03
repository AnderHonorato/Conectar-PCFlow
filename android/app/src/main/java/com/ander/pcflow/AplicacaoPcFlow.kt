package com.ander.pcflow

import android.app.Application
import android.util.Log
import com.ander.pcflow.rede.SessaoPcFlow

/**
 * Ponto de entrada do processo.
 *
 * Também instala um tratador global: antes, qualquer exceção numa corrotina de
 * rede fechava o app em silêncio. Agora o erro é registrado no logcat e a
 * sessão é encerrada de forma controlada, sem "sumir" da tela do usuário.
 */
class AplicacaoPcFlow : Application() {

    override fun onCreate() {
        super.onCreate()
        SessaoPcFlow.inicializar(this)

        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            Log.e("PCFlow", "Falha não tratada em ${thread.name}", erro)
            runCatching { SessaoPcFlow.desconectar() }
            anterior?.uncaughtException(thread, erro)
        }
    }
}
