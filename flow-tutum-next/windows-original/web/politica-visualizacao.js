(function(raiz){
"use strict";
// Nunca libera comandos sem sessão autorizada. Cancelamento é permitido para soltar entradas presas.
function permitir(somenteVisualizacao,comando){
 return !somenteVisualizacao || comando?.phase==="cancel";
}
const politica={permitir};
if(typeof module!=="undefined"&&module.exports)module.exports=politica;
raiz.FlowPoliticaVisualizacao=politica;
})(typeof globalThis!=="undefined"?globalThis:this);
