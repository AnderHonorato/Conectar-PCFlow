(()=>{"use strict";
const app=window.FlowTutumNext;if(!app)return;
const botao=document.createElement("button");
botao.type="button";botao.id="pausa-sessao";botao.setAttribute("aria-pressed","false");botao.textContent="Pausar controle";
const aviso=document.createElement("p");aviso.id="pausa-aviso";aviso.setAttribute("role","status");aviso.setAttribute("aria-live","polite");aviso.hidden=true;
aviso.textContent="Controle pausado neste celular. Nenhum comando será enviado ao Windows.";
document.querySelector(".permission")?.after(aviso);
document.querySelector(".dock")?.after(botao);
function aplicar(pausado){
 if(pausado===app.state.pausaLocal)return;
 if(pausado)app.wsSend({phase:"cancel"});
 app.state.pausaLocal=pausado;
 app.state.pointer=null;app.state.contacts.clear();
 botao.setAttribute("aria-pressed",String(pausado));
 botao.textContent=pausado?"Retomar controle":"Pausar controle";
 aviso.hidden=!pausado;
 document.body.classList.toggle("sessao-pausada",pausado);
}
botao.addEventListener("click",()=>aplicar(!app.state.pausaLocal));
window.addEventListener("offline",()=>aplicar(true));
document.addEventListener("visibilitychange",()=>{if(document.hidden)aplicar(true)});
})();