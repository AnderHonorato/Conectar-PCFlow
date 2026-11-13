(()=>{"use strict";
const chave="flow-next-mao-dominante";
const pad=document.getElementById("touchpad-layout");
if(!pad)return;
const painel=document.createElement("section");
painel.className="ergonomia-painel";
painel.setAttribute("aria-label","Ergonomia do touchpad");
painel.innerHTML='<div class="ergonomia-cabecalho"><strong>Controle confortável</strong><small>Adapte os botões à sua mão</small></div><div class="ergonomia-opcoes" role="group" aria-label="Mão dominante"><button type="button" data-mao="direita" aria-pressed="true">Destro</button><button type="button" data-mao="esquerda" aria-pressed="false">Canhoto</button></div><p id="ergonomia-ajuda" role="status">Botão principal à esquerda.</p>';
pad.insertBefore(painel,pad.querySelector(".pad-interaction"));
let mao="direita";
try{if(localStorage.getItem(chave)==="esquerda")mao="esquerda"}catch{}
function aplicar(valor){
 if(!["direita","esquerda"].includes(valor))return;
 mao=valor;
 pad.dataset.mao=mao;
 painel.querySelectorAll("[data-mao]").forEach(botao=>{
  const selecionado=botao.dataset.mao===mao;
  botao.setAttribute("aria-pressed",String(selecionado));
  botao.classList.toggle("selecionado",selecionado);
 });
 const canhoto=mao==="esquerda";
 painel.querySelector("#ergonomia-ajuda").textContent=canhoto?"Botão principal à direita. Toque simples e duplo usam o botão principal.":"Botão principal à esquerda. Toque simples e duplo usam o botão principal.";
 const botoes=pad.querySelectorAll("[data-mouse]");
 botoes.forEach(botao=>{
  const tipo=botao.dataset.mouse;
  if(tipo==="middle")return;
  botao.textContent=tipo==="left"?(canhoto?"Direito · principal":"Esquerdo · principal"):(canhoto?"Esquerdo · secundário":"Direito · secundário");
 });
 try{localStorage.setItem(chave,mao)}catch{}
}
window.FlowTutumErgonomia=Object.freeze({
 primario:()=>mao==="esquerda"?"right":"left",
 secundario:()=>mao==="esquerda"?"left":"right",
 mao:()=>mao
});
painel.querySelectorAll("[data-mao]").forEach(botao=>botao.addEventListener("click",()=>aplicar(botao.dataset.mao)));
aplicar(mao);
})();
