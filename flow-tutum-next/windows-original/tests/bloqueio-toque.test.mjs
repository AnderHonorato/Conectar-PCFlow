// Teste de integração estática: execute com node --test tests/bloqueio-toque.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,resolve} from "node:path";
const raiz=resolve(dirname(fileURLToPath(import.meta.url)),"../web");
const ler=nome=>readFileSync(resolve(raiz,nome),"utf8");
test("arquivos de bloqueio estão conectados à interface",()=>{
 const html=ler("index.html");
 assert.match(html,/bloqueio-toque\.css/);
 assert.match(html,/bloqueio-toque\.js/);
});
test("bloqueio mantém desbloqueio acessível e não revoga sessão",()=>{
 const codigo=ler("bloqueio-toque.js");
 assert.match(codigo,/aria-modal/);
 assert.match(codigo,/desbloquear-toques/);
 assert.match(codigo,/evento\.key==="Escape"/);
 assert.match(codigo,/phase:"cancel"/);
 assert.doesNotMatch(codigo,/\/api\/unpair|localStorage\.removeItem/);
});
test("cortina cobre a tela e respeita movimento reduzido",()=>{
 const css=ler("bloqueio-toque.css");
 assert.match(css,/position:fixed;inset:0/);
 assert.match(css,/prefers-reduced-motion:no-preference/);
 assert.match(css,/min-height:54px/);
});
