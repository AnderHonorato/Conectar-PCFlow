package com.ander.pcflow.ui

import android.content.Context

/** Preferências locais do app (nada sai do aparelho). */
class Preferencias(context: Context) {
    private val p = context.applicationContext
        .getSharedPreferences("pcflow_ui", Context.MODE_PRIVATE)

    var sensibilidade: Float
        get() = p.getFloat("sensibilidade", 1.6f)
        set(v) = p.edit().putFloat("sensibilidade", v).apply()

    var velocidadeScroll: Float
        get() = p.getFloat("scroll", 1.0f)
        set(v) = p.edit().putFloat("scroll", v).apply()

    var inverterScroll: Boolean
        get() = p.getBoolean("inverter_scroll", false)
        set(v) = p.edit().putBoolean("inverter_scroll", v).apply()

    var vibrar: Boolean
        get() = p.getBoolean("vibrar", true)
        set(v) = p.edit().putBoolean("vibrar", v).apply()

    var aceleracao: Boolean
        get() = p.getBoolean("aceleracao", true)
        set(v) = p.edit().putBoolean("aceleracao", v).apply()

    var qualidadeTela: Int
        get() = p.getInt("tela_qualidade", 55)
        set(v) = p.edit().putInt("tela_qualidade", v).apply()

    var larguraTela: Int
        get() = p.getInt("tela_largura", 1280)
        set(v) = p.edit().putInt("tela_largura", v).apply()

    var fpsTela: Int
        get() = p.getInt("tela_fps", 15)
        set(v) = p.edit().putInt("tela_fps", v).apply()

    var conectarAoAbrir: Boolean
        get() = p.getBoolean("conectar_ao_abrir", true)
        set(v) = p.edit().putBoolean("conectar_ao_abrir", v).apply()

    fun ajustesTouchpad() = AjustesTouchpad(
        sensibilidade = sensibilidade,
        velocidadeScroll = velocidadeScroll,
        inverterScroll = inverterScroll,
        vibrar = vibrar,
        aceleracao = aceleracao
    )
}
