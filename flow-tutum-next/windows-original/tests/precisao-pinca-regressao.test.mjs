import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const fonte = readFileSync(new URL("../web/precisao-ultrawide.js", import.meta.url), "utf8");
test("pinça mantém âncora no centro anterior e desloca centro depois", () => {
 assert.match(fonte, /zoomNoPonto\(novoZoom,gesto\.centro\.x,gesto\.centro\.y\)/);
 assert.match(fonte, /state\.panX\+=dx;state\.panY\+=dy/);
});
test("multitoque não se converte em duplo toque de ajuste", () => {
 assert.match(fonte, /mudouQuantidade=true;ultimoToque=0/);
 assert.match(fonte, /!bloqueioClique&&!mudouQuantidade/);
});
test("limpeza de gestos não conserva estado de multitoque", () => {
 assert.match(fonte, /function limpar\(\).*mudouQuantidade=false/);
});
