(()=>{
"use strict";
const flow=window.FlowTutumNext;if(!flow)return;
const {state,paint}=flow,viewer=document.getElementById("viewer"),tela=document.getElementById("screen");
const acao=document.createElement("button");acao.type="button";acao.className="mapa-acao";acao.textContent="Mapa da tela";acao.hidden=true;acao.setAttribute("aria-expanded","false");
const painel=document.createElement("section");painel.className="mapa-navegacao";painel.hidden=true;painel.setAttribute("aria-label","Navegação pela imagem ampliada");
painel.innerHTML='<div class="mapa-titulo"><span>Visão geral</span><button type="button" aria-label="Fechar mapa">×</button></div><canvas width="320" height="188" aria-label="Arraste para navegar pela tela ampliada" tabindex="0"></canvas>';
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
 if(!modulo||!geometria)return;
 const rect=mini.getBoundingClientRect(),x=(evento.clientX-rect.left)*mini.width/rect.width,y=(evento.clientY-rect.top)*mini.height/rect.height;
 const ponto=modulo.normalizarPonto(x,y,geometria),d=dimensoes();if(!ponto||!d)return;
 const escala=Math.min(tela.width/d.w,tela.height/d.h)*state.zoom;
 const pan=modulo.posicaoPan(ponto.x,ponto.y,d.w*escala,d.h*escala,tela.width,tela.height);
 state.panX=pan.x*tela.clientWidth/tela.width;state.panY=pan.y*tela.clientHeight/tela.height;
 paint();atualizar();
}
acao.addEventListener("click",()=>{painel.hidden=!painel.hidden;acao.setAttribute("aria-expanded",String(!painel.hidden));atualizar()});
fechar.addEventListener("click",()=>{painel.hidden=true;acao.setAttribute("aria-expanded","false");acao.focus()});
mini.addEventListener("pointerdown",e=>{e.preventDefault();arrastando=true;mini.setPointerCapture(e.pointerId);navegar(e)});
mini.addEventListener("pointermove",e=>{if(arrastando){e.preventDefault();navegar(e)}});
mini.addEventListener("pointerup",()=>arrastando=false);
mini.addEventListener("pointercancel",()=>arrastando=false);
mini.addEventListener("keydown",e=>{if(!["ArrowLeft","ArrowRight","ArrowUp","ArrowDown"].includes(e.key))return;e.preventDefault();const passo=24;state.panX+=e.key==="ArrowLeft"?passo:e.key==="ArrowRight"?-passo:0;state.panY+=e.key==="ArrowUp"?passo:e.key==="ArrowDown"?-passo:0;paint();atualizar()});
window.addEventListener("flow-zoom-alterado",atualizar);
document.getElementById("zoom-in")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("zoom-out")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("zoom-label")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("fit-toggle")?.addEventListener("click",()=>requestAnimationFrame(atualizar));
document.getElementById("monitor")?.addEventListener("change",()=>requestAnimationFrame(atualizar));
new ResizeObserver(atualizar).observe(tela);
setInterval(()=>{if(!painel.hidden)atualizar();else if(!acao.hidden)atualizar()},700);
})();
