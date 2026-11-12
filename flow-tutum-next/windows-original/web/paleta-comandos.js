/* Paleta de comandos: ações reais do painel, sem contornar a autorização do Windows. */
(function(){
"use strict";
const COMANDOS=[
{id:"tela",nome:"Abrir tela remota",grupo:"Navegação",tipo:"local",alvo:"mode-direct"},
{id:"touchpad",nome:"Abrir touchpad",grupo:"Navegação",tipo:"local",alvo:"mode-trackpad"},
{id:"teclado",nome:"Abrir teclado",grupo:"Entrada",tipo:"protegido",alvo:"open-keyboard"},
{id:"explorar",nome:"Explorar sem clicar",grupo:"Tela",tipo:"local",alvo:"explore-toggle"},
{id:"ajustar",nome:"Enquadrar monitor",grupo:"Tela",tipo:"local",alvo:"zoom-label"},
{id:"diagnostico",nome:"Diagnóstico da sessão",grupo:"Sessão",tipo:"local",alvo:"diagnostico-abrir"},
{id:"midia",nome:"Controles de mídia",grupo:"Mídia",tipo:"local",alvo:"midia-abrir"},
{id:"apresentar",nome:"Controle de apresentação",grupo:"Mídia",tipo:"local",alvo:"apresentacao-abrir"},
{id:"silenciar",nome:"Silenciar computador",grupo:"Mídia",tipo:"remoto",tecla:"volume-mudo"},
{id:"pausar",nome:"Reproduzir ou pausar",grupo:"Mídia",tipo:"remoto",tecla:"midia-reproduzir"},
{id:"desktop",nome:"Mostrar área de trabalho",grupo:"Windows",tipo:"remoto",tecla:"win+d"},
{id:"trocar",nome:"Alternar janela",grupo:"Windows",tipo:"remoto",tecla:"alt+tab"}
];
const normalizar=t=>String(t||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLocaleLowerCase("pt-BR").trim();
function pesquisar(termo){const partes=normalizar(termo).split(/\s+/).filter(Boolean);return COMANDOS.filter(c=>partes.every(p=>normalizar(c.nome+" "+c.grupo).includes(p)))}
function autorizado(s){return Boolean(s?.approved&&s?.authenticated&&s?.sessionReady&&!s?.somenteVisualizacao&&!s?.stopped)}
if(typeof module!=="undefined"&&module.exports)module.exports={pesquisar,autorizado,COMANDOS};
if(typeof document==="undefined")return;
const app=window.FlowTutumNext,ancora=document.querySelector(".topbar");if(!app||!ancora)return;
const abrir=document.createElement("button");abrir.type="button";abrir.className="paleta-abrir";abrir.textContent="Ações";abrir.setAttribute("aria-label","Buscar ações e atalhos");ancora.append(abrir);
const painel=document.createElement("section");painel.className="paleta-comandos";painel.hidden=true;painel.setAttribute("role","dialog");painel.setAttribute("aria-modal","true");painel.setAttribute("aria-label","Buscar ações do Flow Tutum Next");
painel.innerHTML='<div class="paleta-corpo"><header><strong>O que deseja fazer?</strong><button type="button" class="paleta-fechar" aria-label="Fechar busca">Fechar</button></header><label for="paleta-busca">Buscar comando</label><input id="paleta-busca" type="search" autocomplete="off" placeholder="Tela, teclado, mídia…"><div class="paleta-lista" role="list"></div><p class="paleta-status" role="status" aria-live="polite"></p><small>Ander Flow · Comandos remotos exigem sessão autorizada.</small></div>';
document.body.append(painel);
const campo=painel.querySelector("input"),lista=painel.querySelector(".paleta-lista"),status=painel.querySelector(".paleta-status");
let anterior=null,ultimo=0;
function fechar(){painel.hidden=true;document.body.classList.remove("paleta-aberta");(anterior?.isConnected?anterior:abrir).focus()}
function mostrar(){anterior=document.activeElement;painel.hidden=false;document.body.classList.add("paleta-aberta");campo.value="";status.textContent="";renderizar();campo.focus()}
function executar(c){
 const s=app.state;
 if(c.tipo!=="local"&&!autorizado(s)){status.textContent="Autorize o controle no Windows antes de continuar.";return}
 if(c.tipo==="remoto"){
  if(performance.now()-ultimo<350){status.textContent="Aguarde antes de repetir.";return}
  if(!app.wsSend({phase:"key",keys:c.tecla})){status.textContent="Comando não enviado. Confira a conexão.";return}
  ultimo=performance.now();status.textContent="Comando enviado, sem confirmação de execução: "+c.nome;return;
 }
 const botao=document.getElementById(c.alvo);
 if(!botao||botao.disabled){status.textContent="Recurso indisponível nesta sessão.";return}
 botao.click();fechar();
}
function renderizar(){
 lista.replaceChildren();const itens=pesquisar(campo.value);
 if(!itens.length){lista.textContent="Nenhuma ação encontrada.";return}
 for(const c of itens){
  const b=document.createElement("button");b.type="button";b.className="paleta-item";
  b.disabled=c.tipo!=="local"&&!autorizado(app.state);
  const titulo=document.createElement("strong");titulo.textContent=c.nome;
  const grupo=document.createElement("small");grupo.textContent=c.grupo+(b.disabled?" · autorização necessária":"");
  b.append(titulo,grupo);b.addEventListener("click",()=>executar(c));lista.append(b);
 }
}
abrir.addEventListener("click",mostrar);
painel.querySelector(".paleta-fechar").addEventListener("click",fechar);
campo.addEventListener("input",renderizar);
painel.addEventListener("keydown",e=>{
 if(e.key==="Escape"){e.preventDefault();fechar()}
 if(e.key==="Enter"&&document.activeElement===campo){const primeiro=lista.querySelector("button:not(:disabled)");if(primeiro){e.preventDefault();primeiro.click()}}
 if(e.key==="Tab"){const focaveis=[...painel.querySelectorAll("button:not(:disabled),input")];const i=focaveis.indexOf(document.activeElement);if(e.shiftKey&&i===0){e.preventDefault();focaveis.at(-1).focus()}else if(!e.shiftKey&&i===focaveis.length-1){e.preventDefault();focaveis[0].focus()}}
});
document.addEventListener("keydown",e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="k"){e.preventDefault();painel.hidden?mostrar():fechar()}});
document.addEventListener("visibilitychange",()=>{if(!document.hidden&&!painel.hidden)renderizar()});
})();