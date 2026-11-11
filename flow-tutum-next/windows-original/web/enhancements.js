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
window.addEventListener("flow-zoom-alterado",zoomText);
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
// Perfis de uso: ajustes locais e reversíveis, sem alterar a autorização do Windows.
const perfisControle={
 "precisao":{nome:"Precisão",sensibilidade:0.8,toque:"click",descricao:"Movimentos delicados para editar textos e planilhas."},
 "cotidiano":{nome:"Cotidiano",sensibilidade:1.5,toque:"click",descricao:"Equilíbrio entre velocidade e controle."},
 "rapido":{nome:"Rápido",sensibilidade:2.5,toque:"click",descricao:"Deslocamento amplo com menos movimentos."}
};
const perfilContainer=document.createElement("section");
perfilContainer.className="perfis-controle";
perfilContainer.setAttribute("aria-label","Perfis do touchpad");
perfilContainer.innerHTML='<div class="perfis-titulo"><strong>Seu jeito de controlar</strong><small>Toque para trocar o perfil</small></div><div class="perfis-opcoes" role="group" aria-label="Escolha o perfil"><button type="button" data-perfil="precisao">Precisão</button><button type="button" data-perfil="cotidiano">Cotidiano</button><button type="button" data-perfil="rapido">Rápido</button></div><p id="perfil-descricao" role="status"></p>';
el("touchpad-layout").insertBefore(perfilContainer,el("touchpad-layout").querySelector(".pad-interaction"));
function atualizarPerfilAtivo(chave){
 perfilContainer.querySelectorAll("[data-perfil]").forEach(botao=>{
  const ativo=botao.dataset.perfil===chave;
  botao.classList.toggle("selecionado",ativo);
  botao.setAttribute("aria-pressed",String(ativo));
 });
 el("perfil-descricao").textContent=perfisControle[chave]?.descricao||"Ajustes personalizados.";
}
function aplicarPerfil(chave){
 const perfil=perfisControle[chave];
 if(!perfil)return;
 state.sensitivity=perfil.sensibilidade;
 state.tapBehavior=perfil.toque;
 el("pointer-sensitivity").value=String(perfil.sensibilidade);
 el("touch-behavior").value=perfil.toque;
 localStorage.setItem("flow-pointer-speed",String(perfil.sensibilidade));
 localStorage.setItem("flow-touch-behavior",perfil.toque);
 localStorage.setItem("flow-perfil-controle",chave);
 atualizarPerfilAtivo(chave);
}
perfilContainer.querySelectorAll("[data-perfil]").forEach(botao=>botao.addEventListener("click",()=>aplicarPerfil(botao.dataset.perfil)));
const perfilSalvo=localStorage.getItem("flow-perfil-controle");
if(perfilSalvo&&perfisControle[perfilSalvo])aplicarPerfil(perfilSalvo);
else atualizarPerfilAtivo("personalizado");
function marcarPersonalizado(){
 localStorage.setItem("flow-perfil-controle","personalizado");
 atualizarPerfilAtivo("personalizado");
}
el("pointer-sensitivity").addEventListener("input",marcarPersonalizado);
el("touch-behavior").addEventListener("change",marcarPersonalizado);
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
// Painel de diagnóstico usa somente medições reais e endpoints já existentes.
const diagnostico=document.createElement("section");
diagnostico.id="diagnostico-conexao";
diagnostico.className="diagnostico-conexao";
diagnostico.hidden=true;
diagnostico.setAttribute("aria-label","Diagnóstico da conexão");
diagnostico.innerHTML='<div class="diagnostico-topo"><strong>Saúde da conexão</strong><button type="button" id="fechar-diagnostico" aria-label="Fechar diagnóstico">×</button></div><div class="diagnostico-grade"><div><span>Controle</span><strong id="diag-controle">Verificando</strong></div><div><span>Permissão</span><strong id="diag-permissao">Verificando</strong></div><div><span>Desktop</span><strong id="diag-desktop">Verificando</strong></div><div><span>Vídeo</span><strong id="diag-video">Verificando</strong></div><div><span>Último quadro</span><strong id="diag-latencia">Sem medida</strong></div><div><span>Rede</span><strong id="diag-rede">Não identificada</strong></div></div><p id="diag-orientacao" role="status">Os indicadores mostram o estado real da sessão.</p><button type="button" id="atualizar-diagnostico">Verificar novamente</button>';
const barraSuperior=document.querySelector(".topbar");
const botaoDiagnostico=document.createElement("button");
botaoDiagnostico.id="abrir-diagnostico";
botaoDiagnostico.type="button";
botaoDiagnostico.className="icon-button";
botaoDiagnostico.textContent="ⓘ";
botaoDiagnostico.title="Diagnóstico da conexão";
botaoDiagnostico.setAttribute("aria-label","Abrir diagnóstico da conexão");
botaoDiagnostico.setAttribute("aria-expanded","false");
botaoDiagnostico.setAttribute("aria-controls","diagnostico-conexao");
barraSuperior.insertBefore(botaoDiagnostico,el("open-settings"));
document.querySelector(".main").prepend(diagnostico);
let intervaloDiagnostico=0;
function atualizarIndicadores(){
 const texto=(id,valor)=>{el(id).textContent=valor};
 const conectado=state.authenticated&&state.ws?.readyState===WebSocket.OPEN;
 texto("diag-controle",conectado?"Conectado":"Desconectado");
 texto("diag-permissao",state.approved?"Autorizado":"Sem autorização");
 texto("diag-desktop",state.sessionReady?"Disponível":"Indisponível");
 texto("diag-video",state.videoActive?"H.264 ativo":state.image?"Quadros recebidos":"Sem imagem");
 const medida=el("latency").textContent.trim();
 texto("diag-latencia",/^\\d+ ms$/.test(medida)?medida+" (captura)":"Não medida");
 texto("diag-rede",state.info?.network||"Não identificada");
 let orientacao="Conexão pronta para uso.";
 if(!state.sessionReady)orientacao="Verifique se a sessão Windows da conta AS está ativa.";
 else if(!state.approved)orientacao="Aguarde a autorização de controle no computador.";
 else if(!conectado)orientacao="Canal de entrada indisponível. Verifique a rede e aguarde a reconexão.";
 else if(!state.image)orientacao="Controle conectado, mas ainda não há quadros de vídeo.";
 texto("diag-orientacao",orientacao);
}
function abrirDiagnostico(aberto){
 diagnostico.hidden=!aberto;
 botaoDiagnostico.setAttribute("aria-expanded",String(aberto));
 clearInterval(intervaloDiagnostico);
 if(aberto){atualizarIndicadores();intervaloDiagnostico=setInterval(atualizarIndicadores,1200);el("fechar-diagnostico").focus()}
 else botaoDiagnostico.focus();
}
botaoDiagnostico.onclick=()=>abrirDiagnostico(diagnostico.hidden);
el("fechar-diagnostico").onclick=()=>abrirDiagnostico(false);
diagnostico.addEventListener("keydown",evento=>{if(evento.key==="Escape")abrirDiagnostico(false)});
el("atualizar-diagnostico").onclick=async()=>{
 const botao=el("atualizar-diagnostico");
 if(botao.disabled)return;
 botao.disabled=true;botao.textContent="Verificando…";
 try{await loadInfo();atualizarIndicadores()}
 catch{el("diag-orientacao").textContent="Não foi possível consultar o servidor."}
 finally{botao.disabled=false;botao.textContent="Verificar novamente"}
};
document.addEventListener("visibilitychange",()=>{if(document.hidden){clearInterval(intervaloDiagnostico)}else if(!diagnostico.hidden){atualizarIndicadores();intervaloDiagnostico=setInterval(atualizarIndicadores,1200)}});

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
