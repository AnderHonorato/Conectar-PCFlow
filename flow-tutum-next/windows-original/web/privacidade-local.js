(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),pause=document.getElementById("pausa-sessao");
if(!app||!viewer||!pause)return;
const botao=document.createElement("button");botao.type="button";botao.id="privacidade-alternar";botao.textContent="Ocultar tela";botao.setAttribute("aria-pressed","false");
const cortina=document.createElement("div");cortina.id="privacidade-cortina";cortina.hidden=true;cortina.setAttribute("role","region");cortina.setAttribute("aria-label","Tela remota oculta");
const titulo=document.createElement("strong");titulo.textContent="Tela protegida neste celular";
const explicacao=document.createElement("p");explicacao.textContent="Imagem escondida e controle pausado. A sessão do Windows não foi encerrada.";
const retomar=document.createElement("button");retomar.type="button";retomar.textContent="Mostrar tela";
const estado=document.createElement("p");estado.setAttribute("role","status");estado.setAttribute("aria-live","polite");
cortina.append(titulo,explicacao,retomar,estado);viewer.append(cortina);viewer.before(botao);
let oculto=false,pausaCriadaPelaCortina=false;
function aplicar(valor,automatico=false){
 if(valor===oculto)return;
 oculto=valor;
 if(valor){
   // Nunca reivindicar uma pausa que já estava ativa antes de ocultar.
   pausaCriadaPelaCortina=!app.state.pausaLocal;
   if(pausaCriadaPelaCortina)pause.click();
   estado.textContent=automatico?"Ocultada automaticamente. Toque em Mostrar tela para voltar.":"Tela oculta até sua confirmação.";
 }else{
   // Mostrar a imagem não equivale a autorizar entrada remota.
   // Em retorno automático de segundo plano, manter a pausa até gesto explícito.
   if(pausaCriadaPelaCortina&&!document.hidden&&navigator.onLine!==false&&app.state.pausaLocal)pause.click();
   pausaCriadaPelaCortina=false;
   estado.textContent="";
 }
 cortina.hidden=!valor;
 botao.setAttribute("aria-pressed",String(valor));botao.textContent=valor?"Tela oculta":"Ocultar tela";
 viewer.classList.toggle("privacidade-ativa",valor);
}
botao.addEventListener("click",()=>aplicar(!oculto));retomar.addEventListener("click",()=>aplicar(false));
document.addEventListener("visibilitychange",()=>{if(document.hidden)aplicar(true,true)});
window.addEventListener("offline",()=>aplicar(true,true));
window.addEventListener("keydown",e=>{if(e.key==="Escape"&&oculto){e.preventDefault();aplicar(false)}});
})();
