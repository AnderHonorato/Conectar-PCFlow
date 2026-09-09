package com.ander.pcflow.telas

import android.content.Context

/**
 * As preferências do aplicativo. Mesmo arquivo que o transporte já usa, com
 * chaves próprias prefixadas por `casca_` — o que a casca guarda é dela.
 */
class ArmazemPreferencias(contexto: Context) : Armazem {
    private val preferencias =
        contexto.applicationContext.getSharedPreferences("pcflow", Context.MODE_PRIVATE)

    override fun ler(chave: String, padrao: String): String =
        preferencias.getString(chave, padrao) ?: padrao

    override fun escrever(chave: String, valor: String) {
        preferencias.edit().putString(chave, valor).apply()
    }
}
