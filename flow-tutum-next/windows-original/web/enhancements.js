(()=>{
"use strict";
const flow=window.FlowTutumNext;
if(!flow)return;
const {state,wsSend,paint,sizeCanvas,setMode,api,loadInfo,toast,closeDialog}=flow;
const el=id=>document.getElementById(id);
const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));
const zoomText=()=>{
 const label=el("zoom-label");
 if(label)label.textContent=state.zoom===1&&state.fillMode==="fit"?"Ajustar":Math.round(state.zoom*100)+"%";
};
function setZoom(value){
 state.zoom=clamp(value,1,5);
 if(state.zoom===1){state.panX=0;state.panY=0}
 zoomText();paint();
}
const explorar=el("explore-toggle");
function atualizarExploracao(){
 explorar.setAttribute("aria-pressed",String(state.exploring));
 explorar.textContent=state.exploring?"Controlar":"Explorar";
 explorar.setAttribute("aria-label",state.exploring?"Voltar ao controle remoto":"Explorar imagem sem controlar o computador");
 el("viewer").classList.toggle("exploring",state.exploring);
 el("screen").setAttribute("aria-label",state.exploring?"Arraste para explorar a imagem, sem enviar comandos":"Toque ou deslize para controlar");
 el("hint").textContent=state.exploring?"Modo explorar: arraste a tela ou use dois dedos para ampliar. Nenhum clique será enviado.":"Toque para clicar • deslize para arrastar • dois dedos para rolar";
}
window.addEventListener("flow-exploracao-alterada",atualizarExploracao);
explorar.onclick=()=>{
 if(state.pointer?.drag)wsSend({phase:"cancel"});
 state.pointer=null;state.contacts.clear();state.gesture=null;
 state.exploring=!state.exploring;
 atualizarExploracao();
};
el("zoom-in").onclick=()=>setZoom(state.zoom*1.35);
el("zoom-out").onclick=()=>setZoom(state.zoom/1.35);
el("zoom-label").onclick=()=>{state.fillMode="fit";setZoom(1)};
el("fit-toggle").onclick=()=>{
 state.fillMode=state.fillMode==="fit"?"fill":"fit";
 el("fit-toggle").textContent=state.fillMode==="fill"?"Enquadrar":"Preencher";
 state.panX=0;state.panY=0;zoomText();paint();
};
el("touch-behavior").value=localStorage.getItem("flow-touch-behavior")||"click";
state.tapBehavior=el("touch-behavior").value;
el("touch-behavior").onchange=e=>{
 state.tapBehavior=e.target.value;
 localStorage.setItem("flow-touch-behavior",state.tapBehavior);
};
el("pointer-sensitivity").value=localStorage.getItem("flow-pointer-speed")||"1.5";
state.sensitivity=Number(el("pointer-sensitivity").value);
el("pointer-sensitivity").oninput=e=>{
 state.sensitivity=Number(e.target.value);
 localStorage.setItem("flow-pointer-speed",String(state.sensitivity));
};
el("connect-local").onclick=async()=>{
 const button=el("connect-local");
 button.disabled=true;el("local-connect-error").textContent="";
 const previous=state.token;state.token="";
 try{
  const result=await api("/api/local-connect",{method:"POST"});
  const data=await result.json();
  if(!data.token)throw Error("Nenhum acesso foi liberado");
  state.token=data.token;
  localStorage.setItem("flow-next-token",data.token);
  closeDialog("discovery-dialog");
  await loadInfo();
  toast("Conectado no Wi-Fi");
 }catch(error){
  state.token=previous;
  el("local-connect-error").textContent=error.message||"Nao foi possivel conectar";
  toast("Wi-Fi indisponivel",true);
 }finally{button.disabled=false}
};
let hold=false;
const mousePosition=()=>({x:state.cursor.x,y:state.cursor.y});
function releaseHold(){
 if(!hold)return;
 wsSend({phase:"up",button:"left",...mousePosition()});
 hold=false;el("pad-hold").setAttribute("aria-pressed","false");
 el("pad-hold").classList.remove("holding");
}
el("pad-hold").onclick=()=>{
 if(hold){releaseHold();return}
 if(!state.approved)return;
 hold=wsSend({phase:"down",button:"left",...mousePosition()});
 if(hold){el("pad-hold").setAttribute("aria-pressed","true");el("pad-hold").classList.add("holding")}
};
document.querySelectorAll("[data-mouse]").forEach(button=>{
 button.addEventListener("click",()=>{
  if(hold)releaseHold();
  wsSend({phase:"tap",button:button.dataset.mouse,...mousePosition()});
 });
});
el("pad-double").onclick=()=>{
 if(hold)releaseHold();
 if(wsSend({phase:"tap",button:"left",...mousePosition()}))
  setTimeout(()=>wsSend({phase:"tap",button:"left",...mousePosition()}),85);
};
el("pad-horizontal-left").onclick=()=>wsSend({phase:"hwheel",wheel:-240});
el("pad-horizontal-right").onclick=()=>wsSend({phase:"hwheel",wheel:240});
// Central de gestos: instruções acessíveis e retorno tátil opcional, sem comandos remotos.
const barra=el("touchpad-layout").querySelector(".pad-toolbar");
const ajuda=document.createElement("button");
ajuda.type="button";ajuda.id="ajuda-gestos";ajuda.textContent="Gestos ?";
ajuda.setAttribute("aria-expanded","false");
ajuda.setAttribute("aria-controls","painel-gestos");
barra.append(ajuda);
const painel=document.createElement("section");
painel.id="painel-gestos";painel.className="painel-gestos";painel.hidden=true;
painel.innerHTML='<div class="gestos-cabecalho"><strong>Como controlar</strong><button type="button" id="fechar-gestos" aria-label="Fechar ajuda">×</button></div><div class="gestos-grade"><p><b>☝ Um dedo</b><span>Deslize para mover o ponteiro. Toque para clicar.</span></p><p><b>✌ Dois dedos</b><span>Deslize para rolar em qualquer direção. Toque para clique direito.</span></p><p><b>◉ Segurar</b><span>Ative “Segurar e arrastar” e mova o dedo para arrastar.</span></p><p><b>⌨ Teclado</b><span>Abra o teclado para digitar no Windows.</span></p></div><label class="gestos-vibracao"><input id="vibracao-gestos" type="checkbox"> Vibração discreta ao clicar</label><small>Os gestos só controlam o computador durante uma sessão autorizada.</small>';
barra.insertAdjacentElement("afterend",painel);
const vibracao=el("vibracao-gestos");
vibracao.checked=localStorage.getItem("flow-vibracao-gestos")==="sim";
vibracao.addEventListener("change",()=>localStorage.setItem("flow-vibracao-gestos",vibracao.checked?"sim":"nao"));
const pulsar=()=>{if(vibracao.checked&&state.approved&&navigator.vibrate)navigator.vibrate(12)};
function abrirAjuda(aberto){painel.hidden=!aberto;ajuda.setAttribute("aria-expanded",String(aberto));if(aberto)el("fechar-gestos").focus()}
ajuda.addEventListener("click",()=>abrirAjuda(painel.hidden));
el("fechar-gestos").addEventListener("click",()=>{abrirAjuda(false);ajuda.focus()});
painel.addEventListener("keydown",e=>{if(e.key==="Escape"){abrirAjuda(false);ajuda.focus()}});
const retorno=document.createElement("output");
retorno.id="retorno-gesto";retorno.className="retorno-gesto";retorno.setAttribute("aria-live","off");retorno.textContent="Pronto para controlar";
el("touchpad-area").append(retorno);
let retornoTimer=0;
function mostrarGesto(mensagem){retorno.textContent=mensagem;retorno.classList.add("ativo");clearTimeout(retornoTimer);retornoTimer=setTimeout(()=>retorno.classList.remove("ativo"),850)}
const pad=el("touchpad-area"),padContacts=new Map();
let padGestureMulti=false,padMoved=false,padDown=0,padLastCenter=null,padMultiScrolled=false;
const scrollThreshold=3;
function centroContatos(){
 const contatos=[...padContacts.values()];
 return {x:contatos.reduce((s,p)=>s+p.x,0)/contatos.length,
         y:contatos.reduce((s,p)=>s+p.y,0)/contatos.length};
}
function encerrarGesto(){
 padContacts.clear();padLastCenter=null;padGestureMulti=false;padMultiScrolled=false;padMoved=false;
}
pad.addEventListener("pointerdown",event=>{
 if(!state.approved)return;
 event.preventDefault();pad.setPointerCapture(event.pointerId);
 padContacts.set(event.pointerId,{x:event.clientX,y:event.clientY,moved:0});
 if(padContacts.size===1){padMoved=false;padGestureMulti=false;padMultiScrolled=false;padDown=Date.now();padLastCenter=null;mostrarGesto(hold?"Arrastando":"Mover ponteiro")}
 if(padContacts.size===2){padGestureMulti=true;padLastCenter=centroContatos();mostrarGesto("Dois dedos · rolar")}
 if(padContacts.size>2){padGestureMulti=true;padLastCenter=null}
});
pad.addEventListener("pointermove",event=>{
 if(!padContacts.has(event.pointerId))return;
 event.preventDefault();
 const previous=padContacts.get(event.pointerId);
 const deltaX=event.clientX-previous.x,deltaY=event.clientY-previous.y;
 previous.x=event.clientX;previous.y=event.clientY;
 previous.moved+=Math.abs(deltaX)+Math.abs(deltaY);
 if(padContacts.size>=2){
  // Usa o centro dos dois dedos: a rolagem não duplica quando ambos se movem.
  if(padContacts.size!==2)return;
  const centro=centroContatos();
  if(padLastCenter){
   const dx=centro.x-padLastCenter.x,dy=centro.y-padLastCenter.y;
   if(Math.abs(dy)>=scrollThreshold){
    if(wsSend({phase:"wheel",wheel:Math.round(-dy*7)})){padMultiScrolled=true;mostrarGesto("Rolagem vertical")}
    padLastCenter.y=centro.y;
   }
   if(Math.abs(dx)>=scrollThreshold){
    if(wsSend({phase:"hwheel",wheel:Math.round(-dx*7)})){padMultiScrolled=true;mostrarGesto("Rolagem horizontal")}
    padLastCenter.x=centro.x;
   }
  }else padLastCenter=centro;
  return;
 }
 // Nunca mover o cursor após o fim de um gesto de dois dedos.
 if(padGestureMulti)return;
 if(Math.abs(deltaX)+Math.abs(deltaY)<0.75)return;
 const bounds=pad.getBoundingClientRect();
 state.cursor.x=clamp(state.cursor.x+deltaX/Math.max(1,bounds.width)*state.sensitivity,0,1);
 state.cursor.y=clamp(state.cursor.y+deltaY/Math.max(1,bounds.height)*state.sensitivity,0,1);
 padMoved=padMoved||previous.moved>9;
 wsSend({phase:"move",...mousePosition()});
});
pad.addEventListener("pointerup",event=>{
 if(!padContacts.has(event.pointerId))return;
 event.preventDefault();const contact=padContacts.get(event.pointerId);
 padContacts.delete(event.pointerId);
 padLastCenter=null;
 if(padContacts.size>0)return;
 const duracao=Date.now()-padDown;
 // Dois dedos sem rolagem = clique direito; rolagem nunca vira clique.
 if(padGestureMulti){
  if(!padMultiScrolled&&duracao<550&&wsSend({phase:"tap",button:"right",...mousePosition()})){mostrarGesto("Clique direito");pulsar()}
 }else if(!padMoved&&contact.moved<9&&!hold){
  if(wsSend({phase:"tap",button:duracao>700?"right":"left",...mousePosition()})){mostrarGesto(duracao>700?"Clique direito":"Clique esquerdo");pulsar()}
 }
 encerrarGesto();
});
pad.addEventListener("pointercancel",()=>{encerrarGesto();releaseHold()});
pad.addEventListener("lostpointercapture",event=>{
 if(padContacts.has(event.pointerId)){encerrarGesto();releaseHold()}
});
pad.addEventListener("contextmenu",e=>e.preventDefault());
const rail=el("pad-scroll");let railLast=null;
rail.addEventListener("pointerdown",e=>{
 e.preventDefault();rail.setPointerCapture(e.pointerId);railLast=e.clientY;
});
rail.addEventListener("pointermove",e=>{
 if(railLast===null)return;
 e.preventDefault();const delta=e.clientY-railLast;
 if(Math.abs(delta)>2){wsSend({phase:"wheel",wheel:Math.round(-delta*9)});railLast=e.clientY;}
});
rail.addEventListener("pointerup",()=>railLast=null);
rail.addEventListener("pointercancel",()=>railLast=null);
el("pad-keyboard").onclick=()=>el("open-keyboard").click();
el("mode-direct").addEventListener("click",()=>{releaseHold();atualizarExploracao();setTimeout(sizeCanvas,40)});
el("mode-trackpad").addEventListener("click",()=>zoomText());
let prior="",composing=false;
const field=el("keyboard-text");
field.spellcheck=false;
function mirrorTyping(){
 if(!state.approved)return;
 const next=field.value;
 let common=0;
 while(common<Math.min(prior.length,next.length) && prior[common]===next[common])common++;
 const deletes=prior.length-common;
 if(deletes>80){prior=next;return}
 for(let i=0;i<deletes;i++)wsSend({phase:"key",keys:"backspace"});
 const insertion=next.slice(common);
 if(insertion){
  const pieces=insertion.split("\n");
  for(let i=0;i<pieces.length;i++){
   if(pieces[i])wsSend({phase:"text",text:pieces[i]});
   if(i<pieces.length-1)wsSend({phase:"key",keys:"enter"});
  }
 }
 prior=next;
 if(field.value.length>1200){field.value="";prior=""}
}
field.addEventListener("compositionstart",()=>composing=true);
field.addEventListener("compositionend",()=>{composing=false;mirrorTyping()});
field.addEventListener("input",()=>{if(!composing)mirrorTyping()});
window.addEventListener("blur",()=>releaseHold());
document.addEventListener("visibilitychange",()=>{if(document.hidden)releaseHold()});
window.addEventListener("orientationchange",()=>setTimeout(sizeCanvas,320));
zoomText();
})();
