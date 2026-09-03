package com.ander.pcflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Mantém a sessão viva com a tela desligada.
 *
 * A versão anterior chamava startForeground sem tipo no Android 14+ e sem
 * proteção contra ForegroundServiceStartNotAllowedException — o app fechava
 * sozinho ao conectar em segundo plano. Agora tudo é tolerante a falha: se o
 * sistema recusar o serviço, a conexão continua, apenas sem garantia em background.
 */
class ServicoConexao : Service() {

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        subirEmPrimeiroPlano(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        criarCanal()
        subirEmPrimeiroPlano(intent?.getStringExtra(EXTRA_PC))
        if (intent?.action == ACAO_DESCONECTAR) {
            com.ander.pcflow.rede.SessaoPcFlow.desconectar()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun subirEmPrimeiroPlano(nomePc: String?) {
        runCatching {
            val notificacao = montarNotificacao(nomePc)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    ID_NOTIFICACAO, notificacao,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(ID_NOTIFICACAO, notificacao)
            }
        }
    }

    private fun montarNotificacao(nomePc: String?): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val desconectar = PendingIntent.getService(
            this, 1,
            Intent(this, ServicoConexao::class.java).setAction(ACAO_DESCONECTAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val construtor = Notification.Builder(this, CANAL)
            .setContentTitle(
                if (nomePc.isNullOrBlank()) "PCFlow conectado" else "Conectado a $nomePc"
            )
            .setContentText("Controle remoto ativo na rede local")
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Desconectar", desconectar).build()
            )
        return construtor.build()
    }

    private fun criarCanal() {
        val gerenciador = getSystemService(NotificationManager::class.java) ?: return
        if (gerenciador.getNotificationChannel(CANAL) != null) return
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL, "Conexão PCFlow", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantém o controle remoto ativo enquanto o app está em segundo plano."
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CANAL = "pcflow_conexao"
        private const val ID_NOTIFICACAO = 17
        private const val EXTRA_PC = "pc"
        const val ACAO_DESCONECTAR = "com.ander.pcflow.DESCONECTAR"

        fun iniciar(context: Context, nomePc: String) {
            val intent = Intent(context, ServicoConexao::class.java).putExtra(EXTRA_PC, nomePc)
            // Em Android 12+ isto pode ser recusado se o app estiver em background:
            // a conexão continua funcionando, então a falha é apenas registrada.
            runCatching { context.startForegroundService(intent) }
        }

        fun parar(context: Context) {
            runCatching { context.stopService(Intent(context, ServicoConexao::class.java)) }
        }
    }
}
