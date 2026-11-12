import test from 'node:test';import assert from 'node:assert/strict';
import {normalizarPreferencias,deslocarCursor,calcularRolagem} from '../web/gestos-configuraveis.mjs';
test('preferencias validas e invalidas',()=>{assert.deepEqual(normalizarPreferencias(),{ponteiro:1.5,rolagem:1});assert.deepEqual(normalizarPreferencias({ponteiro:'NaN',rolagem:999}),{ponteiro:1.5,rolagem:3});});
test('cursor respeita sensibilidade e limites',()=>{assert.deepEqual(deslocarCursor({x:.5,y:.5},50,-50,100,100,2),{x:1,y:0});});
test('rolagem ajustavel e limitada',()=>{assert.equal(calcularRolagem(10,2),-140);assert.equal(calcularRolagem(-9999,3),960);});
