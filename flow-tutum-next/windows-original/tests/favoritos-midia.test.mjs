import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const favoritos=require("../web/favoritos.js");
const backup=require("../web/backup-favoritos.js");
test("catálogo oferece controles de mídia com contratos de tecla reais",()=>{
 const esperados={anterior:"midia-anterior",proxima:"midia-proxima",volumeMais:"volume-mais",volumeMenos:"volume-menos"};
 for(const [id,tecla] of Object.entries(esperados)){
  const acao=favoritos.catalogo.find(x=>x.id===id);
  assert.equal(acao?.tipo,"remoto");
  assert.deepEqual(acao.comando,{phase:"key",keys:tecla});
 }
});
test("backup preserva favoritos novos, ordem e compatibilidade",()=>{
 const lista=["anterior","proxima","volumeMais","volumeMenos","touchpad"];
 const dados={ativo:"Mídia",listas:{Pessoal:["touchpad"],Trabalho:["tela"],"Mídia":lista}};
 const restaurado=backup.validar(JSON.parse(backup.exportar(dados)));
 assert.deepEqual(restaurado.listas["Mídia"],lista);
 assert.deepEqual(backup.limpar(["anterior","anterior","desconhecido"]),["anterior"]);
});
test("reordenação mantém integridade de ações",()=>{
 assert.deepEqual(favoritos.reordenar(["anterior","proxima","volumeMais"],0,2),["proxima","volumeMais","anterior"]);
});
