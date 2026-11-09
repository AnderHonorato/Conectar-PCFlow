// Geometria do espelho remoto: nunca altera a resolução ou a frequência do monitor.
const limitar = (n, min, max) => Math.min(max, Math.max(min, n));

function dimensao(n, nome) {
  if (!Number.isFinite(n) || n <= 0) throw new RangeError(nome + " deve ser positivo");
  return n;
}

export function calcularEnquadramento({
  origemLargura, origemAltura, areaLargura, areaAltura,
  modo = "ajustar", zoom = 1, panX = 0, panY = 0
}) {
  dimensao(origemLargura, "origemLargura");
  dimensao(origemAltura, "origemAltura");
  dimensao(areaLargura, "areaLargura");
  dimensao(areaAltura, "areaAltura");
  if (!["ajustar", "preencher"].includes(modo)) throw new RangeError("Modo desconhecido");
  if (![zoom, panX, panY].every(Number.isFinite)) throw new RangeError("Transformação inválida");

  const base = modo === "ajustar"
    ? Math.min(areaLargura / origemLargura, areaAltura / origemAltura)
    : Math.max(areaLargura / origemLargura, areaAltura / origemAltura);
  const fatorZoom = limitar(zoom, 1, 6);
  const escala = base * fatorZoom;
  const largura = origemLargura * escala;
  const altura = origemAltura * escala;
  const maxPanX = Math.max(0, (largura - areaLargura) / 2);
  const maxPanY = Math.max(0, (altura - areaAltura) / 2);
  const deslocX = limitar(panX, -maxPanX, maxPanX);
  const deslocY = limitar(panY, -maxPanY, maxPanY);
  return {
    x: (areaLargura - largura) / 2 + deslocX,
    y: (areaAltura - altura) / 2 + deslocY,
    largura, altura, escala, zoom: fatorZoom, panX: deslocX, panY: deslocY,
    origemLargura, origemAltura, areaLargura, areaAltura, modo
  };
}

// Coordenadas do toque e da tela são independentes da resolução física do PC.
// Para cliques no contorno vazio de "ajustar", não move o cursor para outra posição.
export function pontoRemoto(enquadramento, x, y, { limitarBordas = false } = {}) {
  if (![x, y].every(Number.isFinite)) throw new RangeError("Ponto inválido");
  const rx = (x - enquadramento.x) / enquadramento.escala;
  const ry = (y - enquadramento.y) / enquadramento.escala;
  const dentro = rx >= 0 && ry >= 0 &&
    rx <= enquadramento.origemLargura && ry <= enquadramento.origemAltura;
  if (!dentro && !limitarBordas) return null;
  const px = limitar(rx, 0, enquadramento.origemLargura);
  const py = limitar(ry, 0, enquadramento.origemAltura);
  return {
    x: px, y: py,
    normalizadoX: px / enquadramento.origemLargura,
    normalizadoY: py / enquadramento.origemAltura
  };
}

// Pinça centrada nos dedos: o ponto sob os dedos não "salta" após o zoom.
export function ampliarNoPonto(parametros, origemAnterior, zoomDesejado, ancoraX, ancoraY) {
  const antes = calcularEnquadramento({ ...parametros, zoom: origemAnterior });
  const ponto = pontoRemoto(antes, ancoraX, ancoraY, { limitarBordas: true });
  const depois = calcularEnquadramento({ ...parametros, zoom: zoomDesejado, panX: 0, panY: 0 });
  const deslocX = ancoraX - (depois.x + ponto.x * depois.escala);
  const deslocY = ancoraY - (depois.y + ponto.y * depois.escala);
  return calcularEnquadramento({
    ...parametros, zoom: zoomDesejado, panX: deslocX, panY: deslocY
  });
}

export function deslocarEnquadramento(parametros, dx, dy) {
  if (![dx, dy].every(Number.isFinite)) throw new RangeError("Deslocamento inválido");
  return calcularEnquadramento({
    ...parametros, panX: (parametros.panX || 0) + dx,
    panY: (parametros.panY || 0) + dy
  });
}
