import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const js=readFileSync(new URL("../web/enhancements.js",import.meta.url),"utf8");
const css=readFileSync(new URL("../web/app.css",import.meta.url),"utf8");

test("modo palco não envia comando automaticamente ao abrir",()=>{
 const trecho=js.slice(js.indexOf('abrirPalco.addEventListener("click"'),js.indexOf('el("fechar-palco").addEventListener'));
 assert.ok(trecho.includes("sincronizarPalco()"));
 assert.ok(!trecho.includes("wsSend("));
});
test("comandos dependem de autorização e canal aberto",()=>{
 assert.match(js,/function sincronizarPalco\(\)[\s\S]*?state\.approved&&state\.authenticated&&state\.sessionReady&&state\.ws\?\.readyState===WebSocket\.OPEN/);
 assert.match(js,/if\(!botao\|\|botao\.disabled\|\|!palco\.contains\(botao\)\)return/);
});
test("diálogo permite fechamento e mantém navegação por teclado",()=>{
 assert.ok(js.includes('aria-modal","true"'));
 assert.ok(js.includes('evento.key==="Escape"'));
 assert.ok(js.includes('evento.key!=="Tab"'));
 assert.ok(js.includes("focoAnteriorPalco"));
});
test("CSS suporta telas pequenas, paisagem e movimento reduzido",()=>{
 assert.ok(css.includes(".palco-apresentacao[hidden]"));
 assert.ok(css.includes("@media(max-width:420px)"));
 assert.ok(css.includes("@media(orientation:landscape) and (max-height:500px)"));
 assert.ok(css.includes("@media(prefers-reduced-motion:no-preference)"));
});
