import test from 'node:test';
import assert from 'node:assert/strict';
import {limitarPan,posicaoPan,normalizarPonto,geometriaMiniatura} from '../web/mapa-navegacao.mjs';
test('ultrawide: limites horizontais impedem fundo vazio',()=>{
 assert.deepEqual(limitarPan(9999,-9999,3440,1440,360,720),{x:1540,y:-360});
});
test('imagem menor que visor permanece centralizada',()=>{
 assert.deepEqual(limitarPan(300,200,300,200,360,720),{x:0,y:0});
});
test('minimapa mantém ponto dentro da imagem',()=>{
 const r=geometriaMiniatura(3440,1440,320,188);
 assert.deepEqual(normalizarPonto(-20,999,r),{x:0,y:1});
 const p=posicaoPan(1,.5,3440,1440,360,720);
 assert.equal(p.x,-1540);assert.equal(p.y,0);
});
test('entradas inválidas não produzem coordenadas',()=>{
 assert.equal(posicaoPan(NaN,0,3440,1440,360,720),null);
 assert.equal(normalizarPonto(1,1,{x:0,y:0,largura:0,altura:1}),null);
});
