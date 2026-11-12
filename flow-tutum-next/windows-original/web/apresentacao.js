/* Controle de apresentações: usa somente atalhos nativos já aceitos pelo Windows. */
(()=>{"use strict";
const painel=document.getElementById("apresentacao-painel"),abrir=document.getElementById("apresentacao-abrir");
if(!painel||!abrir)return;
const estado=document.getElementById("apresentacao-estado");
const comandos=[
 ["f5","Iniciar apresentação","Iniciar do começo"],
 ["shift+f5","Do slide atual","Iniciar deste slide"],
 ["escape","Encerrar apresentação","Sair da apresentação"],
 ["right","Próximo slide","Avançar"],
 ["left","Slide anterior","Voltar"],
 ["home","Primeiro slide","Ir ao início"],
 ["end","Último slide","Ir ao fim"],
 ["b","Tela preta","Escurecer apresentação"],
 ["w","Tela branca","Clarear apresentação"]
];
const valido=()=>{const s=window.FlowTutumNext?.state;return Boolean(s?.approved&&s?.authenticated&&s?.sessionReady&&!s?.somenteVisualizacao&&!s?.stopped&&s.ws?.readyState===WebSocket.OPEN)};
function atualizar(){const pronto=valido();painel.querySelectorAll("[data-apresentacao]").forEach(b=>b.disabled=!pronto);if(!pronto)estado.textContent="Controle indisponível: autorize a sessão no Windows.";}
function alternar(visivel){painel.hidden=!visivel;abrir.setAttribute("aria-expanded",String(visivel));if(visivel){atualizar();painel.querySelector("[data-apresentacao]")?.focus()}else abrir.focus();}
const grade=painel.querySelector(".apresentacao-grade");
for(const [tecla,titulo,descricao] of comandos){
 const botao=document.createElement("button");botao.type="button";botao.dataset.apresentacao=tecla;
 const forte=document.createElement("strong");forte.textContent=titulo;
 const pequeno=document.createElement("small");pequeno.textContent=descricao;
 botao.append(forte,pequeno);grade.append(botao);
}
grade.addEventListener("click",evento=>{
 const botao=evento.target.closest("[data-apresentacao]");if(!botao)return;
 atualizar();if(!valido())return;
 const ok=window.FlowTutumNext.wsSend({phase:"key",keys:botao.dataset.apresentacao});
 estado.textContent=ok?"Atalho enviado: "+botao.querySelector("strong").textContent+". O aplicativo no PC determina o resultado.":"Não foi possível enviar. Verifique a sessão.";
});
abrir.addEventListener("click",()=>alternar(painel.hidden));
document.getElementById("apresentacao-fechar").addEventListener("click",()=>alternar(false));
painel.addEventListener("keydown",e=>{if(e.key==="Escape"){e.preventDefault();e.stopPropagation();alternar(false)}});
document.addEventListener("visibilitychange",()=>{if(!document.hidden&&!painel.hidden)atualizar()});
const relogio=setInterval(()=>{if(!painel.hidden)atualizar()},1500);
window.addEventListener("pagehide",()=>clearInterval(relogio),{once:true});
})();
