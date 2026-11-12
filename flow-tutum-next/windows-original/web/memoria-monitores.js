(()=>{"use strict";
const app=window.FlowTutumNext,monitor=document.getElementById("monitor"),ferramentas=document.querySelector(".zoom-tools");
if(!app||!monitor||!ferramentas)return;
const {state,paint}=app,CHAVE="flow-next-visualizacao-monitores-v1";
let ativo=true,indice=String(state.monitor),temporizador;
function carregar(){try{const dado=JSON.parse(localStorage.getItem(CHAVE)||"{}");return dado&&typeof dado==="object"&&!Array.isArray(dado)?dado:{}}catch{return {}}}
function guardar(dados){try{localStorage.setItem(CHAVE,JSON.stringify(dados))}catch{/* Armazenamento indisponível: sessão continua funcional. */}}
function valido(p){return p&&Number.isFinite(p.zoom)&&p.zoom>=1&&p.zoom<=5&&Number.isFinite(p.panX)&&Number.isFinite(p.panY)&&Math.abs(p.panX)<=10000&&Math.abs(p.panY)<=10000&&["fit","fill"].includes(p.fillMode)}
function salvar(){if(!ativo)return;const dados=carregar();dados[indice]={zoom:state.zoom,panX:state.panX,panY:state.panY,fillMode:state.fillMode};guardar(dados)}
function restaurar(){if(!ativo)return;const dados=carregar(),p=dados[indice];if(!valido(p))return;state.zoom=p.zoom;state.panX=p.panX;state.panY=p.panY;state.fillMode=p.fillMode;paint();window.dispatchEvent(new Event("flow-zoom-alterado"))}
const painel=document.createElement("div");painel.className="memoria-monitores";
painel.innerHTML='<label><input type="checkbox" checked id="lembrar-monitores"> Lembrar zoom por monitor</label><button type="button" id="limpar-monitor">Redefinir este monitor</button><span role="status" aria-live="polite" id="memoria-status">Preferências locais</span>';
ferramentas.insertAdjacentElement("afterend",painel);
const caixa=painel.querySelector("#lembrar-monitores"),saida=painel.querySelector("#memoria-status");
caixa.addEventListener("change",()=>{ativo=caixa.checked;saida.textContent=ativo?"Memória ativada":"Memória pausada";if(ativo)salvar()});
painel.querySelector("#limpar-monitor").addEventListener("click",()=>{const dados=carregar();delete dados[indice];guardar(dados);state.zoom=1;state.panX=0;state.panY=0;state.fillMode="fit";paint();window.dispatchEvent(new Event("flow-zoom-alterado"));saida.textContent="Visualização redefinida"});
monitor.addEventListener("change",()=>{salvar();indice=String(monitor.value);setTimeout(()=>{restaurar();saida.textContent="Monitor "+(Number(indice)+1)+" selecionado"},0)},true);
window.addEventListener("flow-zoom-alterado",()=>{clearTimeout(temporizador);temporizador=setTimeout(salvar,250)});
window.addEventListener("pagehide",salvar);
restaurar();
})();
