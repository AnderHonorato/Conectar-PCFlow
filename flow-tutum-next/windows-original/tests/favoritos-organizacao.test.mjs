import {createRequire} from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
const require=createRequire(import.meta.url);
const {reordenar,normalizar}=require("../web/favoritos.js");
test("arraste reordena sem duplicar e sem mutar origem",()=>{
 const inicial=["touchpad","teclado","play"];
 assert.deepEqual(reordenar(inicial,0,2),["teclado","play","touchpad"]);
 assert.deepEqual(inicial,["touchpad","teclado","play"]);
});
test("destinos inválidos não removem ações",()=>{
 assert.deepEqual(reordenar(["tela","play"],0,9),["tela","play"]);
 assert.deepEqual(reordenar(["tela","play"],-1,0),["tela","play"]);
});
test("persistência normaliza duplicatas e comandos desconhecidos",()=>{
 assert.deepEqual(normalizar(["play","play","inexistente","teclado"]),["play","teclado"]);
});
