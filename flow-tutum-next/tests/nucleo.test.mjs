import test from "node:test";
import assert from "node:assert/strict";
import {
  calcularEnquadramento, pontoRemoto, ampliarNoPonto, deslocarEnquadramento
} from "../web/geometria.mjs";
import {
  comandoMouse, comandoRolagem, comandoTecla, diferencaDeDigitacao,
  separarGrafemas, SessaoDeEntrada
} from "../web/entrada.mjs";

const origem = { origemLargura: 3440, origemAltura: 1440 };
const proximo = (a, b, margem = 0.00001) => assert.ok(Math.abs(a - b) < margem, a + " != " + b);

test("portrait: encaixa 3440×1440 sem alterar a proporção", () => {
  const t = calcularEnquadramento({ ...origem, areaLargura: 360, areaAltura: 800 });
  proximo(t.largura, 360);
  proximo(t.altura, 1440 * 360 / 3440);
  proximo(t.x, 0);
  assert.ok(t.y > 300);
  assert.equal(t.origemLargura, 3440);
});

test("landscape: o ultrawide ocupa a largura do celular", () => {
  const t = calcularEnquadramento({ ...origem, areaLargura: 800, areaAltura: 360 });
  proximo(t.largura, 800);
  assert.ok(t.altura < 360);
});
test("preencher recorta o vídeo no celular, preserva a origem", () => {
  const t = calcularEnquadramento({
    ...origem, areaLargura: 360, areaAltura: 800, modo: "preencher"
  });
  proximo(t.altura, 800);
  assert.ok(t.largura > 360 && t.x < 0);
  assert.equal(t.origemLargura, 3440);
});
test("ponto no centro da imagem vira coordenada normalizada central", () => {
  const t = calcularEnquadramento({ ...origem, areaLargura: 800, areaAltura: 360 });
  const p = pontoRemoto(t, 400, 180);
  proximo(p.normalizadoX, 0.5);
  proximo(p.normalizadoY, 0.5);
});
test("área vazia no modo ajustar não provoca toque no PC", () => {
  const t = calcularEnquadramento({ ...origem, areaLargura: 360, areaAltura: 800 });
  assert.equal(pontoRemoto(t, 150, 20), null);
});
test("o clique que sai da área de vídeo pode ser limitado conscientemente", () => {
  const t = calcularEnquadramento({ ...origem, areaLargura: 360, areaAltura: 800 });
  const p = pontoRemoto(t, 150, -20, { limitarBordas: true });
  assert.equal(p.normalizadoY, 0);
});
test("pinça conserva âncora ao ampliar em torno de ponto visível", () => {
  const opts = { ...origem, areaLargura: 800, areaAltura: 360, modo: "ajustar" };
  const before = calcularEnquadramento(opts);
  const anchorX = 340, anchorY = 165;
  const mapped = pontoRemoto(before, anchorX, anchorY);
  const after = ampliarNoPonto(opts, 1, 2, anchorX, anchorY);
  const afterMapped = pontoRemoto(after, anchorX, anchorY);
  proximo(afterMapped.x, mapped.x);
  proximo(afterMapped.y, mapped.y);
});
test("zoom e panorâmica respeitam limites e rejeitam NaN", () => {
  const opts = { ...origem, areaLargura: 800, areaAltura: 360, zoom: 40, panX: 9000 };
  const t = calcularEnquadramento(opts);
  assert.equal(t.zoom, 6);
  assert.ok(t.panX < 9000);
  const moved = deslocarEnquadramento(opts, -20, 10);
  assert.ok(moved.panX <= t.panX);
  assert.throws(() => calcularEnquadramento({ ...opts, origemLargura: 0 }), RangeError);
  assert.throws(() => calcularEnquadramento({ ...opts, panX: Number.NaN }), RangeError);
});
test("comandos de mouse têm coordenadas normalizadas e botões limitados", () => {
  assert.deepEqual(comandoMouse("tap", 3, -3, "middle"),
    { phase: "tap", x: 1, y: 0, button: "middle" });
  assert.throws(() => comandoMouse("tap", 1, 1, "system"), RangeError);
  assert.throws(() => comandoMouse("tap", Number.NaN, 0), RangeError);
});
test("rolagem limitada impede valores absurdos e aceita horizontal", () => {
  assert.deepEqual(comandoRolagem(true, -90000), { phase: "hwheel", wheel: -1200 });
  assert.deepEqual(comandoRolagem(false, 90.9), { phase: "wheel", wheel: 90 });
});
test("teclas especiais são filtradas", () => {
  assert.deepEqual(comandoTecla("ENTER"), { phase: "key", keys: "enter" });
  assert.throws(() => comandoTecla("win+r"), RangeError);
});
test("digitação normal e acentos aparecem incrementalmente", () => {
  assert.deepEqual(diferencaDeDigitacao("", "olá"), [{ phase: "text", text: "olá" }]);
  assert.deepEqual(diferencaDeDigitacao("olá", "olá!"), [{ phase: "text", text: "!" }]);
});
test("graphemes não dividem emoji compostos, tom de pele ou marca combinante", () => {
  assert.equal(separarGrafemas("👩🏽‍💻").length, 1);
  assert.equal(separarGrafemas("e\u0301").length, 1);
  assert.deepEqual(diferencaDeDigitacao("Olá 👩🏽‍💻", "Olá "),
    [{ phase: "key", keys: "backspace" }]);
});
test("autocorreção substitui grafema intermediário sem apagar sufixo", () => {
  assert.deepEqual(diferencaDeDigitacao("casa", "cara"), [
    { phase: "key", keys: "arrowleft" },
    { phase: "key", keys: "backspace" },
    { phase: "text", text: "r" },
    { phase: "key", keys: "arrowright" }
  ]);
});
test("quebras de linha viram Enter e não são duplicadas", () => {
  assert.deepEqual(diferencaDeDigitacao("Oi", "Oi\nTudo bem"), [
    { phase: "key", keys: "enter" },
    { phase: "text", text: "Tudo bem" }
  ]);
});
test("não envia conteúdo sem alteração e limita tamanho do campo", () => {
  assert.deepEqual(diferencaDeDigitacao("ok", "ok"), []);
  assert.throws(() => diferencaDeDigitacao("", "x".repeat(2001)), RangeError);
});
test("sessão desconectada não manda comandos ao PC", () => {
  const mensagens = [];
  const sessao = new SessaoDeEntrada(m => { mensagens.push(m); return true; });
  assert.equal(sessao.enviar(comandoTecla("enter")), false);
  assert.equal(mensagens.length, 0);
});
test("desconexão solta imediatamente o mouse e limpa arraste", () => {
  const mensagens = [];
  const sessao = new SessaoDeEntrada(m => { mensagens.push(m); return true; });
  sessao.conectar();
  assert.equal(sessao.pressionar("left", .5, .5), true);
  assert.equal(sessao.pressionar("left", .5, .5), false);
  assert.equal(sessao.arrastando, true);
  sessao.desconectar();
  assert.equal(sessao.arrastando, false);
  assert.deepEqual(mensagens.map(m => m.phase), ["down", "up"]);
});
test("erros de transporte não mantêm sessão conectada", () => {
  const sessao = new SessaoDeEntrada(() => { throw Error("Wi-Fi caiu"); });
  sessao.conectar();
  assert.equal(sessao.enviar(comandoTecla("enter")), false);
  assert.equal(sessao.ativa, false);
});
