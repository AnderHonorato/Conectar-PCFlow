import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {normalizar,reordenar,PADRAO}=require("../web/favoritos.js");

test("reordenação preserva todos os atalhos e não altera a entrada",()=>{
 const original=["touchpad","teclado","explorar","play"];
 const novo=reordenar(original,0,3);
 assert.deepEqual(novo,["teclado","explorar","play","touchpad"]);
 assert.deepEqual(original,["touchpad","teclado","explorar","play"]);
});
test("movimentos fora da lista são ignorados sem perda",()=>{
 const original=["touchpad","teclado"];
 assert.deepEqual(reordenar(original,-1,1),original);
 assert.deepEqual(reordenar(original,0,2),original);
});
test("persistência sanitiza IDs, duplicatas e entradas inválidas",()=>{
 assert.deepEqual(normalizar(["play","play","invasor","teclado",null]),["play","teclado"]);
 assert.deepEqual(normalizar(null),PADRAO);
 assert.deepEqual(normalizar([]),[]);
});
