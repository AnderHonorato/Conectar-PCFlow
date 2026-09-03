package com.ander.pcflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Fundo = Color(0xFF0E1218)
val Painel = Color(0xFF171C23)
val PainelClaro = Color(0xFF1F252E)
val Borda = Color(0xFF2E353F)
val Dourado = Color(0xFFF2AA2E)
val DouradoFraco = Color(0xFF6B5223)
val Turquesa = Color(0xFF16D3C6)
val Vermelho = Color(0xFFFF8A80)
val TextoPrimario = Color(0xFFF1F4F7)
val TextoSecundario = Color(0xFF98A1AC)

@Composable
fun TemaPcFlow(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Dourado,
            onPrimary = Color(0xFF0E1218),
            secondary = Turquesa,
            background = Fundo,
            onBackground = TextoPrimario,
            surface = Painel,
            onSurface = TextoPrimario,
            surfaceVariant = PainelClaro,
            onSurfaceVariant = TextoSecundario,
            outline = Borda,
            error = Vermelho
        ),
        typography = Typography(),
        content = conteudo
    )
}

@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    cor: Color = Painel,
    corBorda: Color = Borda,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(cor, RoundedCornerShape(18.dp))
            .border(1.dp, corBorda, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = conteudo
    )
}
