import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const raiz=new URL("../web/",import.meta.url);
const ergonomia=readFileSync(new URL("ergonomia-touchpad.js",raiz),"utf8");
const gestos=readFileSync(new URL("enhancements.js",raiz),"utf8");
const html=readFileSync(new URL("index.html",raiz),"utf8");
test("configuração destro/canhoto é persistente e tem botões acessíveis",()=>{
 assert.match(ergonomia,/localStorage\.setItem\(chave,mao\)/);
 assert.match(ergonomia,/aria-pressed/);
 assert.match(ergonomia,/primario:\(\)=>mao==="esquerda"\?"right":"left"/);
 assert.match(ergonomia,/secundario:\(\)=>mao==="esquerda"\?"left":"right"/);
});
test("toque simples, longo, duplo, dois dedos e botões físicos usam mapeamento",()=>{
 assert.match(gestos,/button:duracao>700\?botaoSecundario\(\):botaoPrimario\(\)/);
 assert.match(gestos,/button:botaoSecundario\(\)/);
 assert.match(gestos,/button:botaoPrimario\(\)/);
 assert.match(gestos,/button:\(button\.dataset\.mouse==="left"\?/);
});
test("módulo carrega antes do controlador de gestos",()=>{
 assert.ok(html.indexOf("/ergonomia-touchpad.js")>=0);
 assert.ok(html.indexOf("/ergonomia-touchpad.js")<html.indexOf("/enhancements.js"));
});
