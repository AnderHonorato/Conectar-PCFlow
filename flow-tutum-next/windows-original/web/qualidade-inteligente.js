(()=>{"use strict";
const seletor=document.getElementById("quality"),dialogo=document.getElementById("settings-dialog");
if(!seletor||!dialogo)return;
const linha=document.createElement("div");linha.className="qualidade-inteligente";
linha.innerHTML='<label class="qualidade-alternar"><input id="qualidade-automatica" type="checkbox"><span><strong>Qualidade inteligente</strong><small>Ajusta a nitidez conforme o tempo real de captura. Não altera o monitor.</small></span></label><output id="qualidade-relatorio" role="status" aria-live="polite">Modo manual</output><button type="button" id="qualidade-restaurar">Restaurar nitidez</button>';
seletor.closest("label")?.after(linha);
const alternar=linha.querySelector("#qualidade-automatica"),relatorio=linha.querySelector("#qualidade-relatorio"),restaurar=linha.querySelector("#qualidade-restaurar");
const opcoes=[640,960,1280,1920];let amostras=[],ultimaTroca=0,boas=0,ruins=0;
try{alternar.checked=localStorage.getItem("flow-next-qualidade-auto")==="1"}catch{}
function escrever(texto){if(relatorio.textContent!==texto)relatorio.textContent=texto;}
function aplicar(valor,motivo){if(!opcoes.includes(valor)||Number(seletor.value)===valor)return;
 seletor.value=String(valor);seletor.dispatchEvent(new Event("change",{bubbles:true}));ultimaTroca=Date.now();amostras=[];boas=0;ruins=0;escrever(motivo+" · "+valor+" px");
}
alternar.addEventListener("change",()=>{amostras=[];boas=0;ruins=0;ultimaTroca=Date.now();try{localStorage.setItem("flow-next-qualidade-auto",alternar.checked?"1":"0")}catch{}
 escrever(alternar.checked?"Analisando captura…":"Modo manual");});
seletor.addEventListener("change",e=>{if(e.isTrusted&&alternar.checked){alternar.checked=false;alternar.dispatchEvent(new Event("change"));}});
restaurar.addEventListener("click",()=>aplicar(1280,"Nitidez restaurada"));
window.addEventListener("flow-captura-medida",evento=>{
 if(!alternar.checked||document.hidden)return;
 const estado=window.FlowTutumNext?.state;
 if(!estado?.sessionReady||!estado?.authenticated||estado.videoActive||estado.frameError)return;
 const ms=Number(evento.detail?.ms);if(!Number.isFinite(ms)||ms<0||ms>10000)return;
 amostras.push(ms);if(amostras.length>12)amostras.shift();if(amostras.length<8)return;
 const media=amostras.reduce((a,b)=>a+b,0)/amostras.length;
 escrever("Captura média "+Math.round(media)+" ms · "+seletor.value+" px");
 if(Date.now()-ultimaTroca<20000)return;
 if(media>250){ruins++;boas=0;}else if(media<105){boas++;ruins=0;}else{ruins=0;boas=0;}
 const atual=opcoes.indexOf(Number(seletor.value));
 if(ruins>=3&&atual>0)aplicar(opcoes[atual-1],"Conexão lenta: imagem mais leve");
 else if(boas>=5&&atual<opcoes.length-1)aplicar(opcoes[atual+1],"Captura estável: mais nitidez");
});
})();