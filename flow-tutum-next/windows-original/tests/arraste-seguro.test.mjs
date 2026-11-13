import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
const app=readFileSync(fileURLToPath(new URL("../web/app.js",import.meta.url)),"utf8");
const html=readFileSync(fileURLToPath(new URL("../web/index.html",import.meta.url)),"utf8");
test("arraste direto exige pressão longa antes de pressionar botão",()=>{
 assert.match(app,/Date\.now\(\)-p\.at>=420/);
 assert.match(app,/p\.drag=wsSend\(\{phase:"down"/);
 assert.match(app,/if\(p\.moved&&state\.mode==="direct"&&state\.tapBehavior==="click"\)return/);
});
test("gestos interrompidos cancelam pressão e feedback",()=>{
 assert.match(app,/state\.pointer\?\.drag\)wsSend\(\{phase:"cancel"\}\)/);
 assert.match(app,/tipo:"cancelado"/);
 assert.match(app,/tipo:"finalizado"/);
});
test("indicador de toque está integrado ao HTML",()=>{
 assert.match(html,/estado-toque\.css/);
 assert.match(html,/estado-toque\.js/);
});
