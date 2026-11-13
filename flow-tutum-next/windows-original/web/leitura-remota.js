(()=>{"use strict";
const app=window.FlowTutumNext,canvas=document.getElementById("screen"),viewer=document.getElementById("viewer");
if(!app||!canvas||!viewer)return;
const CHAVE="flow-next-leitura-v1";
const LIMITES={contraste:[75,175,100],brilho:[75,150,100],saturacao:[0,150,100]};
function limitar(nome,valor){const [min,max,padrao]=LIMITES[nome];const n=Number(valor);return Number.isFinite(n)?Math.min(max,Math.max(min,Math.round(n))):padrao}
function normalizar(obj){const dados={};for(const nome of Object.keys(LIMITES))dados[nome]=limitar(nome,obj?.[nome]);return dados}
let valores;try{valores=normalizar(JSON.parse(localStorage.getItem(CHAVE)||"{}"))}catch{valores=normalizar({})}
const painel=document.createElement("section");painel.className="leitura-remota";painel.setAttribute("aria-label","Ajustes de leitura da tela");
painel.innerHTML='<div class="leitura-cabecalho"><div><strong>Leitura confortável</strong><small>Ajustes apenas no celular, sem alterar o Windows</small></div><button type="button" id="leitura-alternar" aria-expanded="false" aria-controls="leitura-ajustes">Ajustar</button></div><div id="leitura-ajustes" hidden><label>Contraste <output id="leitura-contraste-valor"></output><input id="leitura-contraste" type="range" min="75" max="175" step="5"></label><label>Brilho <output id="leitura-brilho-valor"></output><input id="leitura-brilho" type="range" min="75" max="150" step="5"></label><label>Cor <output id="leitura-saturacao-valor"></output><input id="leitura-saturacao" type="range" min="0" max="150" step="5"></label><div class="leitura-acoes"><button type="button" id="leitura-texto">Texto nítido</button><button type="button" id="leitura-noite">Noite</button><button type="button" id="leitura-restaurar">Restaurar</button></div><p id="leitura-retorno" role="status" aria-live="polite"></p></div>';
viewer.insertAdjacentElement("afterend",painel);
const ajustes=painel.querySelector("#leitura-ajustes"),alternar=painel.querySelector("#leitura-alternar");
function aplicar(salvar=true){canvas.style.filter=`contrast(${valores.contraste}%) brightness(${valores.brilho}%) saturate(${valores.saturacao}%)`;
for(const nome of Object.keys(LIMITES)){painel.querySelector("#leitura-"+nome).value=String(valores[nome]);painel.querySelector("#leitura-"+nome+"-valor").textContent=valores[nome]+"%"}
if(salvar)try{localStorage.setItem(CHAVE,JSON.stringify(valores))}catch{painel.querySelector("#leitura-retorno").textContent="Preferência não pôde ser salva"}
}
alternar.addEventListener("click",()=>{ajustes.hidden=!ajustes.hidden;alternar.setAttribute("aria-expanded",String(!ajustes.hidden));alternar.textContent=ajustes.hidden?"Ajustar":"Fechar"});
for(const nome of Object.keys(LIMITES))painel.querySelector("#leitura-"+nome).addEventListener("input",e=>{valores[nome]=limitar(nome,e.target.value);aplicar()});
function perfil(dados,mensagem){valores=normalizar(dados);aplicar();painel.querySelector("#leitura-retorno").textContent=mensagem}
painel.querySelector("#leitura-texto").addEventListener("click",()=>perfil({contraste:140,brilho:110,saturacao:65},"Perfil de leitura aplicado"));
painel.querySelector("#leitura-noite").addEventListener("click",()=>perfil({contraste:115,brilho:75,saturacao:75},"Brilho reduzido para ambientes escuros"));
painel.querySelector("#leitura-restaurar").addEventListener("click",()=>perfil({},"Imagem original restaurada"));
aplicar(false);
if(typeof module!=="undefined"&&module.exports)module.exports={normalizar,limitar};
})();