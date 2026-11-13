(()=>{"use strict";
const app=window.FlowTutumNext;
const dock=document.querySelector(".dock");
if(!app||!dock)return;
const botao=document.createElement("button");
botao.type="button";botao.id="bloqueio-toque";botao.setAttribute("aria-label","Bloquear toques acidentais");botao.setAttribute("aria-pressed","false");
botao.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg><span>Bloquear</span>';
dock.append(botao);
const cortina=document.createElement("section");
cortina.id="cortina-toque";cortina.hidden=true;cortina.setAttribute("role","dialog");cortina.setAttribute("aria-modal","true");cortina.setAttribute("aria-labelledby","bloqueio-titulo");
cortina.innerHTML='<div class="bloqueio-cartao"><svg viewBox="0 0 48 48" aria-hidden="true"><rect x="10" y="20" width="28" height="24" rx="5"/><path d="M16 20v-6a8 8 0 0 1 16 0v6"/></svg><p class="bloqueio-marca">ANDER FLOW · FLOW TUTUM NEXT</p><h2 id="bloqueio-titulo">Toques bloqueados</h2><p>O celular não enviará novos toques enquanto esta proteção estiver ativa. A sessão Windows permanece conectada.</p><button type="button" id="desbloquear-toques">Desbloquear controle</button><small>Pressione Esc ou use o botão acima para voltar.</small></div>';
document.body.append(cortina);
const desbloquear=cortina.querySelector("#desbloquear-toques");
let anterior=null,bloqueado=false;
function alterar(ativo){
 if(ativo===bloqueado)return;
 bloqueado=ativo;
 if(ativo){
  anterior=document.activeElement;
  document.getElementById("keyboard-sheet")?.setAttribute("hidden","");
  app.wsSend({phase:"cancel"});
  cortina.hidden=false;
  botao.setAttribute("aria-pressed","true");
  botao.querySelector("span").textContent="Bloqueado";
  desbloquear.focus();
 }else{
  cortina.hidden=true;
  botao.setAttribute("aria-pressed","false");
  botao.querySelector("span").textContent="Bloquear";
  (anterior?.isConnected?anterior:botao).focus();
 }
}
botao.addEventListener("click",()=>alterar(true));
desbloquear.addEventListener("click",()=>alterar(false));
document.addEventListener("keydown",evento=>{
 if(!bloqueado)return;
 if(evento.key==="Escape"){evento.preventDefault();alterar(false);return;}
 if(evento.key==="Tab"){
  evento.preventDefault();desbloquear.focus();
 }
},true);
// Protege a interface contra cliques e gestos; não pausa o vídeo nem revoga a sessão.
cortina.addEventListener("pointerdown",evento=>{if(evento.target!==desbloquear)evento.preventDefault()});
window.FlowTutumBloqueio={estaBloqueado:()=>bloqueado};
})();