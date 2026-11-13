(()=>{"use strict";
const ACOES=[
{id:"touchpad",nome:"Touchpad",tipo:"local",executar:a=>a.setMode("trackpad")},
{id:"tela",nome:"Tela remota",tipo:"local",executar:a=>a.setMode("direct")},
{id:"teclado",nome:"Teclado",tipo:"local",executar:()=>document.getElementById("open-keyboard")?.click()},
{id:"explorar",nome:"Explorar",tipo:"local",executar:()=>document.getElementById("explore-toggle")?.click()},
{id:"zoom",nome:"Ampliar",tipo:"local",executar:()=>document.getElementById("zoom-in")?.click()},
{id:"play",nome:"Reproduzir / pausar",tipo:"remoto",comando:{phase:"key",keys:"midia-reproduzir"}},
{id:"volume",nome:"Silenciar",tipo:"remoto",comando:{phase:"key",keys:"volume-mudo"}},
{id:"desktop",nome:"Área de trabalho",tipo:"remoto",comando:{phase:"key",keys:"win+d"}}
];
const CHAVE="flow-next-favoritos-v1",CHAVE_PERFIS="flow-next-favoritos-perfis-v1",PADRAO=["touchpad","teclado","explorar","play"];
const ids=new Set(ACOES.map(x=>x.id));
function normalizar(valor){return Array.isArray(valor)?[...new Set(valor.filter(x=>typeof x==="string"&&ids.has(x)))].slice(0,8):[...PADRAO]}
function moverPorTeclado(lista,id,direcao){const pos=lista.indexOf(id);return reordenar(lista,pos,pos+direcao)}
function reordenar(lista,origem,destino){const itens=[...lista];if(!Number.isInteger(origem)||!Number.isInteger(destino)||origem<0||destino<0||origem>=itens.length||destino>=itens.length)return itens;const [item]=itens.splice(origem,1);itens.splice(destino,0,item);return itens}
function carregar(){try{const raw=localStorage.getItem(CHAVE);return raw===null?[...PADRAO]:normalizar(JSON.parse(raw))}catch{return [...PADRAO]}}
function guardar(v){try{localStorage.setItem(CHAVE,JSON.stringify(v))}catch{}}
if(typeof module!=="undefined"&&module.exports)module.exports={normalizar,reordenar,moverPorTeclado,PADRAO,catalogo:ACOES};
if(typeof document==="undefined")return;
const app=window.FlowTutumNext,ancora=document.querySelector(".dock");if(!app||!ancora)return;
const PERFIS=["Pessoal","Trabalho","Mídia"];
function carregarPerfis(){try{const dados=JSON.parse(localStorage.getItem(CHAVE_PERFIS)||"{}");return {ativo:PERFIS.includes(dados.ativo)?dados.ativo:"Pessoal",listas:Object.fromEntries(PERFIS.map(p=>[p,Array.isArray(dados.listas?.[p])?normalizar(dados.listas[p]):p==="Pessoal"?carregar():p==="Trabalho"?["tela","teclado","desktop","explorar"]:["play","volume","touchpad","tela"]]))}}catch{return {ativo:"Pessoal",listas:{Pessoal:carregar(),Trabalho:["tela","teclado","desktop","explorar"],"Mídia":["play","volume","touchpad","tela"]}}}}
let perfis=carregarPerfis(),favoritos=[...perfis.listas[perfis.ativo]],editando=false,mostrarTodos=false,desfazer=null;
function registrarDesfazer(){desfazer={perfil:perfis.ativo,lista:[...favoritos]}}
function desfazerAlteracao(){if(!desfazer)return;const anterior=desfazer;desfazer=null;perfis.listas[anterior.perfil]=[...anterior.lista];if(perfis.ativo===anterior.perfil)favoritos=[...anterior.lista];salvar();renderizar();mensagem("Alteração desfeita")}

function salvar(){perfis.listas[perfis.ativo]=[...favoritos];try{localStorage.setItem(CHAVE_PERFIS,JSON.stringify(perfis));if(perfis.ativo==="Pessoal")guardar(favoritos)}catch{mensagem("Não foi possível salvar neste navegador")}}
function selecionarPerfil(nome){if(!PERFIS.includes(nome))return;desfazer=null;salvar();perfis.ativo=nome;favoritos=[...perfis.listas[nome]];salvar();renderizar();mensagem("Perfil "+nome+" selecionado")}
const painel=document.createElement("section");painel.className="favoritos";painel.setAttribute("aria-label","Ações favoritas");
painel.innerHTML='<div class="favoritos-topo"><strong>Favoritos</strong><label class="favoritos-perfil-rotulo">Perfil <select id="favoritos-perfil" aria-label="Perfil de favoritos"><option>Pessoal</option><option>Trabalho</option><option>Mídia</option></select></label><button type="button" id="favoritos-contexto" aria-pressed="true">Contexto: ligado</button><button type="button" id="favoritos-editar" aria-pressed="false">Organizar</button></div><div class="favoritos-acoes" id="favoritos-acoes"></div><p id="favoritos-retorno" role="status" aria-live="polite"></p><label id="favoritos-busca-rotulo" class="favoritos-busca-rotulo" hidden>Buscar ação<input id="favoritos-busca" type="search" autocomplete="off" placeholder="Ex.: volume, teclado"></label><div id="favoritos-catalogo" class="favoritos-catalogo" hidden></div>';
ancora.insertAdjacentElement("beforebegin",painel);
const seletor=painel.querySelector("#favoritos-perfil");seletor.value=perfis.ativo;seletor.addEventListener("change",()=>selecionarPerfil(seletor.value));
const lista=painel.querySelector("#favoritos-acoes"),catalogo=painel.querySelector("#favoritos-catalogo"),retorno=painel.querySelector("#favoritos-retorno"),editar=painel.querySelector("#favoritos-editar");
function mensagem(t){retorno.textContent=t}
const desfazerBotao=botao("Desfazer última alteração",desfazerAlteracao,"Desfazer mudança nos favoritos");desfazerBotao.id="favoritos-desfazer";painel.querySelector(".favoritos-topo").append(desfazerBotao);
const busca=painel.querySelector("#favoritos-busca"),rotuloBusca=painel.querySelector("#favoritos-busca-rotulo"),contexto=painel.querySelector("#favoritos-contexto");
function disponivel(item){if(item.tipo==="remoto")return !!(app.state.approved&&app.state.authenticated&&!app.state.somenteVisualizacao);if(item.id==="teclado")return !!app.state.approved;return true}
contexto.addEventListener("click",()=>{mostrarTodos=!mostrarTodos;contexto.setAttribute("aria-pressed",String(!mostrarTodos));contexto.textContent=mostrarTodos?"Contexto: desligado":"Contexto: ligado";renderizar()});
busca.addEventListener("input",()=>renderizar());
function mover(id,delta){const pos=favoritos.indexOf(id),dest=pos+delta;if(pos<0||dest<0||dest>=favoritos.length)return;registrarDesfazer();favoritos=reordenar(favoritos,pos,dest);salvar();renderizar()}
function acionar(item){
 if(item.tipo==="remoto"){
  if(!app.state.approved||!app.state.authenticated||app.state.somenteVisualizacao){mensagem("Controle remoto não autorizado");return}
  mensagem(app.wsSend(item.comando)?"Comando enviado: "+item.nome:"Comando não enviado");return;
 }
 if(item.id==="teclado"&&!app.state.approved){mensagem("Teclado exige autorização do Windows");return}
 item.executar(app);mensagem("Ação: "+item.nome);
}
function botao(texto,acao,rotulo){const b=document.createElement("button");b.type="button";b.textContent=texto;b.setAttribute("aria-label",rotulo||texto);b.addEventListener("click",acao);return b}
function renderizar(){
 desfazerBotao.hidden=!desfazer;
 seletor.value=perfis.ativo;
 lista.replaceChildren();catalogo.replaceChildren();
 for(const id of favoritos){
  const candidato=ACOES.find(x=>x.id===id);if(!editando&&!mostrarTodos&&candidato&&!disponivel(candidato))continue;
  const item=ACOES.find(x=>x.id===id);if(!item)continue;
  const grupo=document.createElement("div");grupo.className="favoritos-item";grupo.dataset.favorito=id;
  const acao=botao(item.nome,()=>acionar(item));acao.disabled=!disponivel(item);if(acao.disabled)acao.title="Aguardando autorização do Windows";grupo.append(acao);
  if(editando){grupo.append(botao("↕",()=>{},"Arrastar "+item.nome+" para reorganizar"));grupo.lastElementChild.className="favoritos-alca";grupo.lastElementChild.title="Arraste ou use as setas para reorganizar";grupo.lastElementChild.addEventListener("keydown",evento=>{if(!["ArrowLeft","ArrowRight","ArrowUp","ArrowDown"].includes(evento.key))return;evento.preventDefault();const direcao=["ArrowLeft","ArrowUp"].includes(evento.key)?-1:1;const indice=favoritos.indexOf(id);if(indice+direcao<0||indice+direcao>=favoritos.length){mensagem("Limite da lista");return}mover(id,direcao);const novaAlca=lista.querySelector(`[data-favorito="${id}"] .favoritos-alca`);novaAlca?.focus();mensagem(item.nome+" movido para posição "+(indice+direcao+1))});grupo.append(botao("←",()=>mover(id,-1),"Mover "+item.nome+" para esquerda"),botao("→",()=>mover(id,1),"Mover "+item.nome+" para direita"),botao("×",()=>{registrarDesfazer();favoritos=favoritos.filter(x=>x!==id);salvar();renderizar()},"Remover "+item.nome))}
  lista.append(grupo);
 }
 if(!lista.children.length)lista.append("Sem ações disponíveis neste momento. Autorize o Windows ou desative o filtro de contexto.");
 for(const item of ACOES.filter(x=>!favoritos.includes(x.id)&&x.nome.toLocaleLowerCase("pt-BR").includes(busca.value.trim().toLocaleLowerCase("pt-BR"))))catalogo.append(botao("+ "+item.nome,()=>{registrarDesfazer();favoritos.push(item.id);salvar();renderizar()},"Adicionar "+item.nome));
 catalogo.hidden=!editando;rotuloBusca.hidden=!editando;editar.setAttribute("aria-pressed",String(editando));editar.textContent=editando?"Concluir":"Organizar";
}
editar.addEventListener("click",()=>{editando=!editando;lista.classList.toggle("favoritos-organizando",editando);renderizar()});
// A alça dedicada impede que o toque nos comandos execute uma ação durante a organização.
let arraste=null,ignorarClique=false;
lista.addEventListener("click",e=>{if(ignorarClique){e.preventDefault();e.stopImmediatePropagation();ignorarClique=false}},true);
lista.addEventListener("pointerdown",e=>{
 if(!editando||!e.target.closest(".favoritos-alca")||(e.pointerType==="mouse"&&e.button!==0))return;
 const alvo=e.target.closest(".favoritos-item");if(!alvo)return;
 arraste={id:alvo.dataset.favorito,x:e.clientX,y:e.clientY,pointer:e.pointerId,ativo:false,alterou:false};
 try{lista.setPointerCapture(e.pointerId)}catch{}
});
lista.addEventListener("pointermove",e=>{
 if(!arraste||e.pointerId!==arraste.pointer)return;
 if(Math.hypot(e.clientX-arraste.x,e.clientY-arraste.y)>10)arraste.ativo=true;
 if(!arraste.ativo)return;
 ignorarClique=true;
 const alvo=document.elementFromPoint(e.clientX,e.clientY)?.closest(".favoritos-item");
 if(!alvo||!lista.contains(alvo)||alvo.dataset.favorito===arraste.id)return;
 const origem=favoritos.indexOf(arraste.id),destino=favoritos.indexOf(alvo.dataset.favorito);
 if(origem<0||destino<0)return;
 if(!arraste.alterou){registrarDesfazer();arraste.alterou=true}
 favoritos=reordenar(favoritos,origem,destino);salvar();
 // Não reconstruir a alça durante o arraste: mantém captura e gesto contínuo.
 const movido=lista.querySelector(`[data-favorito="${arraste.id}"]`);
 if(movido){const referencia=lista.querySelector(`[data-favorito="${alvo.dataset.favorito}"]`);if(referencia)lista.insertBefore(movido,destino>origem?referencia.nextSibling:referencia)}
 mensagem("Ordem salva no perfil "+perfis.ativo);
});
function finalizarArraste(){if(arraste){try{lista.releasePointerCapture(arraste.pointer)}catch{}}arraste=null}
lista.addEventListener("pointerup",()=>{const alterou=arraste?.alterou;finalizarArraste();if(alterou)renderizar()});
lista.addEventListener("pointercancel",()=>{const alterou=arraste?.alterou;finalizarArraste();ignorarClique=false;if(alterou)renderizar()});
window.addEventListener("flow-favoritos-alterados",()=>{const novos=carregarPerfis();perfis=novos;favoritos=[...novos.listas[novos.ativo]];renderizar();mensagem("Favoritos atualizados pela busca de ações.")});
let ultimoEstado="";setInterval(()=>{if(document.hidden||editando)return;const atual=String(app.state.approved)+"|"+String(app.state.authenticated)+"|"+String(app.state.somenteVisualizacao);if(atual!==ultimoEstado){ultimoEstado=atual;renderizar()}},1200);
renderizar();
})();
