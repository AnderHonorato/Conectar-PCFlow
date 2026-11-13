(()=>{"use strict";
const raiz=document.getElementById("viewer"),canvas=document.getElementById("screen");
if(!raiz||!canvas)return;
const caixa=document.createElement("section");caixa.className="guia-gestos";caixa.setAttribute("aria-label","Guia de gestos da tela remota");
caixa.innerHTML='<button type="button" class="guia-abrir" aria-expanded="false" aria-controls="guia-conteudo">? Gestos</button><div id="guia-conteudo" class="guia-conteudo" hidden><div class="guia-topo"><strong>Como controlar</strong><button type="button" class="guia-fechar" aria-label="Fechar guia de gestos">×</button></div><p class="guia-contexto" role="status" aria-live="polite">Escolha um modo de controle.</p><div class="guia-passos"></div><label class="guia-lembrar"><input type="checkbox"> Mostrar dicas ao abrir</label></div>';
raiz.append(caixa);
const abrir=caixa.querySelector(".guia-abrir"),conteudo=caixa.querySelector(".guia-conteudo"),fechar=caixa.querySelector(".guia-fechar"),contexto=caixa.querySelector(".guia-contexto"),passos=caixa.querySelector(".guia-passos"),lembrar=caixa.querySelector("input");
const chave="flow-next-dicas-gestos";
const dicas={
 direct:["Toque uma vez para clicar no ponto exibido.","Arraste com um dedo para mover o ponteiro.","Use dois dedos para ampliar a imagem; explore sem clicar ativando Explorar."],
 trackpad:["Deslize o dedo para mover o ponteiro.","Toque para clicar; segure para arrastar quando o modo permitir.","Use dois dedos para rolar; os botões abaixo oferecem cliques precisos."],
 explore:["Arraste a imagem para observar outra região sem enviar cliques.","Amplie com dois dedos ou pelos botões de zoom.","Desative Explorar para voltar ao controle remoto."]
};
function modo(){const s=window.FlowTutumNext?.state;return s?.exploring?"explore":s?.mode==="trackpad"?"trackpad":"direct";}
function atualizar(){const m=modo();const titulo={direct:"Toque direto",trackpad:"Touchpad",explore:"Explorar sem clicar"};contexto.textContent=titulo[m];passos.replaceChildren();dicas[m].forEach((frase,i)=>{const item=document.createElement("p");const numero=document.createElement("span");numero.textContent=String(i+1).padStart(2,"0");item.append(numero,document.createTextNode(frase));passos.append(item);});}
function alternar(valor){conteudo.hidden=!valor;abrir.setAttribute("aria-expanded",String(valor));if(valor)atualizar();}
abrir.addEventListener("click",()=>alternar(conteudo.hidden));
fechar.addEventListener("click",()=>{alternar(false);abrir.focus();});
caixa.addEventListener("keydown",e=>{if(e.key==="Escape"&&!conteudo.hidden){alternar(false);abrir.focus();}});
lembrar.checked=(()=>{try{return localStorage.getItem(chave)==="1"}catch{return false}})();
lembrar.addEventListener("change",()=>{try{localStorage.setItem(chave,lembrar.checked?"1":"0")}catch{}});
["flow-zoom-alterado"].forEach(nome=>window.addEventListener(nome,()=>{if(!conteudo.hidden)atualizar()}));
document.querySelectorAll("#dock-direct,#dock-trackpad,#explore-toggle,[data-mode]").forEach(botao=>botao.addEventListener("click",()=>{if(!conteudo.hidden)queueMicrotask(atualizar)}));
if(lembrar.checked)alternar(true);
})();