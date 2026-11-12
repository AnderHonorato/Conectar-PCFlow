import test from "node:test";import assert from "node:assert/strict";import {readFileSync} from "node:fs";
const base=new URL("../web/",import.meta.url);const ler=n=>readFileSync(new URL(n,base),"utf8");
test("qualidade inteligente tem controle visível e ajuste real",()=>{const js=ler("qualidade-inteligente.js");assert.match(js,/flow-captura-medida/);assert.match(js,/seletor\.dispatchEvent/);assert.match(js,/opcoes\[atual-1\]/);assert.match(js,/opcoes\[atual\+1\]/);});
test("não ajusta quando sessão indisponível ou vídeo H264 ativo",()=>{assert.match(ler("qualidade-inteligente.js"),/!estado\?\.sessionReady\|\|!estado\?\.authenticated\|\|estado\.videoActive/);});
test("interface e servidor incluem os recursos",()=>{assert.match(ler("index.html"),/qualidade-inteligente\.js/);assert.match(ler("index.html"),/qualidade-inteligente\.css/);assert.match(readFileSync(new URL("../../src/Program.cs",import.meta.url),"utf8"),/qualidade-inteligente\.js/);});
