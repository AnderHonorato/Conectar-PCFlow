(()=>{
"use strict";
const app=window.FlowTutumNext;
if(!app)return;
const {state,wsSend}=app;
const viewer=document.getElementById("viewer");
const toolbar=document.querySelector(".view-actions");
if(!viewer||!toolbar)return;
const botao=document.createElement("button");
botao.type="button";botao.id="alternar-visualizacao";botao.className="alternar-visualizacao";
botao.setAttribute("aria-pressed","false");botao.setAttribute("aria-controls","viewer");
const aviso=document.createElement("div");aviso.className="aviso-visualizacao";aviso.id="aviso-visualizacao";
aviso.setAttribute("role","status");aviso.hidden=true;
aviso.textContent="Somente visualização · nenhum comando será enviado ao PC";
viewer.append(aviso);toolbar.prepend(botao);
function atualizar(){
 const ativo=!!state.somenteVisualizacao;
 botao.textContent=ativo?"Ativar controle":"Somente visualizar";
 botao.setAttribute("aria-pressed",String(ativo));
 botao.setAttribute("aria-label",ativo?"Voltar ao controle remoto":"Bloquear comandos e somente visualizar");
 aviso.hidden=!ativo;
 viewer.classList.toggle("somente-visualizacao",ativo);
 document.getElementById("open-keyboard").disabled=ativo||!state.approved;
}
botao.addEventListener("click",()=>{
 if(!state.somenteVisualizacao){
  // Libera arrastos ANTES de ativar a proteção, sem desautorizar a sessão.
  wsSend({phase:"cancel"});
  state.pointer=null;state.contacts.clear();state.gesture=null;
  state.somenteVisualizacao=true;
  document.getElementById("keyboard-sheet").hidden=true;
 }else state.somenteVisualizacao=false;
 atualizar();
});
window.addEventListener("flow-permissao-alterada",atualizar);
atualizar();
})();
