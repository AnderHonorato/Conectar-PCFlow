import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {test}=require("node:test");const assert=require("node:assert/strict");const {validar,exportar,limpar}=require("../windows-original/web/backup-favoritos.js");
test("remove IDs desconhecidos, duplicatas e limita a oito",()=>assert.deepEqual(limpar(["tela","tela","invasor","play"]),["tela","play"]));
test("backup conserva ordem e perfis",()=>{const d={ativo:"Mídia",listas:{Pessoal:["tela"],Trabalho:["teclado"],"Mídia":["play","volume"]}};assert.deepEqual(validar(JSON.parse(exportar(d))),d)});
test("não aceita formatos arbitrários nem listas malformadas",()=>{assert.throws(()=>validar({formato:"outro",versao:1,listas:{}}));assert.throws(()=>validar({formato:"flow-tutum-favoritos",versao:1,listas:{Pessoal:"token"}}))});
test("não exporta campos privados extras",()=>{const raw=exportar({ativo:"Pessoal",listas:{Pessoal:[],Trabalho:[],"Mídia":[]},token:"segredo"});assert.ok(!raw.includes("segredo"))});