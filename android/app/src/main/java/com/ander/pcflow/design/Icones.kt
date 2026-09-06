package com.ander.pcflow.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícones desenhados pelo próprio PCFlow. São de traço, com ponta arredondada,
 * para casar com os cantos do sistema visual — e para não depender de um pacote
 * de ícones que traria outra linguagem gráfica junto.
 */
enum class Icone {
    COMPUTADOR, CELULAR, PONTEIRO, TECLADO, PASTA, ARQUIVO, PRANCHETA,
    AJUSTES, BUSCAR, QR, TECLADO_NUMERICO, REDE, ESTRELA, ESTRELA_CHEIA,
    RELOGIO, CADEADO, ENERGIA, ALERTA, CHECK, FECHAR, SETA_DIREITA,
    SETA_ESQUERDA, ATUALIZAR, BAIXAR, ENVIAR, LIXEIRA, MAIS, MIDIA,
    APRESENTACAO, NAVEGADOR, OLHO, OLHO_FECHADO, EXPANDIR, SAIR, INFO
}

/**
 * @param descricao texto lido em voz alta. Deixe nulo só quando o ícone repete
 * uma informação que já está escrita ao lado — aí ele é decoração.
 */
@Composable
fun IconePcFlow(
    icone: Icone,
    cor: Color,
    modifier: Modifier = Modifier,
    tamanho: Dp = 24.dp,
    descricao: String? = null
) {
    val comSemantica =
        if (descricao != null) modifier.semantics { contentDescription = descricao } else modifier
    Canvas(comSemantica.size(tamanho)) { desenhar(icone, cor) }
}

private fun DrawScope.desenhar(icone: Icone, cor: Color) {
    val w = size.width
    val h = size.height
    val traco = maxOf(1.4f, w * 0.075f)
    val estilo = Stroke(width = traco, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun linha(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(cor, Offset(w * x1, h * y1), Offset(w * x2, h * y2), traco, StrokeCap.Round)

    fun caixa(x: Float, y: Float, larg: Float, alt: Float, raio: Float = 0.10f) =
        drawRoundRect(
            cor, Offset(w * x, h * y), Size(w * larg, h * alt),
            CornerRadius(w * raio), style = estilo
        )

    fun circulo(cx: Float, cy: Float, r: Float, preenchido: Boolean = false) =
        if (preenchido) drawCircle(cor, w * r, Offset(w * cx, h * cy))
        else drawCircle(cor, w * r, Offset(w * cx, h * cy), style = estilo)

    fun caminho(construir: Path.() -> Unit) =
        drawPath(Path().apply { construir() }, cor, style = estilo)

    when (icone) {
        Icone.COMPUTADOR -> {
            caixa(0.10f, 0.18f, 0.80f, 0.52f, 0.08f)
            linha(0.50f, 0.70f, 0.50f, 0.84f)
            linha(0.30f, 0.84f, 0.70f, 0.84f)
        }
        Icone.CELULAR -> {
            caixa(0.30f, 0.10f, 0.40f, 0.80f, 0.10f)
            linha(0.44f, 0.78f, 0.56f, 0.78f)
        }
        Icone.PONTEIRO -> {
            caminho {
                moveTo(w * 0.28f, h * 0.16f)
                lineTo(w * 0.72f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.54f)
                lineTo(w * 0.60f, h * 0.82f)
                lineTo(w * 0.44f, h * 0.86f)
                lineTo(w * 0.36f, h * 0.58f)
                lineTo(w * 0.20f, h * 0.72f)
                close()
            }
        }
        Icone.TECLADO -> {
            caixa(0.08f, 0.26f, 0.84f, 0.48f, 0.08f)
            for (coluna in 0..3) {
                circulo(0.22f + coluna * 0.19f, 0.42f, 0.030f, preenchido = true)
                circulo(0.22f + coluna * 0.19f, 0.56f, 0.030f, preenchido = true)
            }
            linha(0.32f, 0.66f, 0.68f, 0.66f)
        }
        Icone.PASTA -> {
            caminho {
                moveTo(w * 0.10f, h * 0.78f)
                lineTo(w * 0.10f, h * 0.24f)
                lineTo(w * 0.42f, h * 0.24f)
                lineTo(w * 0.52f, h * 0.36f)
                lineTo(w * 0.90f, h * 0.36f)
                lineTo(w * 0.90f, h * 0.78f)
                close()
            }
        }
        Icone.ARQUIVO -> {
            caminho {
                moveTo(w * 0.24f, h * 0.12f)
                lineTo(w * 0.60f, h * 0.12f)
                lineTo(w * 0.78f, h * 0.32f)
                lineTo(w * 0.78f, h * 0.88f)
                lineTo(w * 0.24f, h * 0.88f)
                close()
            }
            linha(0.60f, 0.12f, 0.60f, 0.32f)
            linha(0.60f, 0.32f, 0.78f, 0.32f)
        }
        Icone.PRANCHETA -> {
            caixa(0.20f, 0.16f, 0.60f, 0.72f, 0.10f)
            caixa(0.36f, 0.08f, 0.28f, 0.16f, 0.14f)
            linha(0.34f, 0.48f, 0.66f, 0.48f)
            linha(0.34f, 0.64f, 0.58f, 0.64f)
        }
        Icone.AJUSTES -> {
            circulo(0.50f, 0.50f, 0.16f)
            for (passo in 0..5) {
                val angulo = Math.toRadians(passo * 60.0)
                val dx = Math.cos(angulo).toFloat()
                val dy = Math.sin(angulo).toFloat()
                drawLine(
                    cor,
                    Offset(w * (0.5f + dx * 0.26f), h * (0.5f + dy * 0.26f)),
                    Offset(w * (0.5f + dx * 0.40f), h * (0.5f + dy * 0.40f)),
                    traco, StrokeCap.Round
                )
            }
        }
        Icone.BUSCAR -> {
            circulo(0.44f, 0.44f, 0.26f)
            linha(0.64f, 0.64f, 0.84f, 0.84f)
        }
        Icone.QR -> {
            caixa(0.10f, 0.10f, 0.30f, 0.30f, 0.06f)
            caixa(0.60f, 0.10f, 0.30f, 0.30f, 0.06f)
            caixa(0.10f, 0.60f, 0.30f, 0.30f, 0.06f)
            linha(0.60f, 0.60f, 0.60f, 0.78f)
            linha(0.78f, 0.60f, 0.90f, 0.60f)
            linha(0.78f, 0.78f, 0.90f, 0.78f)
            linha(0.90f, 0.78f, 0.90f, 0.90f)
        }
        Icone.TECLADO_NUMERICO -> {
            for (linhaIndice in 0..2) for (coluna in 0..2)
                circulo(0.26f + coluna * 0.24f, 0.24f + linhaIndice * 0.24f, 0.055f)
            circulo(0.50f, 0.90f, 0.055f)
        }
        Icone.REDE -> {
            drawArc(cor, 200f, 140f, false, Offset(w * 0.08f, h * 0.20f), Size(w * 0.84f, h * 0.84f), style = estilo)
            drawArc(cor, 205f, 130f, false, Offset(w * 0.24f, h * 0.36f), Size(w * 0.52f, h * 0.52f), style = estilo)
            circulo(0.50f, 0.78f, 0.055f, preenchido = true)
        }
        Icone.ESTRELA, Icone.ESTRELA_CHEIA -> {
            val estrela = Path().apply {
                moveTo(w * 0.50f, h * 0.10f)
                lineTo(w * 0.62f, h * 0.38f)
                lineTo(w * 0.90f, h * 0.40f)
                lineTo(w * 0.68f, h * 0.60f)
                lineTo(w * 0.76f, h * 0.88f)
                lineTo(w * 0.50f, h * 0.72f)
                lineTo(w * 0.24f, h * 0.88f)
                lineTo(w * 0.32f, h * 0.60f)
                lineTo(w * 0.10f, h * 0.40f)
                lineTo(w * 0.38f, h * 0.38f)
                close()
            }
            if (icone == Icone.ESTRELA_CHEIA) drawPath(estrela, cor) else drawPath(estrela, cor, style = estilo)
        }
        Icone.RELOGIO -> {
            circulo(0.50f, 0.50f, 0.38f)
            linha(0.50f, 0.28f, 0.50f, 0.52f)
            linha(0.50f, 0.52f, 0.68f, 0.62f)
        }
        Icone.CADEADO -> {
            caixa(0.22f, 0.44f, 0.56f, 0.44f, 0.10f)
            drawArc(cor, 180f, 180f, false, Offset(w * 0.32f, h * 0.16f), Size(w * 0.36f, h * 0.36f), style = estilo)
        }
        Icone.ENERGIA -> {
            drawArc(cor, -60f, 300f, false, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.68f), style = estilo)
            linha(0.50f, 0.08f, 0.50f, 0.44f)
        }
        Icone.ALERTA -> {
            caminho {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.92f, h * 0.84f)
                lineTo(w * 0.08f, h * 0.84f)
                close()
            }
            linha(0.50f, 0.42f, 0.50f, 0.62f)
            circulo(0.50f, 0.74f, 0.035f, preenchido = true)
        }
        Icone.CHECK -> {
            linha(0.16f, 0.52f, 0.40f, 0.76f)
            linha(0.40f, 0.76f, 0.84f, 0.24f)
        }
        Icone.FECHAR -> {
            linha(0.22f, 0.22f, 0.78f, 0.78f)
            linha(0.78f, 0.22f, 0.22f, 0.78f)
        }
        Icone.SETA_DIREITA -> {
            linha(0.38f, 0.20f, 0.68f, 0.50f)
            linha(0.68f, 0.50f, 0.38f, 0.80f)
        }
        Icone.SETA_ESQUERDA -> {
            linha(0.62f, 0.20f, 0.32f, 0.50f)
            linha(0.32f, 0.50f, 0.62f, 0.80f)
        }
        Icone.ATUALIZAR -> {
            drawArc(cor, 40f, 280f, false, Offset(w * 0.14f, h * 0.14f), Size(w * 0.72f, h * 0.72f), style = estilo)
            caminho {
                moveTo(w * 0.66f, h * 0.10f)
                lineTo(w * 0.90f, h * 0.24f)
                lineTo(w * 0.64f, h * 0.38f)
            }
        }
        Icone.BAIXAR -> {
            linha(0.50f, 0.12f, 0.50f, 0.62f)
            linha(0.28f, 0.42f, 0.50f, 0.64f)
            linha(0.72f, 0.42f, 0.50f, 0.64f)
            linha(0.16f, 0.84f, 0.84f, 0.84f)
        }
        Icone.ENVIAR -> {
            linha(0.50f, 0.64f, 0.50f, 0.14f)
            linha(0.28f, 0.34f, 0.50f, 0.12f)
            linha(0.72f, 0.34f, 0.50f, 0.12f)
            linha(0.16f, 0.84f, 0.84f, 0.84f)
        }
        Icone.LIXEIRA -> {
            linha(0.14f, 0.26f, 0.86f, 0.26f)
            linha(0.38f, 0.26f, 0.42f, 0.14f)
            linha(0.62f, 0.26f, 0.58f, 0.14f)
            caminho {
                moveTo(w * 0.22f, h * 0.30f)
                lineTo(w * 0.28f, h * 0.88f)
                lineTo(w * 0.72f, h * 0.88f)
                lineTo(w * 0.78f, h * 0.30f)
            }
        }
        Icone.MAIS -> {
            linha(0.50f, 0.18f, 0.50f, 0.82f)
            linha(0.18f, 0.50f, 0.82f, 0.50f)
        }
        Icone.MIDIA -> {
            caminho {
                moveTo(w * 0.32f, h * 0.18f)
                lineTo(w * 0.80f, h * 0.50f)
                lineTo(w * 0.32f, h * 0.82f)
                close()
            }
        }
        Icone.APRESENTACAO -> {
            caixa(0.10f, 0.16f, 0.80f, 0.50f, 0.08f)
            linha(0.50f, 0.66f, 0.50f, 0.80f)
            linha(0.34f, 0.90f, 0.50f, 0.80f)
            linha(0.66f, 0.90f, 0.50f, 0.80f)
        }
        Icone.NAVEGADOR -> {
            circulo(0.50f, 0.50f, 0.38f)
            drawOval(cor, Offset(w * 0.34f, h * 0.12f), Size(w * 0.32f, h * 0.76f), style = estilo)
            linha(0.13f, 0.50f, 0.87f, 0.50f)
        }
        Icone.OLHO, Icone.OLHO_FECHADO -> {
            caminho {
                moveTo(w * 0.08f, h * 0.50f)
                quadraticBezierTo(w * 0.50f, h * 0.14f, w * 0.92f, h * 0.50f)
                quadraticBezierTo(w * 0.50f, h * 0.86f, w * 0.08f, h * 0.50f)
                close()
            }
            circulo(0.50f, 0.50f, 0.13f)
            if (icone == Icone.OLHO_FECHADO) linha(0.16f, 0.86f, 0.84f, 0.14f)
        }
        Icone.EXPANDIR -> {
            linha(0.12f, 0.36f, 0.12f, 0.12f); linha(0.12f, 0.12f, 0.36f, 0.12f)
            linha(0.64f, 0.12f, 0.88f, 0.12f); linha(0.88f, 0.12f, 0.88f, 0.36f)
            linha(0.88f, 0.64f, 0.88f, 0.88f); linha(0.88f, 0.88f, 0.64f, 0.88f)
            linha(0.36f, 0.88f, 0.12f, 0.88f); linha(0.12f, 0.88f, 0.12f, 0.64f)
        }
        Icone.SAIR -> {
            caminho {
                moveTo(w * 0.56f, h * 0.14f)
                lineTo(w * 0.18f, h * 0.14f)
                lineTo(w * 0.18f, h * 0.86f)
                lineTo(w * 0.56f, h * 0.86f)
            }
            linha(0.44f, 0.50f, 0.88f, 0.50f)
            linha(0.72f, 0.34f, 0.88f, 0.50f)
            linha(0.72f, 0.66f, 0.88f, 0.50f)
        }
        Icone.INFO -> {
            circulo(0.50f, 0.50f, 0.38f)
            circulo(0.50f, 0.30f, 0.038f, preenchido = true)
            linha(0.50f, 0.44f, 0.50f, 0.72f)
        }
    }
}
