(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),tools=document.querySelector(".zoom-tools"),tela=document.getElementById("screen");
if(!app||!viewer||!tools||!tela)return;
const {state,paint,zoomNoPonto}=app;
const painel=document.createElement("section");
painel.className="precisao-ultrawide";painel.setAttribute("aria-label","Navegação de precisão na tela");
painel.innerHTML='<div class="precisao-topo"><strong>Precisão ultrawide</strong><output id="precisao-estado" role="status" aria-live="polite">Enquadrado</output></div><div class="precisao-escalas" role="group" aria-label="Ampliação da tela"><button type="button" data-escala="1">100%</button><button type="button" data-escala="2">200%</button><button type="button" data-escala="3">300%</button></div><div class="precisao-direcoes" role="group" aria-label="Mover imagem ampliada"><button type="button" data-pan="cima" aria-label="Mover imagem para cima">↑</button><button type="button" data-pan="esquerda" aria-label="Mover imagem para esquerda">←</button><button type="button" data-pan="centro" aria-label="Centralizar imagem">◎</button><button type="button" data-pan="direita" aria-label="Mover imagem para direita">→</button><button type="button" data-pan="baixo" aria-label="Mover imagem para baixo">↓</button></div><p>Ampliação centrada no ponto visível. O modo Explorar impede cliques no Windows.</p>';
tools.insertAdjacentElement("afterend",painel);
const saida=painel.querySelector("#precisao-estado");
function dimensoes(){const im=state.image;if(!im)return null;const w=state.videoActive?im.videoWidth:im.width,h=state.videoActive?im.videoHeight:im.height;return w&&h?{w,h}:null}
function limites(){const d=dimensoes();if(!d)return null;const w=tela.clientWidth,h=tela.clientHeight;if(!w||!h)return null;const base=state.fillMode==="fill"?Math.max(w/d.w,h/d.h):Math.min(w/d.w,h/d.h);return {x:Math.max(0,(d.w*base*state.zoom-w)/2),y:Math.max(0,(d.h*base*state.zoom-h)/2)}}
function restringir(){const l=limites();if(!l)return;state.panX=Math.max(-l.x,Math.min(l.x,state.panX));state.panY=Math.max(-l.y,Math.min(l.y,state.panY))}
function informar(){saida.textContent=Math.round(state.zoom*100)+"% · "+(state.exploring?"Explorando":"Controlando");painel.querySelectorAll("[data-escala]").forEach(b=>b.setAttribute("aria-pressed",String(Number(b.dataset.escala)===state.zoom)))}
function explorarSeguro(){if(!state.exploring)document.getElementById("explore-toggle")?.click();return state.exploring}
function escala(valor){if(!Number.isFinite(valor)||valor<1||valor>5)return;if(valor>1&&!explorarSeguro())return;state.fillMode="fit";zoomNoPonto(valor,tela.clientWidth/2,tela.clientHeight/2);restringir();paint();informar()}
function mover(direcao){if(!explorarSeguro())return;if(direcao==="centro"){state.panX=0;state.panY=0}else{const passo=Math.max(36,Math.min(viewer.clientWidth,viewer.clientHeight)*.22);const vetor={cima:[0,passo],baixo:[0,-passo],esquerda:[passo,0],direita:[-passo,0]}[direcao];if(!vetor)return;state.panX+=vetor[0];state.panY+=vetor[1]}restringir();paint();informar();window.dispatchEvent(new Event("flow-zoom-alterado"))}
painel.addEventListener("click",e=>{const b=e.target.closest("button");if(!b||!painel.contains(b))return;if(b.dataset.escala)escala(Number(b.dataset.escala));if(b.dataset.pan)mover(b.dataset.pan)});
window.addEventListener("flow-zoom-alterado",informar);window.addEventListener("flow-exploracao-alterada",informar);informar();
})();
