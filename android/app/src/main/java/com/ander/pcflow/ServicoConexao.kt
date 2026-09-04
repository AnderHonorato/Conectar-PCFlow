package com.ander.pcflow

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class ServicoConexao : Service() {
    override fun onCreate() {
        super.onCreate()
        criarCanal()
        startForeground(17, notificacao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificacao(): Notification {
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CANAL)
            .setContentTitle("PCFlow conectado")
            .setContentText("O controle remoto continua ativo na rede local")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(abrir)
            .setOngoing(true)
            .build()
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CANAL, "Conexão PCFlow", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CANAL = "pcflow_conexao"
        fun iniciar(context: Context) = context.startForegroundService(Intent(context, ServicoConexao::class.java))
        fun parar(context: Context) = context.stopService(Intent(context, ServicoConexao::class.java))
    }
}
