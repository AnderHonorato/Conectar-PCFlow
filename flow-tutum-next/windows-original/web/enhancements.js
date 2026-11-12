(()=>{
"use strict";
const flow=window.FlowTutumNext;
if(!flow)return;
const {state,wsSend,paint,sizeCanvas,setMode,api,loadInfo,toast,closeDialog}=flow;
const el=id=>document.getElementById(id);
const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));
// O módulo puro é carregado sem bloquear o controle existente.
let motorGestos=null;
import('./gestos-configuraveis.mjs').then(modulo=>{motorGestos=modulo}).catch(()=>{});
let fatorRolagem=1;
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
// Preferência independente para rolagem: acessível no touchpad e persistente.
const ajusteRolagem=document.createElement('label');
ajusteRolagem.className='ajuste-rolagem';
ajusteRolagem.innerHTML='<span>Velocidade da rolagem <output id="valor-rolagem">1×</output></span><input id="velocidade-rolagem" type="range" min="0.5" max="3" step="0.25" value="1" aria-label="Velocidade da rolagem com dois dedos">';
perfilContainer.append(ajusteRolagem);
const campoRolagem=ajusteRolagem.querySelector('input'),valorRolagem=ajusteRolagem.querySelector('output');
try{const salvo=Number(localStorage.getItem('flow-velocidade-rolagem'));if(Number.isFinite(salvo)&&salvo>=0.5&&salvo<=3)fatorRolagem=salvo}catch{}
campoRolagem.value=String(fatorRolagem);valorRolagem.textContent=fatorRolagem+'×';
campoRolagem.addEventListener('input',()=>{fatorRolagem=clamp(Number(campoRolagem.value)||1,0.5,3);valorRolagem.textContent=fatorRolagem+'×';try{localStorage.setItem('flow-velocidade-rolagem',String(fatorRolagem))}catch{}});
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
    if(wsSend({phase:"wheel",wheel:(motorGestos?motorGestos.calcularRolagem(dy,fatorRolagem):Math.round(-dy*7*fatorRolagem))})){padMultiScrolled=true;mostrarGesto("Rolagem vertical")}
    padLastCenter.y=centro.y;
   }
   if(Math.abs(dx)>=scrollThreshold){
    if(wsSend({phase:"hwheel",wheel:(motorGestos?motorGestos.calcularRolagem(dx,fatorRolagem):Math.round(-dx*7*fatorRolagem))})){padMultiScrolled=true;mostrarGesto("Rolagem horizontal")}
    padLastCenter.x=centro.x;
   }
  }else padLastCenter=centro;
  return;
 }
 // Nunca mover o cursor após o fim de um gesto de dois dedos.
 if(padGestureMulti)return;
 if(Math.abs(deltaX)+Math.abs(deltaY)<0.75)return;
 const bounds=pad.getBoundingClientRect();
 const proximo=motorGestos?motorGestos.deslocarCursor(state.cursor,deltaX,deltaY,bounds.width,bounds.height,state.sensitivity):{x:clamp(state.cursor.x+deltaX/Math.max(1,bounds.width)*state.sensitivity,0,1),y:clamp(state.cursor.y+deltaY/Math.max(1,bounds.height)*state.sensitivity,0,1)};
 state.cursor.x=proximo.x;state.cursor.y=proximo.y;
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

// Central de mídia: ações reais, sem simular reprodução ou nível de volume.
const midia=document.createElement("section");
midia.className="central-midia";
midia.setAttribute("aria-label","Controles multimídia do Windows");
midia.innerHTML=`<div class="midia-cabecalho"><div><strong>Controle de mídia</strong><small>Comandos para o Windows conectado</small></div><span class="midia-estado" id="midia-estado" role="status" aria-live="polite">Aguardando autorização</span></div>
<div class="midia-botoes" role="group" aria-label="Reprodução"><button type="button" data-atalho-midia="midia-anterior" aria-label="Faixa anterior" title="Faixa anterior">⏮</button><button type="button" data-atalho-midia="midia-reproduzir" aria-label="Reproduzir ou pausar" title="Reproduzir ou pausar">⏯</button><button type="button" data-atalho-midia="midia-proxima" aria-label="Próxima faixa" title="Próxima faixa">⏭</button><button type="button" data-atalho-midia="midia-parar" aria-label="Parar reprodução" title="Parar reprodução">⏹</button></div>
<div class="midia-botoes midia-volume" role="group" aria-label="Volume"><button type="button" data-atalho-midia="volume-mudo">Silenciar</button><button type="button" data-atalho-midia="volume-menos">− Volume</button><button type="button" data-atalho-midia="volume-mais">+ Volume</button></div>
<p>Os botões enviam teclas multimídia ao Windows. A reprodução depende do aplicativo aberto no computador.</p>`;
el("touchpad-layout").appendChild(midia);
const estadoMidia=midia.querySelector("#midia-estado");
let ultimoComandoMidia=0;
function atualizarMidia(){
 const permitido=state.approved&&state.authenticated&&state.ws?.readyState===WebSocket.OPEN&&state.sessionReady;
 midia.querySelectorAll("[data-atalho-midia]").forEach(b=>b.disabled=!permitido);
 if(!permitido)estadoMidia.textContent="Aguardando conexão autorizada";
 else if(estadoMidia.textContent.startsWith("Aguardando"))estadoMidia.textContent="Pronto para controlar";
}
midia.addEventListener("click",e=>{
 const botao=e.target.closest("[data-atalho-midia]");
 if(!botao||botao.disabled)return;
 const agora=Date.now();
 if(agora-ultimoComandoMidia<180)return;
 ultimoComandoMidia=agora;
 const enviado=wsSend({phase:"key",keys:botao.dataset.atalhoMidia});
 estadoMidia.textContent=enviado?"Comando enviado: "+(botao.getAttribute("aria-label")||botao.textContent.trim()):"Não foi possível enviar";
 if(enviado){botao.classList.add("midia-acionado");setTimeout(()=>botao.classList.remove("midia-acionado"),220)}
 atualizarMidia();
});
setInterval(atualizarMidia,1000);
atualizarMidia();

// Controle de apresentação: teclado remoto autorizado, sem alterar o protocolo Windows.
const apresentacao=document.createElement("section");
apresentacao.className="controle-apresentacao";
apresentacao.setAttribute("aria-label","Controle de apresentação e leitura");
apresentacao.innerHTML=`<div class="apresentacao-cabecalho"><div><strong>Apresentação e leitura</strong><small>Slides, documentos e navegação sem mouse</small></div><span id="apresentacao-estado" role="status" aria-live="polite">Aguardando conexão</span></div>
<div class="apresentacao-grade" role="group" aria-label="Navegação por páginas">
<button type="button" data-tecla-apresentacao="left" aria-label="Slide anterior">◀ <span>Anterior</span></button>
<button type="button" data-tecla-apresentacao="right" aria-label="Próximo slide"><span>Próximo</span> ▶</button>
<button type="button" data-tecla-apresentacao="pageup">Página acima</button>
<button type="button" data-tecla-apresentacao="pagedown">Página abaixo</button>
<button type="button" data-tecla-apresentacao="home">Início</button>
<button type="button" data-tecla-apresentacao="end">Fim</button>
<button type="button" data-tecla-apresentacao="f5">Iniciar slides (F5)</button>
<button type="button" data-tecla-apresentacao="escape">Sair da apresentação</button>
</div><p>As teclas atuam no aplicativo em primeiro plano do Windows. A execução depende do programa aberto.</p>`;
el("touchpad-layout").appendChild(apresentacao);
const estadoApresentacao=apresentacao.querySelector("#apresentacao-estado");
const comandosApresentacao=new Set(["left","right","pageup","pagedown","home","end","f5","escape"]);
function sincronizarApresentacao(){
 const autorizado=state.approved&&state.authenticated&&state.sessionReady&&state.ws?.readyState===WebSocket.OPEN;
 apresentacao.querySelectorAll("[data-tecla-apresentacao]").forEach(botao=>botao.disabled=!autorizado);
 if(!autorizado)estadoApresentacao.textContent="Aguardando conexão autorizada";
 else if(estadoApresentacao.textContent.startsWith("Aguardando"))estadoApresentacao.textContent="Pronto para navegar";
}
apresentacao.addEventListener("click",evento=>{
 const botao=evento.target.closest("[data-tecla-apresentacao]");
 if(!botao||!apresentacao.contains(botao)||botao.disabled)return;
 const tecla=botao.dataset.teclaApresentacao;
 if(!comandosApresentacao.has(tecla))return;
 sincronizarApresentacao();
 if(botao.disabled)return;
 const enviado=wsSend({phase:"key",keys:tecla});
 estadoApresentacao.textContent=enviado?"Enviado: "+botao.textContent.trim():"Falha ao enviar comando";
 if(enviado){botao.classList.add("apresentacao-acionado");setTimeout(()=>botao.classList.remove("apresentacao-acionado"),200);}
});
setInterval(sincronizarApresentacao,1200);
sincronizarApresentacao();

// Modo palco: controle de slides em tela dedicada, sem alterar a sessão do Windows.
const abrirPalco=document.createElement("button");
abrirPalco.type="button";abrirPalco.className="abrir-palco";
abrirPalco.textContent="Abrir controle de palco";
abrirPalco.setAttribute("aria-haspopup","dialog");
apresentacao.querySelector(".apresentacao-cabecalho").append(abrirPalco);
const palco=document.createElement("section");
palco.id="palco-apresentacao";
palco.className="palco-apresentacao";
palco.hidden=true;
palco.setAttribute("role","dialog");
palco.setAttribute("aria-modal","true");
palco.setAttribute("aria-label","Controle de palco");
palco.innerHTML=`<header class="palco-cabecalho"><div><small>FLOW TUTUM NEXT · ANDER FLOW</small><strong>Modo palco</strong><span id="palco-estado" role="status" aria-live="polite">Aguardando sessão</span></div><button type="button" id="fechar-palco" aria-label="Fechar modo palco">Fechar ✕</button></header>
<div class="palco-principal"><button type="button" data-palco="left" aria-label="Slide anterior"><span aria-hidden="true">←</span> Anterior</button><button type="button" data-palco="right" aria-label="Próximo slide">Próximo <span aria-hidden="true">→</span></button></div>
<div class="palco-secundario"><button type="button" data-palco="f5">Iniciar slides</button><button type="button" data-palco="escape">Encerrar slides</button><button type="button" data-palco="pageup">Página acima</button><button type="button" data-palco="pagedown">Página abaixo</button></div>
<p>Controle o programa aberto no Windows. O envio de uma tecla não confirma a troca de slide.</p>`;
document.body.append(palco);
let focoAnteriorPalco=null;
function sincronizarPalco(){
 const autorizado=state.approved&&state.authenticated&&state.sessionReady&&state.ws?.readyState===WebSocket.OPEN;
 palco.querySelectorAll("[data-palco]").forEach(botao=>botao.disabled=!autorizado);
 el("palco-estado").textContent=autorizado?"Conectado · pronto para enviar":"Sem sessão autorizada";
}
function fecharPalco(){
 if(palco.hidden)return;
 palco.hidden=true;
 document.body.classList.remove("palco-aberto");
 (focoAnteriorPalco?.isConnected?focoAnteriorPalco:abrirPalco).focus();
}
abrirPalco.addEventListener("click",()=>{
 focoAnteriorPalco=document.activeElement;
 palco.hidden=false;
 document.body.classList.add("palco-aberto");
 sincronizarPalco();
 el("fechar-palco").focus();
});
el("fechar-palco").addEventListener("click",fecharPalco);
palco.addEventListener("click",evento=>{
 const botao=evento.target.closest("[data-palco]");
 if(!botao||botao.disabled||!palco.contains(botao))return;
 sincronizarPalco();
 if(botao.disabled)return;
 const tecla=botao.dataset.palco;
 if(!["left","right","f5","escape","pageup","pagedown"].includes(tecla))return;
 const enviado=wsSend({phase:"key",keys:tecla});
 el("palco-estado").textContent=enviado?"Tecla enviada · "+botao.textContent.trim():"Falha ao enviar tecla";
});
palco.addEventListener("keydown",evento=>{
 if(evento.key==="Escape"){evento.preventDefault();fecharPalco();return}
 if(evento.key!=="Tab")return;
 const focaveis=[...palco.querySelectorAll("button:not(:disabled)")];
 const primeiro=focaveis[0],ultimo=focaveis[focaveis.length-1];
 if(evento.shiftKey&&document.activeElement===primeiro){evento.preventDefault();ultimo.focus()}
 else if(!evento.shiftKey&&document.activeElement===ultimo){evento.preventDefault();primeiro.focus()}
});
window.addEventListener("blur",()=>{if(!palco.hidden)sincronizarPalco()});
document.addEventListener("visibilitychange",()=>{if(!document.hidden&&!palco.hidden)sincronizarPalco()});
document.querySelector("#disconnect")?.addEventListener("click",fecharPalco);
setInterval(()=>{if(!palco.hidden)sincronizarPalco()},1200);


// Leitura contínua: rolagem sustentada somente enquanto o usuário mantém pressionado.
const leitura=document.createElement("section");
leitura.className="controle-leitura";
leitura.setAttribute("aria-label","Leitura contínua");
leitura.innerHTML=`<div class="leitura-cabecalho"><div><strong>Leitura contínua</strong><small>Segure para rolar PDFs e páginas sem movimentos repetidos</small></div><span id="leitura-estado" role="status" aria-live="polite">Pronto</span></div>
<div class="leitura-velocidades" role="group" aria-label="Velocidade de leitura">
<button type="button" data-velocidade="1" aria-pressed="true">Suave</button>
<button type="button" data-velocidade="2" aria-pressed="false">Normal</button>
<button type="button" data-velocidade="3" aria-pressed="false">Rápida</button></div>
<div class="leitura-acoes"><button type="button" data-direcao="-1">↑ Subir</button><button type="button" data-direcao="1">↓ Descer</button></div>
<p>O movimento para imediatamente ao soltar, trocar de aba ou perder a conexão.</p>`;
el("touchpad-layout").appendChild(leitura);
let velocidadeLeitura=1,intervaloLeitura=null,ponteiroLeitura=null;
const estadoLeitura=leitura.querySelector("#leitura-estado");
function leituraAutorizada(){return state.approved&&state.authenticated&&state.sessionReady&&state.ws?.readyState===WebSocket.OPEN}
function pararLeitura(){
 if(intervaloLeitura!==null){clearInterval(intervaloLeitura);intervaloLeitura=null}
 ponteiroLeitura=null;
 leitura.querySelectorAll("[data-direcao]").forEach(b=>b.classList.remove("leitura-ativa"));
 estadoLeitura.textContent=leituraAutorizada()?"Pronto":"Aguardando conexão autorizada";
}
function iniciarLeitura(botao,evento){
 if(intervaloLeitura!==null||!leituraAutorizada())return;
 evento.preventDefault();
 ponteiroLeitura=evento.pointerId;
 try{botao.setPointerCapture(evento.pointerId)}catch{}
 const direcao=Number(botao.dataset.direcao);
 const rolar=()=>{
  if(!leituraAutorizada()||state.mode!=="trackpad"||document.hidden){pararLeitura();return}
  if(!wsSend({phase:"wheel",wheel:direcao*velocidadeLeitura*90}))pararLeitura();
 };
 botao.classList.add("leitura-ativa");
 estadoLeitura.textContent=direcao>0?"Descendo…":"Subindo…";
 rolar();
 if(intervaloLeitura===null&&leituraAutorizada())intervaloLeitura=setInterval(rolar,150);
}
leitura.addEventListener("pointerdown",evento=>{
 const botao=evento.target.closest("[data-direcao]");
 if(botao&&!botao.disabled)iniciarLeitura(botao,evento);
});
for(const tipo of ["pointerup","pointercancel","lostpointercapture"])leitura.addEventListener(tipo,evento=>{
 if(ponteiroLeitura===evento.pointerId)pararLeitura();
});
leitura.addEventListener("click",evento=>{
 const botao=evento.target.closest("[data-velocidade]");
 if(!botao)return;
 velocidadeLeitura=Number(botao.dataset.velocidade);
 leitura.querySelectorAll("[data-velocidade]").forEach(b=>b.setAttribute("aria-pressed",String(b===botao)));
});
window.addEventListener("blur",pararLeitura);
document.addEventListener("visibilitychange",()=>{if(document.hidden)pararLeitura()});
leitura.addEventListener("focusout",evento=>{if(!leitura.contains(evento.relatedTarget))pararLeitura()});
document.querySelectorAll("#mode-direct,#mode-trackpad,#disconnect").forEach(b=>b.addEventListener("click",pararLeitura));
setInterval(()=>{
 const autorizado=leituraAutorizada();
 leitura.querySelectorAll("[data-direcao]").forEach(b=>b.disabled=!autorizado);
 if(!autorizado&&intervaloLeitura!==null)pararLeitura();
},1000);

// Modo foco: amplia apenas a tela remota, sem alterar o monitor ou enviar comandos.
const botaoFoco=el("modo-foco");
const atualizarFoco=()=>{
 const ativo=document.body.classList.contains("modo-foco-ativo");
 botaoFoco.setAttribute("aria-pressed",String(ativo));
 botaoFoco.setAttribute("aria-label",ativo?"Sair do modo foco":"Ativar modo foco da tela remota");
 botaoFoco.querySelector("span").textContent=ativo?"Sair":"Foco";
 botaoFoco.title=ativo?"Restaurar painéis":"Ocultar painéis e ampliar a tela";
 sizeCanvas();
};
botaoFoco.addEventListener("click",()=>{
 document.body.classList.toggle("modo-foco-ativo");
 atualizarFoco();
});
document.addEventListener("keydown",evento=>{
 if(evento.key==="Escape"&&document.body.classList.contains("modo-foco-ativo")){
  document.body.classList.remove("modo-foco-ativo");
  atualizarFoco();
 }
});
el("mode-trackpad").addEventListener("click",()=>{
 if(document.body.classList.contains("modo-foco-ativo")){
  document.body.classList.remove("modo-foco-ativo");
  atualizarFoco();
 }
});
window.addEventListener("resize",()=>{
 if(document.body.classList.contains("modo-foco-ativo"))sizeCanvas();
});
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
