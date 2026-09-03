package com.ander.pcflow.rede

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Descoberta de PCs por broadcast UDP.
 *
 * A versão anterior só mandava para 255.255.255.255 e não segurava o MulticastLock.
 * Em boa parte dos roteadores domésticos o broadcast limitado é descartado e, sem o
 * lock, o Wi-Fi do Android nem entrega os pacotes recebidos ao app — era a causa de
 * "o celular não acha o PC". Agora:
 *  - envia para 255.255.255.255 **e** para o broadcast de cada interface (ex. 192.168.0.255);
 *  - segura MulticastLock e WifiLock enquanto procura;
 *  - repete a sonda algumas vezes, porque UDP pode ser perdido.
 */
object Descoberta {

    suspend fun procurar(
        context: Context,
        duracaoMs: Long = 2_500,
        aoEncontrar: (PcEncontrado) -> Unit
    ): List<PcEncontrado> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicast = wifi?.createMulticastLock("pcflow-descoberta")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val encontrados = LinkedHashMap<String, PcEncontrado>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300

                val sonda = Protocolo.SONDA.toByteArray()
                val alvos = enderecosBroadcast()
                val fim = System.currentTimeMillis() + duracaoMs
                var proximoEnvio = 0L

                while (System.currentTimeMillis() < fim) {
                    val agora = System.currentTimeMillis()
                    if (agora >= proximoEnvio) {
                        for (alvo in alvos) {
                            runCatching {
                                socket.send(
                                    DatagramPacket(
                                        sonda, sonda.size, alvo, Protocolo.PORTA_DESCOBERTA
                                    )
                                )
                            }
                        }
                        proximoEnvio = agora + 700
                    }

                    val buffer = ByteArray(2048)
                    val pacote = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(pacote)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    val host = pacote.address?.hostAddress ?: continue
                    val json = runCatching {
                        JSONObject(String(pacote.data, 0, pacote.length, Charsets.UTF_8))
                    }.getOrNull() ?: continue
                    if (json.optString("tipo") != "anuncio") continue

                    val pc = PcEncontrado(
                        nome = json.optString("nome").ifBlank { host },
                        host = host,
                        porta = json.optInt("porta", Protocolo.PORTA_CONTROLE),
                        versao = json.optString("versao")
                    )
                    if (encontrados.put(host, pc) == null) aoEncontrar(pc)
                }
            }
        } finally {
            runCatching { multicast?.release() }
        }
        encontrados.values.toList()
    }

    /** 255.255.255.255 mais o broadcast calculado de cada interface IPv4 ativa. */
    private fun enderecosBroadcast(): List<InetAddress> {
        val alvos = linkedSetOf<InetAddress>()
        runCatching { alvos.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (endereco in ni.interfaceAddresses) {
                    val broadcast = endereco.broadcast ?: continue
                    if (endereco.address is Inet4Address) alvos.add(broadcast)
                }
            }
        }
        return alvos.toList()
    }
}
