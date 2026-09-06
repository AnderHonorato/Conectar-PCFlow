package com.ander.pcflow.sessao

import kotlin.math.max
import kotlin.math.min

/** Ponto normalizado (0..1) dentro do monitor remoto. */
data class CoordenadaRemota(val x: Double, val y: Double)

/** Ampliação e deslocamento aplicados na imagem do PC, só do lado do celular. */
data class Visualizacao(
    val zoom: Float = ZOOM_MINIMO,
    val deslocX: Float = 0f,
    val deslocY: Float = 0f
) {
    /** Acima disso o gesto de dois dedos passa a deslocar a imagem em vez de rolar. */
    val ampliada: Boolean get() = zoom > 1.02f
}

const val ZOOM_MINIMO = 1f
const val ZOOM_MAXIMO = 4f

/**
 * Retângulo em que a imagem aparece de fato dentro da área, sem contar as
 * barras pretas. Vem de `ContentScale.Fit`: a proporção do monitor quase nunca
 * é a do celular, então sobra faixa preta em cima/embaixo ou nas laterais.
 */
data class AreaImagem(val esquerda: Float, val topo: Float, val largura: Float, val altura: Float) {
    val direita: Float get() = esquerda + largura
    val base: Float get() = topo + altura
}

fun areaDaImagem(
    larguraArea: Int,
    alturaArea: Int,
    larguraQuadro: Int,
    alturaQuadro: Int
): AreaImagem? {
    if (larguraArea <= 0 || alturaArea <= 0 || larguraQuadro <= 0 || alturaQuadro <= 0) return null
    val escala = min(larguraArea.toFloat() / larguraQuadro, alturaArea.toFloat() / alturaQuadro)
    val largura = larguraQuadro * escala
    val altura = alturaQuadro * escala
    return AreaImagem(
        esquerda = (larguraArea - largura) / 2f,
        topo = (alturaArea - altura) / 2f,
        largura = largura,
        altura = altura
    )
}

/**
 * Converte o ponto tocado na tela do celular em coordenada do monitor remoto.
 *
 * Desfaz, nesta ordem, a ampliação (que acontece em volta do centro da área) e
 * o deslocamento; depois desconta a barra preta. Devolve `null` quando o dedo
 * caiu na barra preta — ali não existe pixel do PC, então mover o ponteiro para
 * lá seria inventar posição.
 */
fun mapearToqueParaMonitor(
    toqueX: Float,
    toqueY: Float,
    larguraArea: Int,
    alturaArea: Int,
    larguraQuadro: Int,
    alturaQuadro: Int,
    visual: Visualizacao = Visualizacao()
): CoordenadaRemota? {
    val imagem = areaDaImagem(larguraArea, alturaArea, larguraQuadro, alturaQuadro) ?: return null
    if (visual.zoom <= 0f) return null

    val centroX = larguraArea / 2f
    val centroY = alturaArea / 2f
    val x = (toqueX - centroX - visual.deslocX) / visual.zoom + centroX
    val y = (toqueY - centroY - visual.deslocY) / visual.zoom + centroY

    if (x < imagem.esquerda || x > imagem.direita) return null
    if (y < imagem.topo || y > imagem.base) return null

    return CoordenadaRemota(
        x = ((x - imagem.esquerda) / imagem.largura).coerceIn(0f, 1f).toDouble(),
        y = ((y - imagem.topo) / imagem.altura).coerceIn(0f, 1f).toDouble()
    )
}

/** Quanto a imagem ampliada pode escorregar para cada lado sem descolar da tela. */
fun limiteDeslocamento(tamanhoArea: Int, zoom: Float): Float =
    max(0f, tamanhoArea * (zoom - 1f) / 2f)

fun Visualizacao.contida(larguraArea: Int, alturaArea: Int): Visualizacao {
    val limiteX = limiteDeslocamento(larguraArea, zoom)
    val limiteY = limiteDeslocamento(alturaArea, zoom)
    return copy(
        deslocX = deslocX.coerceIn(-limiteX, limiteX),
        deslocY = deslocY.coerceIn(-limiteY, limiteY)
    )
}

/**
 * Aplica a pinça mantendo debaixo dos dedos o mesmo ponto da imagem. Sem isso a
 * ampliação puxa tudo para o centro e a pessoa perde de vista o que queria ver.
 */
fun aplicarPinca(
    visual: Visualizacao,
    fator: Float,
    focoX: Float,
    focoY: Float,
    larguraArea: Int,
    alturaArea: Int
): Visualizacao {
    if (!fator.isFinite() || fator <= 0f) return visual
    val novoZoom = (visual.zoom * fator).coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)
    if (novoZoom == visual.zoom) return visual

    val centroX = larguraArea / 2f
    val centroY = alturaArea / 2f
    val conteudoX = (focoX - centroX - visual.deslocX) / visual.zoom
    val conteudoY = (focoY - centroY - visual.deslocY) / visual.zoom
    val ajustado = Visualizacao(
        zoom = novoZoom,
        deslocX = visual.deslocX - conteudoX * (novoZoom - visual.zoom),
        deslocY = visual.deslocY - conteudoY * (novoZoom - visual.zoom)
    )
    return ajustado.contida(larguraArea, alturaArea)
}

fun aplicarDeslocamento(
    visual: Visualizacao,
    dx: Float,
    dy: Float,
    larguraArea: Int,
    alturaArea: Int
): Visualizacao =
    visual.copy(deslocX = visual.deslocX + dx, deslocY = visual.deslocY + dy)
        .contida(larguraArea, alturaArea)
