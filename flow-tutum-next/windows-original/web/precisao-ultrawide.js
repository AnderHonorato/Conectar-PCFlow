(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),tools=document.querySelector(".zoom-tools"),tela=document.getElementById("screen");
if(!app||!viewer||!tools||!tela)return;
const {state,paint,zoomNoPonto}=app;
const painel=document.createElement("section");
painel.className="precisao-ultrawide";painel.setAttribute("aria-label","Navegação de precisão na tela");
painel.innerHTML='<div class="precisao-topo"><strong>Precisão ultrawide</strong><output id="precisao-estado" role="status" aria-live="polite">Enquadrado</output></div><div class="precisao-escalas" role="group" aria-label="Ampliação da tela"><button type="button" data-escala="1">100%</button><button type="button" data-escala="2">200%</button><button type="button" data-escala="3">300%</button><button type="button" data-ajustar="true" aria-label="Restaurar enquadramento e centralizar">Ajustar tela</button></div><div class="precisao-direcoes" role="group" aria-label="Mover imagem ampliada"><button type="button" data-pan="cima" aria-label="Mover imagem para cima">↑</button><button type="button" data-pan="esquerda" aria-label="Mover imagem para esquerda">←</button><button type="button" data-pan="centro" aria-label="Centralizar imagem">◎</button><button type="button" data-pan="direita" aria-label="Mover imagem para direita">→</button><button type="button" data-pan="baixo" aria-label="Mover imagem para baixo">↓</button></div><p>Ampliação centrada no ponto visível. No modo Explorar, arraste para navegar, use pinça para ampliar e dê dois toques para ajustar. Nenhum gesto envia clique ao Windows.</p>';
tools.insertAdjacentElement("afterend",painel);
const saida=painel.querySelector("#precisao-estado");
function dimensoes(){const im=state.image;if(!im)return null;const w=state.videoActive?im.videoWidth:im.width,h=state.videoActive?im.videoHeight:im.height;return w&&h?{w,h}:null}
function limites(){const d=dimensoes();if(!d)return null;const w=tela.clientWidth,h=tela.clientHeight;if(!w||!h)return null;const base=state.fillMode==="fill"?Math.max(w/d.w,h/d.h):Math.min(w/d.w,h/d.h);return {x:Math.max(0,(d.w*base*state.zoom-w)/2),y:Math.max(0,(d.h*base*state.zoom-h)/2)}}
function restringir(){const l=limites();if(!l)return;state.panX=Math.max(-l.x,Math.min(l.x,state.panX));state.panY=Math.max(-l.y,Math.min(l.y,state.panY))}
function informar(){saida.textContent=Math.round(state.zoom*100)+"% · "+(state.exploring?"Explorando":"Controlando");painel.querySelectorAll("[data-escala]").forEach(b=>b.setAttribute("aria-pressed",String(Number(b.dataset.escala)===state.zoom)))}
function explorarSeguro(){if(!state.exploring)document.getElementById("explore-toggle")?.click();return state.exploring}
function escala(valor){if(!Number.isFinite(valor)||valor<1||valor>5)return;if(valor>1&&!explorarSeguro())return;state.fillMode="fit";zoomNoPonto(valor,tela.clientWidth/2,tela.clientHeight/2);restringir();paint();informar()}
function mover(direcao){if(!explorarSeguro())return;if(direcao==="centro"){state.panX=0;state.panY=0}else{const passo=Math.max(36,Math.min(viewer.clientWidth,viewer.clientHeight)*.22);const vetor={cima:[0,passo],baixo:[0,-passo],esquerda:[passo,0],direita:[-passo,0]}[direcao];if(!vetor)return;state.panX+=vetor[0];state.panY+=vetor[1]}restringir();paint();informar();window.dispatchEvent(new Event("flow-zoom-alterado"))}
painel.addEventListener("click",e=>{const b=e.target.closest("button");if(!b||!painel.contains(b))return;if(b.dataset.escala)escala(Number(b.dataset.escala));if(b.dataset.pan)mover(b.dataset.pan);if(b.dataset.ajustar){state.zoom=1;state.fillMode="fit";state.panX=0;state.panY=0;paint();informar();window.dispatchEvent(new Event("flow-zoom-alterado"))}});
// Gestos de exploração são tratados na captura para não disparar comandos do mouse.
const dedos=new Map();let gesto=null;let toqueInicial=null;let ultimoToque=0;let bloqueioClique=false;let mudouQuantidade=false;
function centro(){const pontos=[...dedos.values()];return {x:pontos.reduce((v,p)=>v+p.x,0)/pontos.length,y:pontos.reduce((v,p)=>v+p.y,0)/pontos.length}}
function distancia(){const p=[...dedos.values()];return p.length===2?Math.hypot(p[0].x-p[1].x,p[0].y-p[1].y):0}
function coordenadas(e){const r=tela.getBoundingClientRect();return {x:e.clientX-r.left,y:e.clientY-r.top}}
function limpar(){dedos.clear();gesto=null;toqueInicial=null;bloqueioClique=false;mudouQuantidade=false}
function capturar(e){
 if(!state.exploring)return;
 if(!["pointerdown","pointermove","pointerup","pointercancel","lostpointercapture"].includes(e.type))return;
 // Não interferir com mouse físico nem caneta; somente gestos de toque.
 if(e.pointerType!=="touch")return;
 e.preventDefault();e.stopImmediatePropagation();
 if(e.type==="pointerdown"){
  try{tela.setPointerCapture(e.pointerId)}catch{}dedos.set(e.pointerId,coordenadas(e));
  if(dedos.size===1)toqueInicial={...coordenadas(e),tempo:Date.now(),movido:false};
  else {toqueInicial=null;bloqueioClique=true;mudouQuantidade=true;ultimoToque=0;}
  gesto={centro:centro(),distancia:distancia(),zoom:state.zoom};
  return;
 }
 if(e.type==="pointercancel"||e.type==="lostpointercapture"||e.type==="pointerup"){
  if(e.type==="pointerup"&&dedos.size===1&&toqueInicial&&!toqueInicial.movido&&!bloqueioClique&&!mudouQuantidade&&Date.now()-toqueInicial.tempo<350){
   const agora=Date.now();if(agora-ultimoToque<330){
    state.zoom=1;state.fillMode="fit";state.panX=0;state.panY=0;paint();informar();window.dispatchEvent(new Event("flow-zoom-alterado"));ultimoToque=0;
   }else ultimoToque=agora;
  }
  dedos.delete(e.pointerId);
  if(!dedos.size){toqueInicial=null;bloqueioClique=false;mudouQuantidade=false;}
  gesto=dedos.size?{centro:centro(),distancia:distancia(),zoom:state.zoom}:null;
  return;
 }
 if(!dedos.has(e.pointerId))return;
 dedos.set(e.pointerId,coordenadas(e));
 const atual=centro();if(!gesto){gesto={centro:atual,distancia:distancia(),zoom:state.zoom};return}
 const dx=atual.x-gesto.centro.x,dy=atual.y-gesto.centro.y;
 if(toqueInicial&&Math.hypot(atual.x-toqueInicial.x,atual.y-toqueInicial.y)>10)toqueInicial.movido=true;
 if(dedos.size===2&&gesto.distancia>0){
  const novaDistancia=distancia();
  const novoZoom=Math.max(1,Math.min(5,state.zoom*novaDistancia/gesto.distancia));
  zoomNoPonto(novoZoom,gesto.centro.x,gesto.centro.y);
 }
 state.panX+=dx;state.panY+=dy;restringir();paint();
 gesto={centro:atual,distancia:distancia(),zoom:state.zoom};
 informar();
}
["pointerdown","pointermove","pointerup","pointercancel","lostpointercapture"].forEach(tipo=>tela.addEventListener(tipo,capturar,true));
window.addEventListener("flow-exploracao-alterada",()=>{if(!state.exploring)limpar()});
window.addEventListener("flow-zoom-alterado",informar);window.addEventListener("flow-exploracao-alterada",informar);informar();
})();
