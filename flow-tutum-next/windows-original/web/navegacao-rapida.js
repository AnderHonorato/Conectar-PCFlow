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
// Favoritos locais: não alteram autorização, sessão ou comandos no Windows.
function carregarFavoritos(){try{const salvos=JSON.parse(localStorage.getItem("flow-atalhos-favoritos")||"[]");return Array.isArray(salvos)?salvos.filter(chave=>autorizados.has(chave)):[]}catch{return []}}
const favoritos=new Set(carregarFavoritos());
const ferramentas=document.createElement("div");
ferramentas.className="navegacao-ferramentas";
ferramentas.innerHTML='<label for="buscar-atalho">Buscar atalho</label><input id="buscar-atalho" type="search" placeholder="Ex.: localizar, nova aba" autocomplete="off"><button type="button" id="limpar-busca">Limpar</button>';
painel.querySelector(".navegacao-grupos").before(ferramentas);
const areaFavoritos=document.createElement("section");
areaFavoritos.className="navegacao-favoritos";
areaFavoritos.setAttribute("aria-label","Meus atalhos favoritos");
areaFavoritos.innerHTML='<h3>Meus favoritos</h3><p class="favoritos-vazio">Toque na estrela de um atalho para fixá-lo aqui.</p><div class="favoritos-lista"></div>';
painel.querySelector(".navegacao-grupos").before(areaFavoritos);
painel.querySelectorAll("[data-comando]").forEach(botao=>{
 const comando=botao.dataset.comando;
 const favorito=document.createElement("button");
 favorito.type="button";favorito.className="favorito-atalho";
 favorito.dataset.favorito=comando;
 favorito.setAttribute("aria-label","Favoritar "+botao.textContent.trim());
 botao.after(favorito);
});
const busca=ferramentas.querySelector("input");
function atualizarFavoritos(){
 const lista=areaFavoritos.querySelector(".favoritos-lista");
 lista.replaceChildren();
 const atalhos=[...painel.querySelectorAll(".navegacao-grupos button[data-comando]")];
 for(const chave of favoritos){
  const original=atalhos.find(botao=>botao.dataset.comando===chave);
  if(!original)continue;
  const botao=document.createElement("button");
  botao.type="button";botao.dataset.comando=chave;
  botao.textContent=original.textContent.trim();
  botao.setAttribute("aria-label","Executar favorito: "+botao.textContent);
  lista.append(botao);
 }
 areaFavoritos.querySelector(".favoritos-vazio").hidden=lista.childElementCount>0;
 areaFavoritos.classList.toggle("sem-favoritos",lista.childElementCount===0);
}
function atualizarLista(){
 const termo=busca.value.trim().toLocaleLowerCase("pt-BR");
 painel.querySelectorAll("[data-comando]").forEach(botao=>{
  const chave=botao.dataset.comando,grupo=botao.parentElement;
  const favorito=grupo.querySelector('[data-favorito="'+chave+'"]');
  const visivel=!termo||botao.textContent.toLocaleLowerCase("pt-BR").includes(termo)||chave.includes(termo);
  botao.hidden=!visivel;favorito.hidden=!visivel;
  favorito.textContent=favoritos.has(chave)?"★":"☆";
  favorito.setAttribute("aria-pressed",String(favoritos.has(chave)));
  favorito.setAttribute("aria-label",(favoritos.has(chave)?"Remover dos favoritos: ":"Adicionar aos favoritos: ")+botao.textContent.trim());
 });
 painel.querySelectorAll(".navegacao-botoes").forEach(grupo=>{
  const secao=grupo.parentElement;
  secao.hidden=![...grupo.querySelectorAll("[data-comando]")].some(b=>!b.hidden);
 });
 atualizarFavoritos();
}
busca.addEventListener("input",atualizarLista);
ferramentas.querySelector("#limpar-busca").addEventListener("click",()=>{busca.value="";atualizarLista();busca.focus()});
painel.addEventListener("click",evento=>{
 const favorito=evento.target.closest("[data-favorito]");
 if(!favorito)return;
 const chave=favorito.dataset.favorito;
 if(favoritos.has(chave))favoritos.delete(chave);else favoritos.add(chave);
 localStorage.setItem("flow-atalhos-favoritos",JSON.stringify([...favoritos]));
 atualizarLista();
});
atualizarLista();
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