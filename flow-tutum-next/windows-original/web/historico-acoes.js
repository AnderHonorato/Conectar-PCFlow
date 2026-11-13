/* Histórico local de ações explícitas: somente identificadores, nunca dados de sessão. */
(()=>{"use strict";
const CHAVE="flow-next-acoes-recentes-v1",LIMITE=8;
const ACOES={
touchpad:{nome:"Touchpad",local:"mode-trackpad"},tela:{nome:"Tela remota",local:"mode-direct"},
teclado:{nome:"Teclado",local:"open-keyboard"},explorar:{nome:"Explorar",local:"explore-toggle"},
zoom:{nome:"Ampliar",local:"zoom-in"},
play:{nome:"Reproduzir / pausar",tecla:"midia-reproduzir"},volume:{nome:"Silenciar",tecla:"volume-mudo"},
anterior:{nome:"Faixa anterior",tecla:"midia-anterior"},proxima:{nome:"Próxima faixa",tecla:"midia-proxima"},
volumeMais:{nome:"Aumentar volume",tecla:"volume-mais"},volumeMenos:{nome:"Diminuir volume",tecla:"volume-menos"},
desktop:{nome:"Área de trabalho",tecla:"win+d"}};
function normalizar(lista){return Array.isArray(lista)?lista.filter(x=>typeof x==="string"&&Object.hasOwn(ACOES,x)).slice(0,LIMITE):[]}
function adicionar(lista,id){return Object.hasOwn(ACOES,id)?[id,...normalizar(lista).filter(x=>x!==id)].slice(0,LIMITE):normalizar(lista)}
function autorizado(s){return !!(s?.approved&&s?.authenticated&&!s?.somenteVisualizacao&&!s?.stopped&&!s?.pausaLocal)}
if(typeof module!=="undefined"&&module.exports)module.exports={normalizar,adicionar,autorizado,ACOES};
if(typeof document==="undefined")return;
const app=window.FlowTutumNext,ancora=document.querySelector(".favoritos");if(!app||!ancora)return;
let recentes=[];try{recentes=normalizar(JSON.parse(localStorage.getItem(CHAVE)||"[]"))}catch{}
const painel=document.createElement("section");painel.className="historico-acoes";painel.setAttribute("aria-label","Ações recentes");
painel.innerHTML='<div class="historico-topo"><strong>Ações recentes</strong><button type="button" id="historico-limpar">Limpar</button></div><div id="historico-lista" class="historico-lista"></div><p id="historico-estado" role="status" aria-live="polite"></p>';
ancora.insertAdjacentElement("afterend",painel);
const lista=painel.querySelector("#historico-lista"),estado=painel.querySelector("#historico-estado");
let ultimoEnvio=0;
function salvar(){try{localStorage.setItem(CHAVE,JSON.stringify(recentes))}catch{estado.textContent="Não foi possível guardar o histórico."}}
function registrar(id){recentes=adicionar(recentes,id);salvar();renderizar()}
function repetir(id){
 const item=ACOES[id];if(!item)return;
 if(item.tecla){
  if(!autorizado(app.state)){estado.textContent="Controle não autorizado ou sessão pausada.";return}
  if(Date.now()-ultimoEnvio<700){estado.textContent="Aguarde antes de repetir.";return}
  const enviado=app.wsSend({phase:"key",keys:item.tecla});
  if(!enviado){estado.textContent="Comando não enviado.";return}
  ultimoEnvio=Date.now();estado.textContent="Comando enviado, sem confirmação do Windows.";
 }else{
  const botao=document.getElementById(item.local);
  if(!botao||botao.disabled){estado.textContent="Ação indisponível nesta sessão.";return}
  botao.click();estado.textContent="Ação local solicitada.";
 }
 registrar(id);
}
function renderizar(){
 lista.replaceChildren();
 if(!recentes.length){lista.textContent="Nenhuma ação registrada neste aparelho.";return}
 for(const id of recentes){const item=ACOES[id],b=document.createElement("button");b.type="button";b.textContent=item.nome;b.disabled=!!item.tecla&&!autorizado(app.state);b.title=b.disabled?"Controle remoto não autorizado":"Repetir "+item.nome;b.addEventListener("click",()=>repetir(id));lista.append(b)}
}
painel.querySelector("#historico-limpar").addEventListener("click",()=>{recentes=[];salvar();renderizar();estado.textContent="Histórico local apagado."});
window.addEventListener("flow-acao-executada",e=>{const id=e.detail?.id;if(Object.hasOwn(ACOES,id))registrar(id)});
window.addEventListener("flow-favoritos-alterados",renderizar);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)renderizar()});
renderizar();
})();