(()=>{"use strict";
const flow=window.FlowTutumNext;
if(!flow)return;
const {state,wsSend}=flow;
const painel=document.createElement("section");
painel.className="navegacao-rapida";
painel.setAttribute("aria-label","Navegação rápida no computador");
painel.innerHTML=`<header><div><strong>Navegação rápida</strong><small>Internet, documentos e janelas</small></div><span id="navegacao-estado" role="status" aria-live="polite">Aguardando sessão</span></header>
<div class="navegacao-grupos">
<div role="group" aria-label="Navegador"><h3>Navegador</h3><div class="navegacao-botoes"><button type="button" data-comando="alt+left">← Voltar</button><button type="button" data-comando="alt+right">Avançar →</button><button type="button" data-comando="ctrl+r">Atualizar</button><button type="button" data-comando="ctrl+l">Endereço</button><button type="button" data-comando="ctrl+t">Nova aba</button><button type="button" data-comando="ctrl+w">Fechar aba</button></div></div>
<div role="group" aria-label="Documentos e janelas"><h3>Documentos e janelas</h3><div class="navegacao-botoes"><button type="button" data-comando="ctrl+f">Localizar</button><button type="button" data-comando="ctrl+a">Selecionar tudo</button><button type="button" data-comando="ctrl+z">Desfazer</button><button type="button" data-comando="ctrl+shift+z">Refazer</button><button type="button" data-comando="alt+tab">Trocar janela</button><button type="button" data-comando="escape">Esc</button></div></div>
</div><p>Atalhos enviados ao aplicativo ativo no Windows. A ação não é confirmada pelo aplicativo remoto.</p>`;
const destino=document.getElementById("touchpad-layout");
if(!destino)return;
destino.append(painel);
const estado=painel.querySelector("#navegacao-estado");
const autorizados=new Set(["alt+left","alt+right","ctrl+r","ctrl+l","ctrl+t","ctrl+w","ctrl+f","ctrl+a","ctrl+z","ctrl+shift+z","alt+tab","escape"]);
const conectado=()=>Boolean(state.approved&&state.authenticated&&state.sessionReady&&state.ws?.readyState===WebSocket.OPEN);
function atualizar(){
 const ativo=conectado();
 painel.querySelectorAll("[data-comando]").forEach(botao=>botao.disabled=!ativo);
 if(!ativo)estado.textContent="Sem sessão autorizada";
 else if(estado.textContent==="Sem sessão autorizada"||estado.textContent==="Aguardando sessão")estado.textContent="Pronto para enviar";
}
painel.addEventListener("click",evento=>{
 const botao=evento.target.closest("button[data-comando]");
 if(!botao||!painel.contains(botao))return;
 atualizar();
 const comando=botao.dataset.comando;
 if(botao.disabled||!autorizados.has(comando))return;
 const enviado=wsSend({phase:"key",keys:comando});
 estado.textContent=enviado?"Atalho enviado: "+botao.textContent.trim():"Falha ao enviar atalho";
 if(enviado){botao.classList.add("enviado");setTimeout(()=>botao.classList.remove("enviado"),180)}
});
document.addEventListener("visibilitychange",()=>{if(!document.hidden)atualizar()});
setInterval(()=>{if(!document.hidden)atualizar()},1400);
atualizar();
})();