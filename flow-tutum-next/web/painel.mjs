import {
  calcularEnquadramento, ampliarNoPonto, deslocarEnquadramento, pontoRemoto
} from "./geometria.mjs";
import {
  comandoMouse, comandoRolagem, comandoTecla,
  diferencaDeDigitacao, SessaoDeEntrada
} from "./entrada.mjs";

import { GestosTouchpad } from "./gestos-touchpad.mjs";

const $ = id => document.getElementById(id);
const tolerancia = 9;
const limite = (n, min, max) => Math.min(max, Math.max(min, n));
const estado = {
  escala: "ajustar", zoom: 1, panX: 0, panY: 0,
  origemLargura: 3440, origemAltura: 1440,
  cursor: { x: .5, y: .5 },
  toque: "clicar", sensibilidade: 1.5
};

// A integração real é feita somente com o Flow Tutum Next autenticado.
// Nenhum WebSocket público, PIN ou sessão fictícia é criado neste painel.
const ponte = window.FlowTutumNext;
const sessao = new SessaoDeEntrada(comando => ponte?.wsSend?.(comando) === true);

function aviso(mensagem) {
  $("estado-texto").textContent = mensagem;
}

function sincronizarConexao() {
  const ligado = !!(ponte?.state?.authenticated && ponte?.state?.approved &&
    ponte?.state?.sessionReady && ponte?.state?.ws?.readyState === WebSocket.OPEN);
  if (ligado && !sessao.ativa) sessao.conectar();
  if (!ligado && sessao.ativa) {
    sessao.desconectar(estado.cursor.x, estado.cursor.y);
    cancelarGestosLocais();
  }
  $("ponto-estado").dataset.ligado = String(ligado);
  aviso(ligado ? "PC conectado" : "Servidor desconectado");
  if (!ligado) atualizarArraste(false);
  return ligado;
}
function enviar(comando) {
  if (!sincronizarConexao()) {
    aviso("Abra o Flow Tutum Next na conta AS");
    return false;
  }
  return sessao.enviar(comando);
}

function obterParametros() {
  const retangulo = $("janela-remota").getBoundingClientRect();
  return {
    origemLargura: estado.origemLargura, origemAltura: estado.origemAltura,
    areaLargura: Math.max(1, retangulo.width),
    areaAltura: Math.max(1, retangulo.height),
    modo: estado.escala, zoom: estado.zoom, panX: estado.panX, panY: estado.panY
  };
}
function desenharEnquadramento() {
  const t = calcularEnquadramento(obterParametros());
  estado.zoom = t.zoom;
  estado.panX = t.panX;
  estado.panY = t.panY;
  const plano = $("plano-remoto");
  plano.style.left = t.x + "px";
  plano.style.top = t.y + "px";
  plano.style.width = t.largura + "px";
  plano.style.height = t.altura + "px";
  plano.style.right = "auto";
  plano.style.bottom = "auto";
  $("percentual-zoom").textContent = Math.round(t.zoom * 100) + "%";
  $("restaurar-zoom").textContent = Math.round(t.zoom * 100) + "%";
  $("modo-escala").textContent = t.modo === "ajustar" ? "AJUSTAR" : "PREENCHER";
  $("alternar-escala").textContent = t.modo === "ajustar" ? "Preencher tela" : "Enquadrar";
  return t;
}
function zoomNoCentro(fator) {
  const p = obterParametros();
  const t = ampliarNoPonto(p, estado.zoom, limite(estado.zoom * fator, 1, 6),
    p.areaLargura / 2, p.areaAltura / 2);
  estado.zoom = t.zoom;
  estado.panX = t.panX;
  estado.panY = t.panY;
  desenharEnquadramento();
}
$("aumentar-zoom").onclick = () => zoomNoCentro(1.3);
$("diminuir-zoom").onclick = () => zoomNoCentro(1 / 1.3);
$("restaurar-zoom").onclick = () => {
  estado.zoom = 1; estado.panX = 0; estado.panY = 0; desenharEnquadramento();
};
$("alternar-escala").onclick = () => {
  estado.escala = estado.escala === "ajustar" ? "preencher" : "ajustar";
  estado.zoom = 1; estado.panX = 0; estado.panY = 0; desenharEnquadramento();
};
$("tela-cheia").onclick = async () => {
  const janela = $("janela-remota");
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else if (janela.requestFullscreen) await janela.requestFullscreen();
    else aviso("Tela cheia não disponível neste navegador");
  } catch { aviso("Tela cheia bloqueada pelo sistema"); }
};
document.addEventListener("fullscreenchange", desenharEnquadramento);
new ResizeObserver(desenharEnquadramento).observe($("janela-remota"));

// Toque sobre a própria imagem; dois dedos fazem zoom e arrastam sem mudar o PC.
const contatosTela = new Map();
let gestoTela = null;
const janela = $("janela-remota");
function posicaoLocal(ev) {
  const r = janela.getBoundingClientRect();
  return { x: ev.clientX - r.left, y: ev.clientY - r.top };
}
function cursorNaTela(ponto) {
  const pos = pontoRemoto(desenharEnquadramento(), ponto.x, ponto.y);
  if (pos) estado.cursor = { x: pos.normalizadoX, y: pos.normalizadoY };
  return pos;
}
janela.addEventListener("pointerdown", ev => {
  if (ev.pointerType === "mouse" && ev.button !== 0) return;
  ev.preventDefault();
  janela.setPointerCapture(ev.pointerId);
  const p = posicaoLocal(ev);
  contatosTela.set(ev.pointerId, { ...p, inicioX: p.x, inicioY: p.y, inicio: ev.timeStamp, arrastou: false });
  if (contatosTela.size === 2) {
    const [a, b] = [...contatosTela.values()];
    gestoTela = {
      distancia: Math.max(1, Math.hypot(a.x - b.x, a.y - b.y)),
      zoom: estado.zoom, panX: estado.panX, panY: estado.panY,
      centroX: (a.x + b.x) / 2, centroY: (a.y + b.y) / 2
    };
  }
});
janela.addEventListener("pointermove", ev => {
  if (!contatosTela.has(ev.pointerId)) return;
  const a = contatosTela.get(ev.pointerId);
  const anterior = { x: a.x, y: a.y };
  const atual = posicaoLocal(ev);
  a.x = atual.x; a.y = atual.y;
  if (Math.hypot(a.x - a.inicioX, a.y - a.inicioY) > tolerancia) a.arrastou = true;
  ev.preventDefault();
  if (contatosTela.size >= 2 && gestoTela) {
    const [p, q] = [...contatosTela.values()];
    const dist = Math.max(1, Math.hypot(p.x - q.x, p.y - q.y));
    const meioX = (p.x + q.x) / 2, meioY = (p.y + q.y) / 2;
    const base = { ...obterParametros(), zoom: gestoTela.zoom,
      panX: gestoTela.panX, panY: gestoTela.panY };
    const atualizado = ampliarNoPonto(base, gestoTela.zoom,
      gestoTela.zoom * dist / gestoTela.distancia, gestoTela.centroX, gestoTela.centroY);
    const movido = deslocarEnquadramento({
      ...base, zoom: atualizado.zoom, panX: atualizado.panX, panY: atualizado.panY
    }, meioX - gestoTela.centroX, meioY - gestoTela.centroY);
    estado.zoom = movido.zoom; estado.panX = movido.panX; estado.panY = movido.panY;
    desenharEnquadramento();
  } else if (contatosTela.size === 1) {
    const m = cursorNaTela(atual);
    if (m && Math.abs(atual.x - anterior.x) + Math.abs(atual.y - anterior.y) > .4)
      enviar(comandoMouse("move", m.normalizadoX, m.normalizadoY));
  }
});
janela.addEventListener("pointerup", ev => {
  const atual = contatosTela.get(ev.pointerId);
  if (!atual) return;
  contatosTela.delete(ev.pointerId);
  ev.preventDefault();
  if (!gestoTela && !atual.arrastou && estado.toque === "clicar") {
    const m = cursorNaTela(posicaoLocal(ev));
    if (m) enviar(comandoMouse("tap", m.normalizadoX, m.normalizadoY,
      ev.timeStamp - atual.inicio > 650 ? "right" : "left"));
  }
  if (contatosTela.size === 0) gestoTela = null;
});
janela.addEventListener("pointercancel", ev => {
  contatosTela.delete(ev.pointerId);
  if (!contatosTela.size) gestoTela = null;
});
janela.addEventListener("contextmenu", ev => ev.preventDefault());

// Mouse relativo: um dedo movimenta, dois dedos rolam nos dois eixos.
const touchpad = $("touchpad"), gestos = new GestosTouchpad();
function cancelarGestosLocais() {
  gestos.cancelarTudo();
  contatosTela.clear();
  gestoTela = null;
  yAnterior = null;
}
function moverCursor(dx, dy) {
  const ret = touchpad.getBoundingClientRect();
  estado.cursor.x = limite(estado.cursor.x + dx * estado.sensibilidade / Math.max(1, ret.width), 0, 1);
  estado.cursor.y = limite(estado.cursor.y + dy * estado.sensibilidade / Math.max(1, ret.height), 0, 1);
  enviar(comandoMouse("move", estado.cursor.x, estado.cursor.y));
}
touchpad.addEventListener("pointerdown", ev => {
  ev.preventDefault();
  if (!gestos.iniciar(ev.pointerId, ev.clientX, ev.clientY, ev.timeStamp)) return;
  touchpad.setPointerCapture(ev.pointerId);
});
touchpad.addEventListener("pointermove", ev => {
  const movimento = gestos.mover(ev.pointerId, ev.clientX, ev.clientY);
  if (!movimento) return;
  ev.preventDefault();
  const { dx, dy, rolagem, moverCursor: mover } = movimento;
  if (rolagem) {
    if (Math.abs(dy) > 1) enviar(comandoRolagem(false, -dy * 8));
    if (Math.abs(dx) > 1) enviar(comandoRolagem(true, dx * 8));
  } else if (mover && Math.abs(dx) + Math.abs(dy) > .5) moverCursor(dx, dy);
});
touchpad.addEventListener("pointerup", ev => {
  ev.preventDefault();
  const botao = gestos.finalizar(ev.pointerId, ev.timeStamp);
  if (botao) enviar(comandoMouse("tap", estado.cursor.x, estado.cursor.y, botao));
});
touchpad.addEventListener("pointercancel", ev => {
  gestos.finalizar(ev.pointerId, ev.timeStamp, { cancelado: true });
});
touchpad.addEventListener("contextmenu", ev => ev.preventDefault());
touchpad.addEventListener("keydown", ev => {
  if (ev.key === "Enter" || ev.key === " ") {
    ev.preventDefault(); enviar(comandoMouse("tap", estado.cursor.x, estado.cursor.y));
  }
});

const trilho = $("trilho-rolagem");
let yAnterior = null;
trilho.addEventListener("pointerdown", ev => {
  ev.preventDefault(); yAnterior = ev.clientY; trilho.setPointerCapture(ev.pointerId);
});
trilho.addEventListener("pointermove", ev => {
  if (yAnterior === null) return;
  ev.preventDefault();
  const dy = ev.clientY - yAnterior;
  if (Math.abs(dy) > 1) {
    enviar(comandoRolagem(false, -dy * 9)); yAnterior = ev.clientY;
  }
});
for (const evento of ["pointerup", "pointercancel"]) {
  trilho.addEventListener(evento, () => yAnterior = null);
}
trilho.addEventListener("keydown", ev => {
  if (!["ArrowDown", "ArrowUp"].includes(ev.key)) return;
  ev.preventDefault(); enviar(comandoRolagem(false, ev.key === "ArrowDown" ? -120 : 120));
});
document.querySelectorAll("[data-botao]").forEach(botao => botao.addEventListener("click", () =>
  enviar(comandoMouse("tap", estado.cursor.x, estado.cursor.y, botao.dataset.botao))));

function atualizarArraste(pressionado) {
  const botao = $("segurar-mouse");
  botao.setAttribute("aria-pressed", String(pressionado));
  botao.textContent = pressionado ? "Soltar arraste" : "Segurar para arrastar";
}
$("segurar-mouse").onclick = () => {
  if (sessao.arrastando) {
    sessao.soltar("left", estado.cursor.x, estado.cursor.y);
    atualizarArraste(false);
  } else if (sincronizarConexao()) {
    const ligado = sessao.pressionar("left", estado.cursor.x, estado.cursor.y);
    atualizarArraste(ligado);
  }
};
$("duplo-clique").onclick = () => {
  if (!sincronizarConexao()) return;
  enviar(comandoMouse("tap", estado.cursor.x, estado.cursor.y));
  setTimeout(() => enviar(comandoMouse("tap", estado.cursor.x, estado.cursor.y)), 90);
};
$("rolar-esquerda").onclick = () => enviar(comandoRolagem(true, -240));
$("rolar-direita").onclick = () => enviar(comandoRolagem(true, 240));

// O Gboard (ou outro teclado nativo) é aberto pelo foco no textarea.
// Os eventos de composição não enviam caracteres incompletos ao Windows.
const dialogoTeclado = $("teclado");
const campo = $("campo-teclado");
let confirmado = "", compondo = false;
$("abrir-teclado").onclick = () => {
  dialogoTeclado.showModal();
  requestAnimationFrame(() => campo.focus());
};
$("fechar-teclado").onclick = () => dialogoTeclado.close();
campo.addEventListener("compositionstart", () => compondo = true);
campo.addEventListener("compositionend", () => {
  compondo = false; sincronizarTexto();
});
function sincronizarTexto() {
  if (compondo) return;
  if (!sincronizarConexao()) {
    // Nunca enviar ao PC, depois da reconexão, texto escrito enquanto offline.
    confirmado = campo.value;
    return;
  }
  let comandos;
  try { comandos = diferencaDeDigitacao(confirmado, campo.value); }
  catch { aviso("Limite de digitação excedido"); return; }
  let sucesso = true;
  for (const comando of comandos) {
    if (!enviar(comando)) { sucesso = false; break; }
  }
  if (sucesso) confirmado = campo.value;
}
campo.addEventListener("input", sincronizarTexto);
document.querySelectorAll("[data-tecla]").forEach(botao =>
  botao.addEventListener("click", () => {
    enviar(comandoTecla(botao.dataset.tecla)); campo.focus();
  }));
$("limpar-teclado").onclick = () => {
  // Não envia Backspace para apagar o PC.
  confirmado = ""; campo.value = ""; campo.focus();
};

// Preferências: armazenadas apenas neste dispositivo, nunca em contas de terceiros.
function lerPreferencia(nome, padrao) {
  try { return localStorage.getItem("tutum:" + nome) || padrao; } catch { return padrao; }
}
function salvarPreferencia(nome, valor) {
  try { localStorage.setItem("tutum:" + nome, valor); } catch {}
}
estado.toque = lerPreferencia("toque", "clicar") === "mover" ? "mover" : "clicar";
estado.sensibilidade = limite(Number(lerPreferencia("sensibilidade", "1.5")) || 1.5, .5, 3);
document.documentElement.dataset.tema = lerPreferencia("tema", "escuro") === "claro" ? "claro" : "escuro";
$("abrir-ajustes").onclick = () => {
  $("config-toque").value = estado.toque;
  $("config-sensibilidade").value = String(estado.sensibilidade);
  $("config-tema").value = document.documentElement.dataset.tema;
  $("configuracoes").showModal();
};
$("configuracoes").addEventListener("close", () => {
  estado.toque = $("config-toque").value;
  estado.sensibilidade = Number($("config-sensibilidade").value);
  document.documentElement.dataset.tema = $("config-tema").value;
  salvarPreferencia("toque", estado.toque);
  salvarPreferencia("sensibilidade", String(estado.sensibilidade));
  salvarPreferencia("tema", document.documentElement.dataset.tema);
});
function trocarPagina(pagina) {
  for (const nome of ["tela", "touchpad"]) {
    const atual = nome === pagina;
    $("pagina-" + nome).hidden = !atual;
    $("aba-" + nome).setAttribute("aria-selected", String(atual));
    $("aba-" + nome).tabIndex = atual ? 0 : -1;
  }
  if (pagina !== "touchpad" && sessao.arrastando) {
    sessao.soltar("left", estado.cursor.x, estado.cursor.y);
    atualizarArraste(false);
  }
  if (pagina === "tela") requestAnimationFrame(desenharEnquadramento);
}
$("aba-tela").onclick = () => trocarPagina("tela");
$("aba-touchpad").onclick = () => trocarPagina("touchpad");
document.querySelector(".navegacao").addEventListener("keydown", ev => {
  if (!["ArrowLeft", "ArrowRight"].includes(ev.key)) return;
  ev.preventDefault();
  const nome = ev.key === "ArrowLeft" ? "tela" : "touchpad";
  trocarPagina(nome); $("aba-" + nome).focus();
});

// O canvas só recebe vídeo quando o aplicativo original conectar seu renderizador.
// Esta função é o ponto de integração com a captura H.264/JPEG do Next.
window.FlowTutumPainel = Object.freeze({
  atualizarDimensoes(largura, altura) {
    if (!(largura > 0 && altura > 0)) throw new RangeError("Resolução remota inválida");
    estado.origemLargura = largura;
    estado.origemAltura = altura;
    $("video-remoto").width = largura;
    $("video-remoto").height = altura;
    desenharEnquadramento();
  },
  informarVideoDisponivel() { $("mensagem-video").hidden = true; },
  atualizarConexao: sincronizarConexao,
  desconectarControles() {
    sessao.desconectar(estado.cursor.x, estado.cursor.y);
    atualizarArraste(false);
    cancelarGestosLocais();
    sincronizarConexao();
  }
});
window.addEventListener("blur", () => {
  sessao.desconectar(estado.cursor.x, estado.cursor.y);
  atualizarArraste(false);
  cancelarGestosLocais();
});
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    sessao.desconectar(estado.cursor.x, estado.cursor.y);
    atualizarArraste(false);
    cancelarGestosLocais();
  } else sincronizarConexao();
});
window.addEventListener("flowtutum:estado", sincronizarConexao);
desenharEnquadramento();
sincronizarConexao();
