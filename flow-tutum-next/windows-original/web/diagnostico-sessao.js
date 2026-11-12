/* Diagnóstico de rede em tempo real: somente leituras, sem comandos remotos. */
(function(){
"use strict";
const $=id=>document.getElementById(id);
const painel=$("diagnostico-painel"),abrir=$("diagnostico-abrir"),fechar=$("diagnostico-fechar");
if(!painel||!abrir||!fechar)return;
let ativo=false,sequencia=0,controlador=null,temporizador=null;
const amostras=[],MAX=24;
const texto=(id,valor)=>{const el=$(id);if(el)el.textContent=valor;};
const tempo=ms=>Number.isFinite(ms)?Math.round(ms)+" ms":"—";
function percentil(valores,p){if(!valores.length)return NaN;const ordenados=[...valores].sort((a,b)=>a-b);return ordenados[Math.ceil(p*ordenados.length)-1];}
function atualizarResumo(){
 const validas=amostras.filter(a=>a.ok).map(a=>a.ms);
 const falhas=amostras.length-validas.length;
 texto("diagnostico-amostras",amostras.length+" leituras");
 texto("diagnostico-mediana",tempo(percentil(validas,.5)));
 texto("diagnostico-p95",tempo(percentil(validas,.95)));
 texto("diagnostico-falhas",amostras.length?Math.round(100*falhas/amostras.length)+"%":"—");
 const grafico=$("diagnostico-historico");if(!grafico)return;
 grafico.replaceChildren();
 const limite=Math.max(100,...validas,1);
 for(const a of amostras){
  const barra=document.createElement("span");
  barra.className="diagnostico-barra"+(a.ok?"":" diagnostico-barra-falha");
  barra.style.height=(a.ok?Math.max(5,Math.min(100,100*a.ms/limite)):100)+"%";
  barra.title=a.ok?tempo(a.ms):"Sem resposta";
  grafico.append(barra);
 }
 texto("diagnostico-alerta",falhas>=3?"Instabilidade detectada: confira o Wi-Fi e a disponibilidade do servidor.":validas.length?"Medição HTTP local; não representa atraso de vídeo ou mouse.":"Aguardando respostas do servidor.");
}
function registrar(ok,ms){
 amostras.push({ok,ms});if(amostras.length>MAX)amostras.shift();atualizarResumo();
}
async function medir(){
 if(!ativo||document.hidden)return;
 const numero=++sequencia;controlador?.abort();
 const atual=new AbortController();controlador=atual;
 const timeout=setTimeout(()=>atual.abort(),3500),inicio=performance.now();
 try{
  const resposta=await fetch("/api/health",{cache:"no-store",signal:atual.signal,credentials:"same-origin"});
  if(numero!==sequencia||!ativo||document.hidden)return;
  const duracao=performance.now()-inicio;
  registrar(resposta.ok,duracao);
  texto("diagnostico-rede",resposta.ok?"Servidor responde":"HTTP "+resposta.status);
  texto("diagnostico-tempo",tempo(duracao));
 }catch{
  if(numero!==sequencia||!ativo||document.hidden)return;
  registrar(false,NaN);
  texto("diagnostico-rede",navigator.onLine===false?"Dispositivo sem rede":"Servidor sem resposta");
  texto("diagnostico-tempo","—");
 }finally{
  clearTimeout(timeout);
  if(numero===sequencia&&ativo&&!document.hidden){
   texto("diagnostico-orientacao",innerWidth>innerHeight?"Paisagem":"Retrato");
   texto("diagnostico-permissao",$("permission-title")?.textContent?.trim()||"Não informado");
   texto("diagnostico-video",$("stream-state")?.textContent?.trim()||"Não informado");
   texto("diagnostico-hora",new Date().toLocaleTimeString("pt-BR"));
  }
 }
}
function agendar(){
 clearInterval(temporizador);temporizador=null;
 if(ativo&&!document.hidden){medir();temporizador=setInterval(medir,5000);}
}
function alternar(valor){
 ativo=valor;painel.hidden=!valor;abrir.setAttribute("aria-expanded",String(valor));
 if(!valor){sequencia++;controlador?.abort();clearInterval(temporizador);temporizador=null;abrir.focus();}
 else agendar();
}
abrir.addEventListener("click",()=>alternar(!ativo));
fechar.addEventListener("click",()=>alternar(false));
painel.addEventListener("keydown",e=>{if(e.key==="Escape")alternar(false)});
document.addEventListener("visibilitychange",()=>{if(document.hidden){sequencia++;controlador?.abort();clearInterval(temporizador);temporizador=null;}else if(ativo)agendar();});
$("diagnostico-limpar")?.addEventListener("click",()=>{amostras.length=0;atualizarResumo();});
atualizarResumo();
})();
