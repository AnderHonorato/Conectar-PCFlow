import { createRequire } from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
const require=createRequire(import.meta.url);
const {podeFixar,adicionar}=require("../web/historico-acoes.js");
test("ação recente pode ser fixada no perfil sem duplicar",()=>{
 assert.equal(podeFixar("play",["touchpad"]),true);
 assert.equal(podeFixar("play",["play"]),false);
 assert.equal(podeFixar("inexistente",[]),false);
});
test("perfil cheio não aceita nova ação",()=>{
 assert.equal(podeFixar("play",Array.from({length:12},(_,i)=>"acao"+i)),false);
});
test("histórico preserva a ação mais recente sem duplicação",()=>{
 assert.deepEqual(adicionar(["play","zoom"],"zoom"),["zoom","play"]);
});
