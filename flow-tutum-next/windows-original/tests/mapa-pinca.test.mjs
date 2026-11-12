import test from "node:test";
import assert from "node:assert/strict";
import {escalaPinca,geometriaMiniatura,posicaoPan} from "../web/mapa-navegacao.mjs";
test("pinça aumenta e reduz zoom proporcionalmente",()=>{
 assert.equal(escalaPinca(100,200,1.5),3);
 assert.equal(escalaPinca(200,100,3),1.5);
});
test("pinça respeita limites e descarta dados inválidos",()=>{
 assert.equal(escalaPinca(100,1000,3),5);
 assert.equal(escalaPinca(100,1,1),1);
 assert.equal(escalaPinca(0,100,2),null);
 assert.equal(escalaPinca(100,NaN,2),null);
});
test("geometria ultrawide mantém o ponto navegável no quadro",()=>{
 const g=geometriaMiniatura(3440,1440,320,188);
 assert.ok(g.largura<=320&&g.altura<=188);
 const p=posicaoPan(.75,.25,3440,1440,360,640);
 assert.ok(Number.isFinite(p.x)&&Number.isFinite(p.y));
});
