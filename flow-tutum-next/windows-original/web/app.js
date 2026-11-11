(() => {
"use strict";
const el=id=>document.getElementById(id);
const state={token:localStorage.getItem("flow-next-token")||"",info:null,monitor:0,quality:960,mode:"direct",zoom:1,panX:0,panY:0,fillMode:"fit",tapBehavior:"click",sensitivity:1.5,localSupported:false,
  image:null,ws:null,authenticated:false,approved:false,seq:Math.max(Date.now(),1),pointer:null,contacts:new Map(),
  cursor:{x:.5,y:.5},frameCount:0,at:Date.now(),frameError:null,stopped:false,videoActive:false,video:null,videoAbort:null,
  sessionReady:false,sessionMessage:"Verificando desktop Windows",videoStarting:false,videoLastAttempt:0};
const canvas=el("screen"),ctx=canvas.getContext("2d",{alpha:false,desynchronized:true});
function toast(text,warning=false){el("status-text").textContent=text;el("status").classList.toggle("ready",!warning);}
function showDialog(id){el(id).hidden=false}
function closeDialog(id){el(id).hidden=true}
async function api(url,opts={}) {
 const headers={...(opts.headers||{})};
 if(state.token)headers.Authorization="Bearer "+state.token;
 if(opts.body&&typeof opts.body==="object"){headers["Content-Type"]="application/json";opts.body=JSON.stringify(opts.body);}
 const response=await fetch(url,{...opts,headers,cache:"no-store"});
 if(!response.ok){let msg="HTTP "+response.status;try{msg=(await response.json()).error||msg}catch{}
    const error=new Error(msg);error.status=response.status;throw error;}
 return response;
}
function sizeCanvas(){const r=canvas.getBoundingClientRect();const scale=Math.min(window.devicePixelRatio||1,2);
 const w=Math.max(1,Math.round(r.width*scale)),h=Math.max(1,Math.round(r.height*scale));
 if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 paint();
}
function layout(){
 if(!state.image)return null;
 const w=canvas.width,h=canvas.height,im=state.image;
 const iw=state.videoActive?im.videoWidth:im.width,ih=state.videoActive?im.videoHeight:im.height;
 if(!iw||!ih)return null;
 const scale=(state.fillMode==="fill"?Math.max(w/iw,h/ih):Math.min(w/iw,h/ih))*state.zoom;
 const x=(w-iw*scale)/2+state.panX*(canvas.width/Math.max(1,canvas.clientWidth));
 const y=(h-ih*scale)/2+state.panY*(canvas.height/Math.max(1,canvas.clientHeight));
 return {x,y,w:iw*scale,h:ih*scale};
}
function paint(){
 if(!canvas.width||!canvas.height)return;
 ctx.fillStyle="#080b11";ctx.fillRect(0,0,canvas.width,canvas.height);
 const l=layout();if(!l)return;
 ctx.imageSmoothingEnabled=true;ctx.imageSmoothingQuality="high";
 ctx.drawImage(state.image,l.x,l.y,l.w,l.h);
 if(state.mode==="trackpad"&&state.approved){
  ctx.strokeStyle="#63d5c8";ctx.lineWidth=2*(canvas.width/Math.max(1,canvas.clientWidth));
  let cx=l.x+state.cursor.x*l.w,cy=l.y+state.cursor.y*l.h;
  ctx.beginPath();ctx.moveTo(cx-8,cy);ctx.lineTo(cx+8,cy);ctx.moveTo(cx,cy-8);ctx.lineTo(cx,cy+8);ctx.stroke();
 }
}
function pointAt(e) {
 const rect=canvas.getBoundingClientRect(),l=layout();if(!l)return null;
 const xp=(e.clientX-rect.left)*(canvas.width/rect.width),yp=(e.clientY-rect.top)*(canvas.height/rect.height);
 const x=(xp-l.x)/l.w,y=(yp-l.y)/l.h;
 return {x:Math.min(1,Math.max(0,x)),y:Math.min(1,Math.max(0,y)),inside:x>=0&&x<=1&&y>=0&&y<=1};
}
function wsSend(cmd){
 if(!state.approved||!state.authenticated||state.ws?.readyState!==WebSocket.OPEN)return false;
 state.seq=Math.max(Date.now(),state.seq+1);
 try {
  state.ws.send(JSON.stringify({seq:state.seq,screen:state.monitor,x:state.cursor.x,y:state.cursor.y,button:"left",...cmd}));
  return true;
 } catch {
  state.authenticated=false;
  toast("Conexão de controle interrompida",true);
  try { state.ws?.close(); } catch {}
  return false;
 }
}
function startSocket(){
 if(!state.token)return;
 try{state.ws?.close()}catch{}
 state.authenticated=false;
 const proto=location.protocol==="https:"?"wss":"ws";
 const ws=new WebSocket(proto+"://"+location.host+"/ws/input");
 state.ws=ws;
 ws.onopen=()=>{
  if(state.ws!==ws || state.stopped)return;
  try{ws.send(JSON.stringify({token:state.token,seq:0}))}
  catch{state.authenticated=false;try{ws.close()}catch{}}
 };
 ws.onmessage=event=>{
  if(state.ws!==ws || state.stopped)return;
  try{const data=JSON.parse(event.data);
   if(data.ready){state.authenticated=true;toast("Conectado");return;}
   if(data.ok===false){toast(data.error||"Comando recusado",true);state.pointer=null;}
   else if(data.ok===true&&data.cursor&&state.mode==="trackpad"&&data.cursor.screen===state.monitor){
     state.cursor={x:data.cursor.x,y:data.cursor.y};paint();
   }
  }catch{}
 };
 ws.onerror=()=>{if(state.ws===ws)toast("Canal de controle indisponível",true)};
 ws.onclose=()=>{
  if(state.ws!==ws)return;
  state.authenticated=false;
  if(!state.stopped)setTimeout(()=>{if(state.ws===ws&&!state.stopped)startSocket()},1600);
 };
}
async function loadInfo(){
 try{
  const resp=await api("/api/info");
  state.info=await resp.json();el("pc-name").textContent=state.info.device;
  el("conn-info").textContent=state.info.network;el("build").textContent="FLOW NEXT · "+state.info.version;
  el("monitor").replaceChildren();
  (state.info.monitors||[]).forEach(m=>{const option=document.createElement("option");option.value=m.index;option.textContent="Tela "+(m.index+1)+" · "+m.width+"×"+m.height;el("monitor").append(option)});
  el("caps").textContent="Vídeo: "+state.info.capabilities.codec+
    " · comandos via WebSocket · "+(state.info.localDirect?"Wi-Fi direto: ligado":"Autorização tradicional");
  await refreshPermission();startSocket();closeDialog("pair-dialog");
  if(state.localSupported&&!state.localToken)showDialog("discovery-dialog");
  toast(state.sessionReady?"Conectado":"Desktop aguardando usuário",!state.sessionReady);
  if(state.sessionReady&&state.info.capabilities.codec==="h264-fmp4")startVideo().catch(()=>{});
 }catch(error){
  if(error.status===401){
     state.token="";localStorage.removeItem("flow-next-token");
     showDialog(state.localSupported?"discovery-dialog":"pair-dialog");
  }
  else{toast("Servidor indisponível",true);}
 }
}
async function refreshPermission(){
 if(!state.token)return;
 try{
  const response=await api("/api/control/status"),data=await response.json();
  const wasReady=state.sessionReady;
  state.sessionReady=data.available===true;
  state.sessionMessage=data.session?.message||"Desktop indisponível";
  state.approved=!!data.approved&&state.sessionReady;
  state.localToken=!!data.localDirect;
  el("permission").classList.toggle("approved",state.approved);
  el("permission-title").textContent=!state.sessionReady?"Desktop Windows indisponível":
      state.approved?"Controle autorizado":"Somente visualização";
  el("permission-desc").textContent=!state.sessionReady?state.sessionMessage:
      state.approved?(data.localDirect?"Acesso direto pelo Wi-Fi da casa":
         "Toque na tela para controlar o Windows"):"Aprove a solicitação diretamente no PC.";
  el("request-control").hidden=state.approved;
  el("request-control").disabled=!state.sessionReady;
  el("open-keyboard").disabled=!state.approved;
  if(!state.approved)el("keyboard-sheet").hidden=true;
  if(wasReady&&!state.sessionReady){
    state.videoAbort?.abort();state.videoActive=false;state.image=null;
    el("empty").hidden=false;el("stream-state").textContent="Sessão desconectada";paint();
    toast("Aguardando sessão Windows",true);
  }else if(!wasReady&&state.sessionReady){
    toast("Desktop reconectado");
    if(state.info?.capabilities.codec==="h264-fmp4")startVideo().catch(()=>{});
  }
 }catch(error){
  state.sessionReady=false;
  if(error.status===401){
   state.token="";state.approved=false;state.authenticated=false;
   localStorage.removeItem("flow-next-token");
   try{state.ws?.close()}catch{}
   showDialog(state.localSupported?"discovery-dialog":"pair-dialog");
  }
 }
}
let frameRunning=false;
async function frameLoop() {
 if(frameRunning||state.stopped)return;frameRunning=true;
 while(!state.stopped) {
  if(!state.token||!state.info||document.hidden||state.videoActive||!state.sessionReady){
    await new Promise(r=>setTimeout(r,state.sessionReady?500:1500));continue;
  }
  // Recupera H.264 automaticamente apos falhas, mantendo JPEG como fallback.
  if(state.info.capabilities.codec==="h264-fmp4"&&!state.videoStarting&&
     Date.now()-state.videoLastAttempt>=15000)startVideo().catch(()=>{});
  const before=performance.now();
  try {
    const resp=await api("/api/frame?monitor="+state.monitor+"&width="+state.quality+"&t="+Date.now());
    const blob=await resp.blob();
    const bitmap=await createImageBitmap(blob);
    if(state.videoActive){bitmap.close();continue;}
    if(state.image?.close)state.image.close();
    state.image=bitmap;state.frameCount++;
    sizeCanvas();el("empty").hidden=true;
    const elapsed=Math.round(performance.now()-before);
    el("latency").textContent=elapsed+" ms";
    el("stream-state").textContent=bitmap.width+"×"+bitmap.height+" · "+(1000/Math.max(100,elapsed)).toFixed(1)+" FPS aprox.";
    state.frameError=null;
  }catch(error){if(state.frameError!==error.message)toast("Captura: "+error.message,true);state.frameError=error.message;
    el("empty").hidden=false;el("stream-state").textContent=error.status===409?
      "Sessão Windows desconectada":"Aguardando vídeo";}
  await new Promise(r=>setTimeout(r,state.frameError?800:Math.max(65,150-(performance.now()-before))));
 }
 frameRunning=false;
}
async function startVideo(){
 if(!state.token||!state.info||!state.sessionReady||state.videoActive||state.videoStarting||
    state.info.capabilities.codec!=="h264-fmp4")return;
 if(Date.now()-state.videoLastAttempt<15000)return;
 state.videoStarting=true;state.videoLastAttempt=Date.now();
 const mime='video/mp4; codecs="avc1.42E01F"';
 if(!window.MediaSource||!MediaSource.isTypeSupported(mime)){state.videoStarting=false;return;}
 state.videoAbort?.abort();const abort=new AbortController();state.videoAbort=abort;
 const video=document.createElement("video");video.muted=true;video.playsInline=true;video.autoplay=true;video.preload="auto";
 const media=new MediaSource();video.src=URL.createObjectURL(media);state.video=video;
 const buffer=new Promise((resolve,reject)=>{
  media.addEventListener("sourceopen",()=>{
   try{const sb=media.addSourceBuffer(mime);sb.mode="segments";resolve(sb)}
   catch(error){reject(error)}
  },{once:true});
 });
 try{
  const response=await api("/api/video?monitor="+state.monitor+"&width="+Math.min(1920,state.quality)+"&fps=12",{signal:abort.signal});
  if(!response.body)throw Error("Streaming indisponível");
  const sb=await buffer;const queue=[];
  function pump(){
   if(sb.updating||!queue.length||media.readyState!=="open")return;
   try{sb.appendBuffer(queue.shift())}catch(error){abort.abort();}
  }
  sb.addEventListener("updateend",()=>{
   try{
    if(video.buffered.length){
     const end=video.buffered.end(video.buffered.length-1);
     if(end-video.currentTime>1.2)video.currentTime=Math.max(0,end-.25);
    }
   }catch{}
   pump();
  });
  video.addEventListener("loadeddata",()=>{
   state.videoActive=true;state.image=video;el("empty").hidden=true;
   el("stream-state").textContent="H.264 · 12 FPS";el("latency").textContent="Vídeo";
   video.play().catch(()=>{});
   const animate=()=>{if(state.videoActive&&!abort.signal.aborted){paint();requestAnimationFrame(animate)}};animate();
  },{once:true});
  const reader=response.body.getReader();
  while(!abort.signal.aborted){
   const {value,done}=await reader.read();if(done)break;
   if(queue.length>40)throw Error("Atraso excessivo no vídeo");
   if(value&&value.length){queue.push(value);pump();}
  }
  throw Error("Fluxo encerrado");
 }catch(error){
  if(!abort.signal.aborted)console.warn("H264 indisponível; voltando para JPEG",error);
 }finally{
  state.videoStarting=false;
  abort.abort();if(state.videoActive){state.videoActive=false;state.image=null;el("empty").hidden=false;}
  try{video.pause();URL.revokeObjectURL(video.src)}catch{}
 }
}
async function syncCursor(){
 if(!state.approved||!state.sessionReady||state.mode!=="trackpad"||state.pointer||document.hidden)return;
 try{
  const response=await api("/api/cursor"),data=await response.json(),cursor=data.cursor;
  if(cursor&&cursor.screen===state.monitor){
    state.cursor={x:cursor.x,y:cursor.y};paint();
  }
 }catch{}
}
function setMode(mode){
 state.mode=mode;state.pointer=null;state.contacts.clear();
 el("viewer").hidden=mode==="trackpad";
 el("touchpad-layout").hidden=mode!=="trackpad";
 document.querySelector(".shell").classList.toggle("touchpad-mode",mode==="trackpad");
 if(mode!=="trackpad")requestAnimationFrame(sizeCanvas);
 el("mode-direct").classList.toggle("selected",mode==="direct");
 el("mode-trackpad").classList.toggle("selected",mode==="trackpad");
 el("hint").textContent=mode==="direct"?"Toque para clicar • deslize para arrastar • dois dedos para rolar":
  "Deslize para mover o cursor • toque para clicar • dois dedos para rolar";
 paint();
}
canvas.addEventListener("pointerdown",e=>{
 if(!state.approved||!state.authenticated)return;
 e.preventDefault();canvas.setPointerCapture(e.pointerId);
 state.contacts.set(e.pointerId,{x:e.clientX,y:e.clientY});
 if(state.contacts.size>1){if(state.pointer?.drag)wsSend({phase:"cancel"});state.pointer=null;return;}
 const at=pointAt(e);if(!at||(!at.inside&&state.mode==="direct"))return;
 state.pointer={id:e.pointerId,startX:e.clientX,startY:e.clientY,lastX:e.clientX,lastY:e.clientY,at:Date.now(),point:at,drag:false};
 if(state.mode==="trackpad")state.cursor={...state.cursor};
});
canvas.addEventListener("pointermove",e=>{
 if(!state.contacts.has(e.pointerId))return;
 e.preventDefault();
 const previous=state.contacts.get(e.pointerId);
 state.contacts.set(e.pointerId,{x:e.clientX,y:e.clientY});
 if(state.contacts.size>=2){
   const all=[...state.contacts.values()],a=all[0],b=all[1];
   const midX=(a.x+b.x)/2,midY=(a.y+b.y)/2;
   if(!state.gesture)state.gesture={distance:Math.max(1,Math.hypot(a.x-b.x,a.y-b.y)),
     x:midX,y:midY,zoom:state.zoom};
   const distance=Math.hypot(a.x-b.x,a.y-b.y);
   if(Math.abs(distance-state.gesture.distance)>14){
      state.zoom=Math.max(1,Math.min(5,state.gesture.zoom*distance/state.gesture.distance));
      paint();
   }else if(state.zoom>1||state.fillMode==="fill"){
      const limits=canvas.getBoundingClientRect();
      state.panX=Math.max(-limits.width*1.6,Math.min(limits.width*1.6,state.panX+(midX-state.gesture.x)));
      state.panY=Math.max(-limits.height*1.6,Math.min(limits.height*1.6,state.panY+(midY-state.gesture.y)));
      paint();
   }else{
      const amount=Math.round((state.gesture.y-midY)*5);
      if(Math.abs(amount)>5)wsSend({phase:"wheel",wheel:amount});
   }
   state.gesture.x=midX;state.gesture.y=midY;
   return;
 }
 const p=state.pointer;if(!p||p.id!==e.pointerId)return;
 const distance=Math.hypot(e.clientX-p.startX,e.clientY-p.startY);
 if(state.mode==="trackpad"){
  const r=canvas.getBoundingClientRect();
  state.cursor={x:Math.max(0,Math.min(1,state.cursor.x+(e.clientX-p.lastX)/r.width*1.4)),
    y:Math.max(0,Math.min(1,state.cursor.y+(e.clientY-p.lastY)/r.height*1.4))};
  if(distance>7){p.drag=true;wsSend({phase:"move",x:state.cursor.x,y:state.cursor.y});paint();}
 }else{
  const at=pointAt(e);
  if(at&&distance>7){
    if(!p.drag && state.tapBehavior==="click"){p.drag=wsSend({phase:"down",x:p.point.x,y:p.point.y});}
    state.cursor={x:at.x,y:at.y};
    if(p.drag||state.tapBehavior==="move")wsSend({phase:"move",x:at.x,y:at.y});
  }
 }
 p.lastX=e.clientX;p.lastY=e.clientY;
});
canvas.addEventListener("pointerup",e=>{
 if(!state.contacts.has(e.pointerId))return;
 e.preventDefault();state.contacts.delete(e.pointerId);
 if(state.contacts.size){state.pointer=null;return}
 state.gesture=null;
 const p=state.pointer;state.pointer=null;
 if(!p||p.id!==e.pointerId)return;
 if(p.drag&&state.mode==="direct"){
   const at=pointAt(e)||p.point;wsSend({phase:"up",x:at.x,y:at.y});return;
 }
 if(p.drag&&state.mode==="trackpad")return;
 const at=state.mode==="trackpad"?state.cursor:pointAt(e)||p.point;
 if(at) {state.cursor={x:at.x,y:at.y};
   const button=Date.now()-p.at>650?"right":"left";
   if(state.mode!=="direct"||state.tapBehavior==="click"||button==="right")
      wsSend({phase:"tap",x:at.x,y:at.y,button});
   else wsSend({phase:"move",x:at.x,y:at.y});
 }
});
const cancel=e=>{state.contacts.delete(e.pointerId);state.pointer=null;state.gesture=null;wsSend({phase:"cancel"});};
canvas.addEventListener("pointercancel",cancel);canvas.addEventListener("lostpointercapture",e=>{if(state.contacts.has(e.pointerId))cancel(e)});
canvas.addEventListener("contextmenu",e=>e.preventDefault());
canvas.addEventListener("wheel",e=>{e.preventDefault();if(e.ctrlKey){state.zoom=Math.max(1,Math.min(4,state.zoom-e.deltaY*.003));paint()}else wsSend({phase:"wheel",wheel:Math.round(-e.deltaY*2)});},{passive:false});
el("mode-direct").onclick=()=>setMode("direct");el("mode-trackpad").onclick=()=>setMode("trackpad");
el("right-click").onclick=()=>wsSend({phase:"tap",x:state.cursor.x,y:state.cursor.y,button:"right"});
el("open-keyboard").onclick=()=>{
 if(!state.approved)return;
 el("keyboard-sheet").hidden=!el("keyboard-sheet").hidden;
 if(!el("keyboard-sheet").hidden)el("keyboard-text").focus();
};
el("keyboard-close").onclick=()=>el("keyboard-sheet").hidden=true;
el("keyboard-form").onsubmit=event=>{
 event.preventDefault();
 wsSend({phase:"key",keys:"enter"});
 el("keyboard-text").focus();
};
document.querySelectorAll("[data-key]").forEach(button=>button.addEventListener("click",()=>{
 if(state.approved)wsSend({phase:"key",keys:button.dataset.key});
}));
el("monitor").onchange=e=>{state.videoAbort?.abort();state.videoActive=false;state.monitor=Number(e.target.value);state.image=null;state.zoom=1;state.panX=0;state.panY=0;paint();if(state.info?.capabilities.codec==="h264-fmp4")setTimeout(startVideo,300)};
el("fullscreen").onclick=()=>{if(document.fullscreenElement)document.exitFullscreen();else el("viewer").requestFullscreen?.().catch(()=>{});};
el("open-settings").onclick=()=>showDialog("settings-dialog");
el("close-settings").onclick=()=>closeDialog("settings-dialog");
el("quality").onchange=e=>{state.quality=Number(e.target.value);state.videoAbort?.abort();state.videoActive=false;state.image=null;if(state.info?.capabilities.codec==="h264-fmp4")setTimeout(startVideo,300);};
el("disconnect").onclick=async()=>{
 state.stopped=true;state.videoAbort?.abort();state.ws?.close();
 try{
  await api("/api/unpair",{method:"POST",body:{},signal:AbortSignal.timeout(2000)});
 }catch(error){console.warn("Falha ao revogar o pareamento no servidor.",error);}
 state.token="";localStorage.removeItem("flow-next-token");location.reload();
};
el("request-control").onclick=async()=>{try{await api("/api/control/request",{method:"POST",body:{}});toast("Aprove o pedido no Windows")}catch(e){toast(e.message,true)}};
el("pair-form").onsubmit=async e=>{
 e.preventDefault();el("pair-error").textContent="";
 try{const r=await api("/api/pair",{method:"POST",body:{pin:el("pin").value,name:el("device").value}});
  const data=await r.json();state.token=data.token;localStorage.setItem("flow-next-token",state.token);await loadInfo();
 }catch(err){el("pair-error").textContent="Não foi possível parear. Verifique o código.";}
};
new ResizeObserver(sizeCanvas).observe(canvas);window.addEventListener("resize",sizeCanvas);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)sizeCanvas()});
setInterval(refreshPermission,3000);
setInterval(syncCursor,1500);
window.FlowTutumNext={state,wsSend,paint,sizeCanvas,setMode,api,loadInfo,toast,showDialog,closeDialog};
(async()=>{try{
 const response=await fetch("/api/health",{cache:"no-store"});
 if(!response.ok)throw Error();
 const health=await response.json();
 state.localSupported=!!health.localDirect;
 if(state.token)await loadInfo();
 else if(state.localSupported){showDialog("discovery-dialog");toast("Computador encontrado");}
 else{showDialog("pair-dialog");toast("Pronto para parear");}
 frameLoop();}
catch{toast("Servidor indisponível",true);showDialog("pair-dialog");}})();
})();
