(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),tools=document.querySelector(".zoom-tools");
if(!app||!viewer||!tools)return;
const {state,paint}=app;
const painel=document.createElement("section");
painel.className="precisao-ultrawide";painel.setAttribute("aria-label","Navegação de precisão na tela");
painel.innerHTML='<div class="precisao-topo"><strong>Precisão</strong><output id="precisao-estado" role="status" aria-live="polite">Enquadrado</output></div><div class="precisao-escalas" role="group" aria-label="Ampliação da tela"><button type="button" data-escala="1">100%</button><button type="button" data-escala="2">200%</button><button type="button" data-escala="3">300%</button></div><div class="precisao-direcoes" role="group" aria-label="Mover imagem ampliada"><button type="button" data-pan="cima" aria-label="Mover imagem para cima">↑</button><button type="button" data-pan="esquerda" aria-label="Mover imagem para esquerda">←</button><button type="button" data-pan="centro" aria-label="Centralizar imagem">◎</button><button type="button" data-pan="direita" aria-label="Mover imagem para direita">→</button><button type="button" data-pan="baixo" aria-label="Mover imagem para baixo">↓</button></div><p>Ao ampliar, o modo Explorar evita cliques acidentais. Toque em Controlar para sair.</p>';
tools.insertAdjacentElement("afterend",painel);
const saida=painel.querySelector("#precisao-estado");
function informar(){saida.textContent=Math.round(state.zoom*100)+"% · "+(state.exploring?"Explorando":"Controlando");painel.querySelectorAll("[data-escala]").forEach(b=>b.setAttribute("aria-pressed",String(Number(b.dataset.escala)===state.zoom)));}
function explorarSeguro(){
 if(!state.exploring)document.getElementById("explore-toggle")?.click();
 return state.exploring;
}
function escala(valor){
 if(!Number.isFinite(valor)||valor<1||valor>5)return;
 if(valor>1&&!explorarSeguro())return;
 state.zoom=valor;state.fillMode="fit";state.panX=0;state.panY=0;
 paint();window.dispatchEvent(new Event("flow-zoom-alterado"));informar();
}
function mover(direcao){
 if(!explorarSeguro())return;
 if(direcao==="centro"){state.panX=0;state.panY=0;}
 else {
  const passo=Math.max(36,Math.min(viewer.clientWidth,viewer.clientHeight)*0.22);
  const vetores={cima:[0,passo],baixo:[0,-passo],esquerda:[passo,0],direita:[-passo,0]};
  const vetor=vetores[direcao];if(!vetor)return;
  state.panX+=vetor[0];state.panY+=vetor[1];
 }
 paint();informar();
}
painel.addEventListener("click",e=>{
 const botao=e.target.closest("button");if(!botao||!painel.contains(botao))return;
 if(botao.dataset.escala)escala(Number(botao.dataset.escala));
 if(botao.dataset.pan)mover(botao.dataset.pan);
});
window.addEventListener("flow-zoom-alterado",informar);
window.addEventListener("flow-exploracao-alterada",informar);
informar();
})();
