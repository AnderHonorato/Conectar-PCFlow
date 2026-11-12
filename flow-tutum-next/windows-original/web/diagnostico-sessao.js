/* Painel de diagnóstico: apenas leituras, nunca emite comandos de entrada. */
(function(){
"use strict";
const $=id=>document.getElementById(id);
const painel=$("diagnostico-painel"),abrir=$("diagnostico-abrir"),fechar=$("diagnostico-fechar");
if(!painel||!abrir||!fechar)return;
let ativo=false,contador=0,controlador=null;
const formatar=(ms)=>Number.isFinite(ms)?Math.round(ms)+" ms":"—";
function linha(id,valor){const el=$(id);if(el)el.textContent=valor;}
async function medir(){
 if(!ativo||document.hidden)return;
 const numero=++contador;controlador?.abort();controlador=new AbortController();
 const timeout=setTimeout(()=>controlador.abort(),3500);
 const inicio=performance.now();
 try{
  const resposta=await fetch("/api/health",{cache:"no-store",signal:controlador.signal,credentials:"same-origin"});
  if(numero!==contador||!ativo)return;
  linha("diagnostico-rede",resposta.ok?"Servidor responde":"HTTP "+resposta.status);
  linha("diagnostico-tempo",formatar(performance.now()-inicio));
  linha("diagnostico-orientacao",innerWidth>innerHeight?"Paisagem":"Retrato");
  const permissao=$("permission-title")?.textContent?.trim();
  linha("diagnostico-permissao",permissao||"Não informado");
  linha("diagnostico-video",$("stream-state")?.textContent?.trim()||"Não informado");
  linha("diagnostico-hora",new Date().toLocaleTimeString("pt-BR"));
 }catch{
  if(numero!==contador||!ativo)return;
  linha("diagnostico-rede",navigator.onLine===false?"Dispositivo sem rede":"Servidor sem resposta");
  linha("diagnostico-tempo","—");
  linha("diagnostico-permissao","Não confirmado");
  linha("diagnostico-video",$("stream-state")?.textContent?.trim()||"Indisponível");
 }finally{clearTimeout(timeout);}
}
function alternar(valor){
 ativo=valor;painel.hidden=!valor;abrir.setAttribute("aria-expanded",String(valor));
 if(!valor){contador++;controlador?.abort();abrir.focus();}else medir();
}
abrir.addEventListener("click",()=>alternar(!ativo));
fechar.addEventListener("click",()=>alternar(false));
painel.addEventListener("keydown",e=>{if(e.key==="Escape")alternar(false)});
document.addEventListener("visibilitychange",()=>{if(document.hidden)controlador?.abort();else if(ativo)medir()});
setInterval(()=>{if(ativo&&!document.hidden)medir()},5000);
})();
