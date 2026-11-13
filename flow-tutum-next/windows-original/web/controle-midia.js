/* Controle de mídia: atalhos nativos do Windows, sem simular estado do player. */
(function(){
"use strict";
const botoes=[
 ["midia-anterior","Faixa anterior","⏮"],
 ["midia-reproduzir","Reproduzir ou pausar","⏯"],
 ["midia-proxima","Próxima faixa","⏭"],
 ["volume-menos","Diminuir volume","−"],
 ["volume-mudo","Ativar ou desativar mudo","◌"],
 ["volume-mais","Aumentar volume","+"]
];
const painel=document.getElementById("midia-painel");
const abrir=document.getElementById("midia-abrir");
if(!painel||!abrir)return;
const api=()=>window.FlowTutumNext;
const mensagem=document.getElementById("midia-retorno");
function permitido(){const s=api()?.state;return !!(s?.approved&&s?.authenticated&&!s?.somenteVisualizacao&&!s?.stopped);}
function atualizar(){
 const pronto=permitido();
 painel.querySelectorAll("[data-midia]").forEach(b=>b.disabled=!pronto);
 if(!pronto)mensagem.textContent="Aguardando autorização de controle.";
}
function alternar(aberto){
 painel.hidden=!aberto;abrir.setAttribute("aria-expanded",String(aberto));
 if(aberto){atualizar();painel.querySelector("button")?.focus();}
 else abrir.focus();
}
abrir.addEventListener("click",()=>alternar(painel.hidden));
document.getElementById("midia-fechar").addEventListener("click",()=>alternar(false));
painel.addEventListener("keydown",e=>{if(e.key==="Escape"){e.preventDefault();alternar(false);}});
for(const [tecla,nome,simbolo] of botoes){
 const b=document.createElement("button");b.type="button";b.dataset.midia=tecla;
 b.setAttribute("aria-label",nome);b.title=nome;
 const icone=document.createElement("span");icone.setAttribute("aria-hidden","true");icone.textContent=simbolo;
 const rotulo=document.createElement("span");rotulo.textContent=nome;
 b.append(icone,rotulo);
 b.addEventListener("click",()=>{
  atualizar();
  if(!permitido())return;
  const enviado=api().wsSend({phase:"key",keys:tecla});
  mensagem.textContent=enviado?"Comando enviado: "+nome+".":"Comando não enviado. Confira a conexão.";
 });
 painel.querySelector(".midia-grade").append(b);
}
const observador=setInterval(()=>{if(!painel.hidden)atualizar();},1500);
window.addEventListener("pagehide",()=>clearInterval(observador),{once:true});
})();
