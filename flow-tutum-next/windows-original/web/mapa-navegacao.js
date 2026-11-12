(()=>{
"use strict";
const flow=window.FlowTutumNext;if(!flow)return;
const {state,paint}=flow,viewer=document.getElementById("viewer"),tela=document.getElementById("screen");
const acao=document.createElement("button");acao.type="button";acao.className="mapa-acao";acao.textContent="Mapa da tela";acao.hidden=true;acao.setAttribute("aria-expanded","false");
const painel=document.createElement("section");painel.className="mapa-navegacao";painel.hidden=true;painel.setAttribute("aria-label","Navegação pela imagem ampliada");
painel.innerHTML='<div class="mapa-titulo"><span>Visão geral</span><button type="button" aria-label="Fechar mapa">×</button></div><canvas width="320" height="188" aria-label="Arraste para navegar pela tela ampliada" tabindex="0"></canvas><div class="mapa-comandos" aria-label="Deslocar visualização"><button type="button" data-direcao="esquerda" aria-label="Mover visão para esquerda">←</button><button type="button" data-direcao="cima" aria-label="Mover visão para cima">↑</button><button type="button" data-direcao="centro" aria-label="Centralizar visão">◎</button><button type="button" data-direcao="baixo" aria-label="Mover visão para baixo">↓</button><button type="button" data-direcao="direita" aria-label="Mover visão para direita">→</button></div><p class="mapa-ajuda" role="status" aria-live="polite">Modo Explorar: não envia cliques ao PC</p>';
viewer.append(acao,painel);
const mini=painel.querySelector("canvas"),ctx=mini.getContext("2d"),fechar=painel.querySelector("button");
let geometria=null,arrastando=false,modulo=null;
import("./mapa-navegacao.mjs").then(m=>{modulo=m;atualizar()}).catch(()=>{acao.hidden=true});
function dimensoes(){const imagem=state.image;return imagem?{w:state.videoActive?imagem.videoWidth:imagem.width,h:state.videoActive?imagem.videoHeight:imagem.height}:null}
function atualizar(){
 const d=dimensoes(),visivel=!!(d?.w&&d?.h&&state.zoom>1.01&&modulo);
 acao.hidden=!visivel;if(!visivel){painel.hidden=true;acao.setAttribute("aria-expanded","false");return}
 if(painel.hidden)return;
 ctx.clearRect(0,0,mini.width,mini.height);
 geometria=modulo.geometriaMiniatura(d.w,d.h,mini.width,mini.height);
 if(!geometria)return;
 ctx.fillStyle="#101820";ctx.fillRect(0,0,mini.width,mini.height);
 try{ctx.drawImage(state.image,geometria.x,geometria.y,geometria.largura,geometria.altura)}catch{}
 const escala=Math.min(tela.width/d.w,tela.height/d.h)*state.zoom;
 const largura=d.w*escala,altura=d.h*escala;
 const origemX=(tela.width-largura)/2+state.panX*tela.width/Math.max(1,tela.clientWidth);
 const origemY=(tela.height-altura)/2+state.panY*tela.height/Math.max(1,tela.clientHeight);
 const x=geometria.x-origemX/largura*geometria.largura,y=geometria.y-origemY/altura*geometria.altura;
 const w=tela.width/largura*geometria.largura,h=tela.height/altura*geometria.altura;
 ctx.fillStyle="rgba(245,188,117,.18)";ctx.fillRect(x,y,w,h);
 ctx.strokeStyle="#f3bf79";ctx.lineWidth=3;ctx.strokeRect(x,y,w,h);
}
function navegar(evento){
 if(!modulo||!geometria||!state.exploring){
  if(!state.exploring)document.getElementById("explore-toggle")?.click();
  if(!state.exploring)return;
 }
 const rect=mini.getBoundingClientRect(),x=(evento.clientX-rect.left)*mini.width/rect.width,y=(evento.clientY-rect.top)*mini.height/rect.height;
 const ponto=modulo.normalizarPonto(x,y,geometria),d=dimensoes();if(!ponto||!d)return;
 const escala=Math.min(tela.width/d.w,tela.height/d.h)*state.zoom;
 const pan=modulo.posicaoPan(ponto.x,ponto.y,d.w*escala,d.h*escala,tela.width,tela.height);
 if(!pan)return;state.panX=pan.x*tela.clientWidth/tela.width;state.panY=pan.y*tela.clientHeight/tela.height;
 paint();atualizar();
}
acao.addEventListener("click",()=>{painel.hidden=!painel.hidden;acao.setAttribute("aria-expanded",String(!painel.hidden));atualizar()});
fechar.addEventListener("click",()=>{painel.hidden=true;acao.setAttribute("aria-expanded","false");acao.focus()});
mini.addEventListener("pointerdown",e=>{e.preventDefault();arrastando=true;mini.setPointerCapture(e.pointerId);navegar(e)});
mini.addEventListener("pointermove",e=>{if(arrastando){e.preventDefault();navegar(e)}});
mini.addEventListener("pointerup",e=>{arrastando=false;if(mini.hasPointerCapture(e.pointerId))mini.releasePointerCapture(e.pointerId)});
mini.addEventListener("pointercancel",()=>arrastando=false);
function deslocar(direcao){
 if(!modulo||!state.exploring){document.getElementById("explore-toggle")?.click();if(!state.exploring)return}
 const d=dimensoes();if(!d)return;
 const escala=Math.min(tela.width/d.w,tela.height/d.h)*state.zoom;
 const passo=Math.max(24,Math.round(Math.min(tela.clientWidth,tela.clientHeight)*.16));
 const delta={esquerda:[passo,0],direita:[-passo,0],cima:[0,passo],baixo:[0,-passo],centro:[-state.panX,-state.panY]}[direcao];
 if(!delta)return;
 const x=state.panX+delta[0],y=state.panY+delta[1];
 const pan=modulo.limitarPan(x*tela.width/Math.max(1,tela.clientWidth),y*tela.height/Math.max(1,tela.clientHeight),d.w*escala,d.h*escala,tela.width,tela.height);
 if(!pan)return;
 state.panX=pan.x*tela.clientWidth/tela.width;state.panY=pan.y*tela.clientHeight/tela.height;
 paint();atualizar();
}
painel.querySelector(".mapa-comandos").addEventListener("click",e=>{const botao=e.target.closest("[data-direcao]");if(botao)deslocar(botao.dataset.direcao)});
mini.addEventListener("keydown",e=>{
 const direcao={ArrowLeft:"esquerda",ArrowRight:"direita",ArrowUp:"cima",ArrowDown:"baixo",Home:"centro"}[e.key];
 if(!direcao)return;e.preventDefault();deslocar(direcao);
});
window.addEventListener("flow-zoom-alterado",atualizar);
document.getElementById("zoom-in")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("zoom-out")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("zoom-label")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("fit-toggle")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("monitor")?.addEventListener("change",()=>requestAnimationFrame(atualizar));
new ResizeObserver(atualizar).observe(tela);
// Atualização sob demanda e somente quando o vídeo muda: evita polling constante.\nwindow.addEventListener("flow-captura-medida",()=>{if(!painel.hidden)atualizar()});\nwindow.addEventListener("flow-exploracao-alterada",atualizar);
})();
