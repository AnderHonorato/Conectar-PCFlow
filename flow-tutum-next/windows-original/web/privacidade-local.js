(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),pause=document.getElementById("pausa-sessao");
if(!app||!viewer||!pause)return;
const botao=document.createElement("button");botao.type="button";botao.id="privacidade-alternar";botao.textContent="Ocultar tela";botao.setAttribute("aria-pressed","false");
const cortina=document.createElement("div");cortina.id="privacidade-cortina";cortina.hidden=true;cortina.setAttribute("role","region");cortina.setAttribute("aria-label","Tela remota oculta");
const titulo=document.createElement("strong");titulo.textContent="Tela protegida neste celular";
const explicacao=document.createElement("p");explicacao.textContent="Imagem escondida e controle pausado. A sessão do Windows não foi encerrada.";
const retomar=document.createElement("button");retomar.type="button";retomar.textContent="Mostrar tela e retomar";
cortina.append(titulo,explicacao,retomar);viewer.append(cortina);viewer.before(botao);
let oculto=false;
function aplicar(valor){
 if(valor===oculto)return;
 oculto=valor;
 if(valor&&!app.state.pausaLocal)pause.click();
 cortina.hidden=!valor;
 botao.setAttribute("aria-pressed",String(valor));botao.textContent=valor?"Tela oculta":"Ocultar tela";
 viewer.classList.toggle("privacidade-ativa",valor);
 if(!valor&&app.state.pausaLocal&&navigator.onLine!==false)pause.click();
}
botao.addEventListener("click",()=>aplicar(!oculto));retomar.addEventListener("click",()=>aplicar(false));
document.addEventListener("visibilitychange",()=>{if(document.hidden)aplicar(true)});
window.addEventListener("offline",()=>aplicar(true));
window.addEventListener("keydown",e=>{if(e.key==="Escape"&&oculto){e.preventDefault();aplicar(false)}});
})();