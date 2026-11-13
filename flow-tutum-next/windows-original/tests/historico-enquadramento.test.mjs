import test from "node:test";import assert from "node:assert/strict";import {criarHistoricoEnquadramento} from "../web/historico-enquadramento.mjs";
const a={zoom:1,panX:0,panY:0,fillMode:"fit"},b={zoom:2,panX:120,panY:-20,fillMode:"fit"},c={zoom:3,panX:200,panY:10,fillMode:"fill"};
test("desfazer e refazer preservam enquadramento",()=>{const h=criarHistoricoEnquadramento();assert.equal(h.registrar(a,b),true);assert.deepEqual(h.desfazer(b),a);assert.deepEqual(h.refazer(a),b)});
test("novo movimento invalida refazer",()=>{const h=criarHistoricoEnquadramento();h.registrar(a,b);h.desfazer(b);h.registrar(a,c);assert.equal(h.refazer(c),null)});
test("não registra estados inválidos nem iguais",()=>{const h=criarHistoricoEnquadramento();assert.equal(h.registrar(a,a),false);assert.equal(h.registrar(a,{...b,zoom:Infinity}),false)});
test("histórico respeita limite",()=>{const h=criarHistoricoEnquadramento(2);h.registrar(a,b);h.registrar(b,c);h.registrar(c,a);assert.deepEqual(h.desfazer(a),c);assert.deepEqual(h.desfazer(c),b);assert.equal(h.desfazer(b),null)});
