/* Teclado avançado: somente comandos suportados pelo contrato existente. */
(function(){
"use strict";
const GRUPOS=[
 {nome:"Navegação",teclas:[["↑","up"],["↓","down"],["←","left"],["→","right"],["Início","home"],["Fim","end"],["Pg↑","pageup"],["Pg↓","pagedown"]]},
 {nome:"Edição",teclas:[["Selecionar tudo","ctrl+a"],["Desfazer","ctrl+z"],["Refazer","ctrl+y"],["Salvar","ctrl+s"],["Localizar","ctrl+f"]]},
 {nome:"Windows",teclas:[["Alternar janela","alt+tab"],["Área de trabalho","win+d"],["Explorador","win+e"],["Bloquear PC","win+l"]]}
];
function autorizado(s){return !!(s&&s.approved&&s.authenticated&&s.sessionReady&&!s.somenteVisualizacao&&!s.stopped)}
function comandoValido(valor){return GRUPOS.some(g=>g.teclas.some(([,codigo])=>codigo===valor))}
if(typeof module!=="undefined"&&module.exports)module.exports={GRUPOS,autorizado,comandoValido};
if(typeof document==="undefined")return;
const app=window.FlowTutumNext,teclado=document.getElementById("keyboard-sheet");
if(!app||!teclado||document.getElementById("teclado-avancado"))return;
const painel=document.createElement("section");painel.id="teclado-avancado";painel.className="teclado-avancado";painel.setAttribute("aria-label","Teclas especiais do Windows");
const cab=document.createElement("div");cab.className="teclado-avancado-cab";
const titulo=document.createElement("strong");titulo.textContent="Teclas especiais";
const alternar=document.createElement("button");alternar.type="button";alternar.textContent="Mostrar atalhos";alternar.setAttribute("aria-expanded","false");
cab.append(titulo,alternar);painel.append(cab);
const corpo=document.createElement("div");corpo.hidden=true;corpo.className="teclado-avancado-corpo";
const retorno=document.createElement("p");retorno.setAttribute("role","status");retorno.setAttribute("aria-live","polite");retorno.className="teclado-avancado-retorno";
let ultimo=0;
for(const grupo of GRUPOS){
 const sec=document.createElement("div");sec.className="teclado-avancado-grupo";
 const h=document.createElement("h3");h.textContent=grupo.nome;sec.append(h);
 const grade=document.createElement("div");grade.className="teclado-avancado-grade";
 for(const [rotulo,codigo] of grupo.teclas){
  const b=document.createElement("button");b.type="button";b.textContent=rotulo;b.dataset.atalho=codigo;
  b.setAttribute("aria-label",rotulo+" no Windows");
  b.addEventListener("click",()=>{
   if(!comandoValido(codigo)||!autorizado(app.state)){retorno.textContent="Autorize a sessão no Windows para usar este atalho.";return}
   if(Date.now()-ultimo<300){retorno.textContent="Aguarde antes de repetir o comando.";return}
   if(!app.wsSend({phase:"key",keys:codigo})){retorno.textContent="Não foi possível enviar. Confira a conexão.";return}
   ultimo=Date.now();retorno.textContent="Enviado, sem confirmação de execução: "+rotulo;
  });
  grade.append(b);
 }
 sec.append(grade);corpo.append(sec);
}
corpo.append(retorno);painel.append(corpo);
alternar.addEventListener("click",()=>{corpo.hidden=!corpo.hidden;alternar.setAttribute("aria-expanded",String(!corpo.hidden));alternar.textContent=corpo.hidden?"Mostrar atalhos":"Ocultar atalhos"});
teclado.append(painel);
})();