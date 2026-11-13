(()=>{"use strict";
const pad=document.getElementById("touchpad-layout");
if(!pad||!window.FlowTutumNext)return;
const bloco=document.createElement("section");
bloco.className="calibracao";
bloco.setAttribute("aria-label","Calibração guiada do touchpad");
bloco.innerHTML='<button type="button" class="calibracao-abrir" aria-expanded="false" aria-controls="calibracao-conteudo">Ajustar meu touchpad <span aria-hidden="true">⚙</span></button><div id="calibracao-conteudo" hidden><header><strong>Encontre seu controle ideal</strong><small>Teste as mudanças no touchpad. Nada será enviado ao PC durante os ajustes.</small></header><div class="calibracao-etapas" role="group" aria-label="Passos da calibração"><button type="button" data-etapa="0">1 · Ponteiro</button><button type="button" data-etapa="1">2 · Rolagem</button><button type="button" data-etapa="2">3 · Ergonomia</button></div><div class="calibracao-passo" role="group" aria-label="Ajuste atual"></div><div class="calibracao-acoes"><button type="button" data-anterior>Anterior</button><button type="button" data-proximo>Próximo</button><button type="button" data-fechar>Concluir</button></div><p class="calibracao-retorno" role="status" aria-live="polite"></p></div>';
const ancora=pad.querySelector(".pad-interaction");pad.insertBefore(bloco,ancora);
const abrir=bloco.querySelector(".calibracao-abrir"),conteudo=bloco.querySelector("#calibracao-conteudo"),passo=bloco.querySelector(".calibracao-passo"),retorno=bloco.querySelector(".calibracao-retorno");
let etapa=0;
const obter=id=>document.getElementById(id);
function atualizar(){
 bloco.querySelectorAll("[data-etapa]").forEach(b=>{const ativo=Number(b.dataset.etapa)===etapa;b.setAttribute("aria-current",ativo?"step":"false");b.setAttribute("aria-pressed",String(ativo))});
 bloco.querySelector("[data-anterior]").disabled=etapa===0;
 bloco.querySelector("[data-proximo]").hidden=etapa===2;
 bloco.querySelector("[data-fechar]").hidden=etapa!==2;
 if(etapa===0){passo.innerHTML='<label>Velocidade do ponteiro <output id="calibracao-valor"></output><input type="range" id="calibracao-ponteiro" min="0.5" max="3" step="0.1"></label><p>Mais lento para precisão; mais rápido para alcançar a borda da tela.</p>';const original=obter("pointer-sensitivity"),controle=obter("calibracao-ponteiro");controle.value=original.value;const mostrar=()=>obter("calibracao-valor").textContent=Number(controle.value).toFixed(1)+"×";mostrar();controle.oninput=()=>{original.value=controle.value;original.dispatchEvent(new Event("input",{bubbles:true}));mostrar();retorno.textContent="Velocidade ajustada e salva."}}
 else if(etapa===1){passo.innerHTML='<label>Velocidade da rolagem <output id="calibracao-valor"></output><input type="range" id="calibracao-rolagem" min="0.5" max="3" step="0.25"></label><p>Para planilhas, use rolagem horizontal no ajuste de direção existente.</p>';const original=obter("velocidade-rolagem"),controle=obter("calibracao-rolagem");controle.value=original.value;const mostrar=()=>obter("calibracao-valor").textContent=Number(controle.value).toFixed(2)+"×";mostrar();controle.oninput=()=>{original.value=controle.value;original.dispatchEvent(new Event("input",{bubbles:true}));mostrar();retorno.textContent="Rolagem ajustada e salva."}}
 else {passo.innerHTML='<p>Escolha a mão dominante. O aplicativo ajusta a ordem e o significado dos botões.</p><div class="calibracao-maos"><button type="button" data-mao-escolha="direita">Destro</button><button type="button" data-mao-escolha="esquerda">Canhoto</button></div>';passo.querySelectorAll("[data-mao-escolha]").forEach(b=>{b.setAttribute("aria-pressed",String(window.FlowTutumErgonomia?.mao()===b.dataset.maoEscolha));b.onclick=()=>{pad.querySelector('[data-mao="'+b.dataset.maoEscolha+'"]')?.click();atualizar();retorno.textContent="Preferência de mão salva."}})}
}
function alternar(visivel){conteudo.hidden=!visivel;abrir.setAttribute("aria-expanded",String(visivel));if(visivel){atualizar();bloco.querySelector("[data-etapa='"+etapa+"']").focus()}else abrir.focus()}
abrir.onclick=()=>alternar(conteudo.hidden);
bloco.querySelectorAll("[data-etapa]").forEach(b=>b.onclick=()=>{etapa=Number(b.dataset.etapa);atualizar()});
bloco.querySelector("[data-anterior]").onclick=()=>{etapa=Math.max(0,etapa-1);atualizar()};
bloco.querySelector("[data-proximo]").onclick=()=>{etapa=Math.min(2,etapa+1);atualizar()};
bloco.querySelector("[data-fechar]").onclick=()=>alternar(false);
bloco.addEventListener("keydown",e=>{if(e.key==="Escape"&&!conteudo.hidden){e.stopPropagation();alternar(false)}});
})();