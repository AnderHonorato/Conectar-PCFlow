import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const base=new URL("../web/",import.meta.url);
const ler=arquivo=>readFileSync(new URL(arquivo,base),"utf8");
test("interface inclui painel e seus recursos",()=>{
 const html=ler("index.html");
 for(const id of ["midia-abrir","midia-painel","midia-fechar","midia-retorno"])assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(html,/controle-midia\.js/);assert.match(html,/controle-midia\.css/);
});
test("comandos enviados exigem permissao e WebSocket autorizado",()=>{
 const js=ler("controle-midia.js");
 assert.match(js,/approved&&s\?\.authenticated/);
 assert.match(js,/!s\?\.somenteVisualizacao/);
 assert.match(js,/api\(\)\.wsSend\(\{phase:"key",keys:tecla\}\)/);
});
test("comandos correspondem ao mapa nativo Windows",()=>{
 const cs=readFileSync(new URL("../src/KeyboardEngine.cs",import.meta.url),"utf8");
 for(const key of ["midia-anterior","midia-reproduzir","midia-proxima","volume-menos","volume-mudo","volume-mais"]){
  assert.ok(ler("controle-midia.js").includes(key));
  assert.ok(cs.includes('["'+key+'"]'));
 }
});
test("servidor publica recursos por lista fechada",()=>{
 const cs=readFileSync(new URL("../src/Program.cs",import.meta.url),"utf8");
 assert.match(cs,/"controle-midia\.js","controle-midia\.css"/);
});
