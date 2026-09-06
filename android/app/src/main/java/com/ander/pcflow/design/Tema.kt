package com.ander.pcflow.design

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Duração de mudança de estado de controle (pressão, foco, seleção). */
const val DURACAO_CONTROLE_MS = 150

/** Duração de abrir/fechar painel, diálogo e faixa de aviso. */
const val DURACAO_PAINEL_MS = 200

/**
 * Ligado quando o sistema está com animações desligadas. As telas leem daqui
 * em vez de perguntar ao Android de novo em cada componente.
 */
val LocalAnimacoesReduzidasPcFlow = staticCompositionLocalOf { false }

/**
 * `ANIMATOR_DURATION_SCALE == 0` é como o Android diz "essa pessoa não quer
 * animação". Ler pode falhar em aparelho restrito, e aí seguimos com animação.
 */
fun animacoesReduzidas(contexto: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        contexto.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

/** Duração já corrigida pela preferência do sistema: 0 vira troca instantânea. */
@Composable
@ReadOnlyComposable
fun duracaoPcFlow(milissegundos: Int): Int =
    if (LocalAnimacoesReduzidasPcFlow.current) 0 else milissegundos

@Composable
@ReadOnlyComposable
fun <T> especPcFlow(milissegundos: Int = DURACAO_CONTROLE_MS): TweenSpec<T> =
    tween(durationMillis = duracaoPcFlow(milissegundos), easing = LinearOutSlowInEasing)

/** Escala tipográfica do contrato: 28 / 20 / 15 / 13 / 11. Peso forte só em título. */
val TipografiaPcFlow = Typography().let { padrao ->
    padrao.copy(
        displaySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 21.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 17.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    )
}

/** Número de identidade (ID, código): monoespaçado e com folga entre dígitos. */
val EstiloIdentidade = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = 2.sp
)

val EstiloIdentidadeMenor = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 1.sp
)

private fun esquemaDe(cores: CoresPcFlow) = darkColorScheme(
    primary = cores.acao,
    onPrimary = cores.sobreAcao,
    primaryContainer = cores.superficieElevada,
    onPrimaryContainer = cores.texto,
    secondary = cores.seguro,
    onSecondary = cores.fundo,
    background = cores.fundo,
    onBackground = cores.texto,
    surface = cores.superficie,
    onSurface = cores.texto,
    surfaceVariant = cores.superficieElevada,
    onSurfaceVariant = cores.textoSecundario,
    outline = cores.borda,
    outlineVariant = cores.bordaFoco,
    error = cores.erro,
    onError = cores.fundo,
    scrim = androidx.compose.ui.graphics.Color.Black
)

@Composable
fun TemaPcFlow(
    cores: CoresPcFlow = CoresEscuroPcFlow,
    conteudo: @Composable () -> Unit
) {
    val contexto = LocalContext.current
    val reduzidas = remember(contexto) { animacoesReduzidas(contexto) }
    CompositionLocalProvider(
        LocalCoresPcFlow provides cores,
        LocalFormasPcFlow provides FormasPadraoPcFlow,
        LocalAnimacoesReduzidasPcFlow provides reduzidas,
        LocalContentColor provides cores.texto
    ) {
        MaterialTheme(
            colorScheme = esquemaDe(cores),
            typography = TipografiaPcFlow,
            shapes = Shapes(
                extraSmall = FormasPadraoPcFlow.controle,
                small = FormasPadraoPcFlow.controle,
                medium = FormasPadraoPcFlow.cartao,
                large = FormasPadraoPcFlow.painel,
                extraLarge = FormasPadraoPcFlow.painel
            ),
            content = conteudo
        )
    }
}

/** Atalho curto para os tokens dentro de um Composable. */
object PcFlow {
    val cores: CoresPcFlow
        @Composable @ReadOnlyComposable get() = LocalCoresPcFlow.current
    val formas: FormasPcFlow
        @Composable @ReadOnlyComposable get() = LocalFormasPcFlow.current
}
