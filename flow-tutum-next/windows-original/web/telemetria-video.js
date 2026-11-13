/* Telemetria de quadros efetivamente apresentados, sem estimar latência de rede. */
(function(){
"use strict";
const $=id=>document.getElementById(id);
const painel=$("diagnostico-painel"),saida=$("video-telemetria");
if(!painel||!saida)return;
let tempos=[],ultimo=0,modo="aguardando",interrupcoes=0,ultimoEvento=0,ativo=true;
const MAX=120;
function registrar(timestamp,origem){
 if(!ativo||document.hidden)return;
 if(modo!==origem){tempos=[];ultimo=0;modo=origem;}
 if(ultimo>0){
  const delta=timestamp-ultimo;
  if(delta>0&&delta<30000){tempos.push(delta);if(tempos.length>MAX)tempos.shift();if(delta>1500)interrupcoes++;}
 }
 ultimo=timestamp;ultimoEvento=performance.now();
 atualizar();
}
function atualizar(){
 const atual=performance.now(),idade=atual-ultimoEvento;
 if(!ultimoEvento){saida.textContent="Aguardando quadros reais";return;}
 if(idade>2500){saida.textContent="Vídeo interrompido · "+(idade/1000).toFixed(1)+" s sem quadro";return;}
 if(!tempos.length){saida.textContent=modo.toUpperCase()+" · coletando amostras";return;}
 const ordenados=[...tempos].sort((a,b)=>a-b);
 const mediana=ordenados[Math.floor((ordenados.length-1)/2)];
 const fps=1000/mediana;
 saida.textContent=modo.toUpperCase()+" · "+fps.toFixed(1)+" FPS observados · "+interrupcoes+" pausas (>1,5 s)";
}
window.addEventListener("flow-quadro-apresentado",e=>{
 const d=e.detail||{};
 if((d.origem==="jpeg"||d.origem==="h264")&&Number.isFinite(d.tempo))registrar(d.tempo,d.origem);
});
document.addEventListener("visibilitychange",()=>{
 if(document.hidden){tempos=[];ultimo=0;ultimoEvento=0;modo="aguardando";}
 else atualizar();
});
$("diagnostico-limpar")?.addEventListener("click",()=>{tempos=[];ultimo=0;interrupcoes=0;ultimoEvento=0;modo="aguardando";atualizar();});
setInterval(()=>{if(!document.hidden&&!painel.hidden)atualizar();},1000);
atualizar();
})();
