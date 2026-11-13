(()=>{"use strict";
const canvas=document.getElementById("screen"),viewer=document.getElementById("viewer");
const pad=document.getElementById("touchpad-area");
if(!canvas||!viewer)return;
const estado=()=>window.FlowTutumNext?.state;
const indicador=document.createElement("div");
indicador.className="gesto-indicador";indicador.setAttribute("aria-hidden","true");
indicador.innerHTML='<span class="gesto-anel"></span><span class="gesto-ponto"></span>';
viewer.append(indicador);
const aviso=document.createElement("output");
aviso.className="gesto-estado";aviso.setAttribute("role","status");aviso.setAttribute("aria-live","polite");
aviso.textContent="Toque para controlar";viewer.append(aviso);
const ativos=new Map();let inicio=0,ultimo=0,ocultar=0;
const liberado=()=>{const s=estado();return !!(s?.approved&&s?.authenticated&&s?.sessionReady&&!s?.somenteVisualizacao)};
function mensagem(texto){if(aviso.textContent!==texto)aviso.textContent=texto;}
function posicionar(evento){const r=viewer.getBoundingClientRect();indicador.style.setProperty("--gesto-x",Math.max(0,Math.min(r.width,evento.clientX-r.left))+"px");indicador.style.setProperty("--gesto-y",Math.max(0,Math.min(r.height,evento.clientY-r.top))+"px");}
function atualizar(){const s=estado();if(!s)return;const explorar=!!s.exploring;
 viewer.classList.toggle("gesto-explorando",explorar);
 viewer.classList.toggle("gesto-sem-controle",!liberado()&&!explorar);
 if(!ativos.size)mensagem(explorar?"Explorar · sem cliques":liberado()?"Toque para controlar":"Controle indisponível");
}
canvas.addEventListener("pointerdown",e=>{
 const s=estado();if(!s)return;
 ativos.set(e.pointerId,{x:e.clientX,y:e.clientY,tempo:performance.now(),movimento:false});
 posicionar(e);inicio=performance.now();
 indicador.classList.add("visivel");indicador.classList.remove("concluido");
 if(ativos.size>1){indicador.classList.add("multiplo");mensagem("Dois dedos · ampliar ou navegar");}
 else {indicador.classList.remove("multiplo");mensagem(s.exploring?"Explorando sem clicar":liberado()?"Toque iniciado":"Somente visualização");}
});
canvas.addEventListener("pointermove",e=>{
 const p=ativos.get(e.pointerId);if(!p)return;
 if(Math.hypot(e.clientX-p.x,e.clientY-p.y)>8)p.movimento=true;
 posicionar(e);
 if(ativos.size>1)mensagem("Dois dedos · gesto em andamento");
 else if(estado()?.exploring)mensagem("Movendo a imagem · sem cliques");
 else if(p.movimento)mensagem(liberado()?"Movendo ou arrastando":"Movimento sem controle");
});
function terminar(e,cancelado){
 const p=ativos.get(e.pointerId);if(!p)return;
 const varios=ativos.size>1;ativos.delete(e.pointerId);
 if(!ativos.size){indicador.classList.remove("visivel","multiplo");indicador.classList.add("concluido");}
 if(cancelado){mensagem("Gesto cancelado");return;}
 if(varios||ativos.size){mensagem("Gesto com dois dedos concluído");return;}
 const s=estado();
 if(s?.exploring){mensagem("Imagem explorada · nenhum clique enviado");return;}
 if(!liberado()){mensagem("Controle indisponível · nenhum clique");return;}
 const duracao=performance.now()-p.tempo;
 mensagem(p.movimento?"Movimento concluído":duracao>=650?"Toque longo · botão direito":"Toque concluído");
 // Feedback discreto somente quando a sessão está autorizada; nunca substitui confirmação do servidor.
 if(!p.movimento&&navigator.vibrate&&document.visibilityState==="visible"&&duracao<1200){
  try{navigator.vibrate(12)}catch{}
 }
}
canvas.addEventListener("pointerup",e=>terminar(e,false));
canvas.addEventListener("pointercancel",e=>terminar(e,true));
canvas.addEventListener("lostpointercapture",e=>terminar(e,true));
window.addEventListener("flow-exploracao-alterada",atualizar);
document.getElementById("mode-direct")?.addEventListener("click",()=>queueMicrotask(atualizar));
document.getElementById("mode-trackpad")?.addEventListener("click",()=>queueMicrotask(atualizar));
document.addEventListener("visibilitychange",()=>{if(document.hidden){ativos.clear();indicador.classList.remove("visivel");}else atualizar();});
setInterval(()=>{if(!document.hidden)atualizar()},2000);
atualizar();
})();