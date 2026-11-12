import test from 'node:test';
import assert from 'node:assert/strict';
import {geometriaMiniatura,normalizarPonto,posicaoPan} from '../web/mapa-navegacao.mjs';
test('ultrawide cabe no minimapa sem distorcao',()=>{const g=geometriaMiniatura(3440,1440,320,188);assert.ok(g.largura<=320&&g.altura<=188);assert.ok(Math.abs(g.largura/g.altura-3440/1440)<1e-10)});
test('coordenadas do mapa ficam dentro da imagem',()=>{const g={x:20,y:30,largura:200,altura:100};assert.deepEqual(normalizarPonto(120,80,g),{x:.5,y:.5});assert.deepEqual(normalizarPonto(-10,200,g),{x:0,y:1})});
test('navegacao move imagem sem gerar comando de entrada',()=>{assert.deepEqual(posicaoPan(.5,.5,2000,1000,400,300),{x:0,y:0});assert.deepEqual(posicaoPan(1,0,2000,1000,400,300),{x:-1000,y:500})});
