package com.ander.pcflow.rede

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Informações da rede do próprio celular.
 *
 * PCFlow só funciona com PC e celular na MESMA rede local, então o app mostra
 * o endereço do aparelho e avisa quando o PC encontrado está em outra faixa —
 * é o erro mais comum (celular no 5 GHz de convidados, PC no cabo, VPN ligada).
 */
object RedeLocal {

    /** IPv4 do celular na rede atual, ou null se não houver rede. */
    fun enderecoDoCelular(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** Prefixo /24 de um IPv4: "192.168.0.23" -> "192.168.0". */
    fun prefixo(ip: String?): String? {
        val partes = ip?.split(".") ?: return null
        if (partes.size != 4) return null
        return partes.take(3).joinToString(".")
    }

    /** true quando celular e PC aparentam estar na mesma sub-rede /24. */
    fun mesmaRede(ipCelular: String?, ipPc: String?): Boolean {
        val a = prefixo(ipCelular) ?: return true   // sem informação, não alarma
        val b = prefixo(ipPc) ?: return true
        return a == b
    }
}
