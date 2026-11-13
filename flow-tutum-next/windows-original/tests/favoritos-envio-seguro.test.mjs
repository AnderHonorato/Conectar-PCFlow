import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {autorizado,tentarComando,catalogo}=require("../web/favoritos.js");
const item=catalogo.find(x=>x.id==="play");
const ativo={approved:true,authenticated:true,somenteVisualizacao:false,stopped:false,pausaLocal:false};
test("bloqueia sessão pausada, revogada ou somente visualização",()=>{
 for(const campo of ["approved","authenticated","somenteVisualizacao","stopped","pausaLocal"]){
  const state={...ativo,[campo]:campo==="approved"||campo==="authenticated"?false:true};
  assert.equal(autorizado(state),false);
  let chamadas=0;
  assert.equal(tentarComando(item,state,()=>{chamadas++;return true},10000).ok,false);
  assert.equal(chamadas,0);
 }
});
test("não informa execução confirmada e limita repetição rápida",()=>{
 let chamadas=0;
 const enviar=()=>{chamadas++;return true};
 assert.equal(tentarComando(item,ativo,enviar,20000).ok,true);
 assert.equal(tentarComando(item,ativo,enviar,20001).ok,false);
 assert.equal(chamadas,1);
 assert.equal(tentarComando(item,ativo,enviar,20700).ok,true);
 assert.equal(chamadas,2);
});
test("falha de transporte não inicia bloqueio temporal",()=>{
 const outro=catalogo.find(x=>x.id==="volumeMais");
 assert.equal(tentarComando(outro,ativo,()=>false,30000).ok,false);
 assert.equal(tentarComando(outro,ativo,()=>true,30001).ok,true);
});
