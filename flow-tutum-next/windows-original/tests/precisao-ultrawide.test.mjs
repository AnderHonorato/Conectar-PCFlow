import test from "node:test";import assert from "node:assert/strict";import {readFileSync} from "node:fs";
const codigo=readFileSync(new URL("../web/precisao-ultrawide.js",import.meta.url),"utf8");
test("ampliacao ativa exploracao antes de permitir movimento",()=>{assert.match(codigo,/if\(valor>1&&!explorarSeguro\(\)\)return/);assert.match(codigo,/if\(!explorarSeguro\(\)\)return/);});
test("presets limitados e comandos apenas locais",()=>{assert.match(codigo,/valor<1\|\|valor>5/);assert.doesNotMatch(codigo,/wsSend\s*\(/);});
test("painel permite centralizar e acompanha zoom existente",()=>{assert.match(codigo,/state\.panX=0;state\.panY=0/);assert.match(codigo,/flow-zoom-alterado/);});
