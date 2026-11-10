import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../web");
const html = fs.readFileSync(path.join(raiz, "painel.html"), "utf8");
const css = fs.readFileSync(path.join(raiz, "painel.css"), "utf8");
const js = fs.readFileSync(path.join(raiz, "painel.mjs"), "utf8");

test("IDs HTML únicos e todos os controles do JS existem", () => {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
  assert.equal(ids.length, new Set(ids).size, "ID duplicado no painel");
  for (const [, id] of js.matchAll(/\$\("([^"]+)"\)/g)) {
    assert.ok(ids.includes(id), "Elemento não encontrado: " + id);
  }
});
test("navegação acessível com abas sem esconder funcionalidades", () => {
  assert.match(html, /role="tablist"/);
  for (const aba of ["tela", "touchpad"]) {
    assert.match(html, new RegExp('id="aba-' + aba + '"'));
    assert.match(html, new RegExp('id="pagina-' + aba + '"'));
  }
  assert.match(js, /aria-selected/);
});
test("painel não inclui tarefas automáticas ou botões perigosos de acesso irrestrito", () => {
  assert.doesNotMatch(js + html, /schtasks|Register-ScheduledTask|taskkill/);
  assert.doesNotMatch(js, /new WebSocket\(/);
  assert.match(js, /window\.FlowTutumNext/);
});
test("visualização contém configurações e respeito à orientação", () => {
  assert.match(html, /id="config-sensibilidade"/);
  assert.match(html, /id="config-toque"/);
  assert.match(css, /orientation:landscape/);
  assert.match(css, /safe-area-inset/);
});
test("interação com Gboard respeita eventos de composição", () => {
  assert.match(html, /id="campo-teclado"/);
  assert.match(js, /compositionstart/);
  assert.match(js, /compositionend/);
  assert.match(js, /diferencaDeDigitacao/);
});
test("estado offline é explícito para não simular transmissão", () => {
  assert.match(html, /Vídeo indisponível/);
  assert.match(html, /id="mensagem-video"/);
  assert.match(js, /informarVideoDisponivel/);
});

test("cancelamento de ponteiro na tela não deixa outro dedo clicar nem aplicar zoom", () => {
  assert.match(js, /janela\.addEventListener\("pointercancel", cancelarContatoTela\)/);
  assert.match(js, /janela\.addEventListener\("lostpointercapture", cancelarContatoTela\)/);
  assert.match(js, /gestoTela = contatosTela\.size \? \{ cancelado: true \} : null/);
  assert.match(js, /if \(gestoTela\?\.cancelado\) return/);
  assert.match(js, /if \(!gestoTela && !atual\.arrastou/);
});
