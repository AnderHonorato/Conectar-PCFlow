(()=>{"use strict";
const catalogo=[
{id:"touchpad",nome:"Touchpad",tipo:"local",executar:a=>a.setMode("trackpad")},
{id:"tela",nome:"Tela remota",tipo:"local",executar:a=>a.setMode("direct")},
{id:"teclado",nome:"Teclado",tipo:"local",executar:()=>document.getElementById("open-keyboard")?.click()},
{id:"explorar",nome:"Explorar",tipo:"local",executar:()=>document.getElementById("explore-toggle")?.click()},
{id:"zoom",nome:"Ampliar",tipo:"local",executar:()=>document.getElementById("zoom-in")?.click()},
{id:"play",nome:"Reproduzir / pausar",tipo:"remoto",comando:{phase:"key",keys:"media-play-pause"}},
{id:"volume",nome:"Silenciar",tipo:"remoto",comando:{phase:"key",keys:"volume-mute"}},
{id:"desktop",nome:"Área de trabalho",tipo:"remoto",comando:{phase:"key",keys:"win+d"}}
];
const CHAVE="flow-next-favoritos-v1",PADRAO=["touchpad","teclado","explorar","play"];
const ids=new Set(catalogo.map(x=>x.id));
function normalizar(valor){return Array.isArray(valor)?[...new Set(valor.filter(x=>typeof x==="string"&&ids.has(x)))].slice(0,8):[...PADRAO]}
function carregar(){try{const raw=localStorage.getItem(CHAVE);return raw===null?[...PADRAO]:normalizar(JSON.parse(raw))}catch{return [...PADRAO]}}
function guardar(v){try{localStorage.setItem(CHAVE,JSON.stringify(v))}catch{}}
if(typeof module!=="undefined"&&module.exports)module.exports={normalizar,PADRAO,catalogo};
if(typeof document==="undefined")return;
const app=window.FlowTutumNext,ancora=document.querySelector(".dock");if(!app||!ancora)return;
let favoritos=carregar(),editando=false;
const painel=document.createElement("section");painel.className="favoritos";painel.setAttribute("aria-label","Ações favoritas");
painel.innerHTML='<div class="favoritos-topo"><strong>Favoritos</strong><button type="button" id="favoritos-editar" aria-pressed="false">Organizar</button></div><div class="favoritos-acoes" id="favoritos-acoes"></div><p id="favoritos-retorno" role="status" aria-live="polite"></p><div id="favoritos-catalogo" class="favoritos-catalogo" hidden></div>';
ancora.insertAdjacentElement("beforebegin",painel);
const lista=painel.querySelector("#favoritos-acoes"),catalogo=painel.querySelector("#favoritos-catalogo"),retorno=painel.querySelector("#favoritos-retorno"),editar=painel.querySelector("#favoritos-editar");
function mensagem(t){retorno.textContent=t}
function mover(id,delta){const pos=favoritos.indexOf(id),dest=pos+delta;if(pos<0||dest<0||dest>=favoritos.length)return;[favoritos[pos],favoritos[dest]]=[favoritos[dest],favoritos[pos]];guardar(favoritos);renderizar()}
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
 lista.replaceChildren();catalogo.replaceChildren();
 for(const id of favoritos){
  const item=catalogo.find(x=>x.id===id);if(!item)continue;
  const grupo=document.createElement("div");grupo.className="favoritos-item";
  grupo.append(botao(item.nome,()=>acionar(item)));
  if(editando){grupo.append(botao("←",()=>mover(id,-1),"Mover "+item.nome+" para esquerda"),botao("→",()=>mover(id,1),"Mover "+item.nome+" para direita"),botao("×",()=>{favoritos=favoritos.filter(x=>x!==id);guardar(favoritos);renderizar()},"Remover "+item.nome))}
  lista.append(grupo);
 }
 if(!favoritos.length)lista.append("Nenhum favorito. Toque em Organizar para adicionar.");
 for(const item of catalogo.filter(x=>!favoritos.includes(x.id)))catalogo.append(botao("+ "+item.nome,()=>{favoritos.push(item.id);guardar(favoritos);renderizar()},"Adicionar "+item.nome));
 catalogo.hidden=!editando;editar.setAttribute("aria-pressed",String(editando));editar.textContent=editando?"Concluir":"Organizar";
}
editar.addEventListener("click",()=>{editando=!editando;renderizar()});
renderizar();
})();
