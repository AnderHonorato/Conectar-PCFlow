import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require=createRequire(import.meta.url);
const {catalogo,normalizar,tentarComando}=require("../windows-original/web/favoritos.js");
const atalhos={apresentar:"f5",slideAtual:"shift+f5",slideProximo:"right",slideAnterior:"left",encerrarApresentacao:"escape"};
const autorizado={approved:true,authenticated:true,somenteVisualizacao:false,stopped:false,pausaLocal:false};
test("perfil de apresentação usa comandos reais e únicos",()=>{
 for(const [id,keys] of Object.entries(atalhos)){
  const acao=catalogo.find(item=>item.id===id);
  assert.ok(acao,id);assert.equal(acao.tipo,"remoto");assert.deepEqual(acao.comando,{phase:"key",keys});
 }
 assert.equal(new Set(catalogo.map(item=>item.id)).size,catalogo.length);
});
test("favoritos de apresentação respeitam persistência, limite e sanitização",()=>{
 const ids=Object.keys(atalhos);assert.deepEqual(normalizar([...ids,...ids,"invalido"]),ids);
 assert.equal(normalizar(Array(30).fill("apresentar")).length,1);
});
test("comando de apresentação exige autorização e confirmação do transporte",()=>{
 const item=catalogo.find(a=>a.id==="slideProximo");let chamadas=0;
 const enviar=()=>{chamadas++;return true};
 assert.equal(tentarComando(item,{...autorizado,approved:false},enviar,1000).ok,false);
 assert.equal(chamadas,0);
 assert.equal(tentarComando(item,autorizado,()=>false,2000).ok,false);
 assert.equal(tentarComando(item,autorizado,enviar,3000).ok,true);
 assert.equal(chamadas,1);
 assert.equal(tentarComando(item,autorizado,enviar,3100).ok,false);
 assert.equal(chamadas,1);
});
