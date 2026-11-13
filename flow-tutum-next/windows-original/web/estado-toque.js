(()=>{"use strict";
const tela=document.getElementById("viewer");if(!tela)return;
const aviso=document.createElement("div");aviso.className="estado-toque";aviso.hidden=true;aviso.setAttribute("role","status");aviso.setAttribute("aria-live","polite");tela.append(aviso);
let identificador=0;
window.addEventListener("flow-toque-estado",evento=>{
 const {tipo,x,y}=evento.detail||{};
 if(tipo==="finalizado"||tipo==="cancelado"){aviso.hidden=true;return}
 if(tipo!=="pressionando"&&tipo!=="arrastando")return;
 const limites=tela.getBoundingClientRect();
 aviso.style.left=Math.max(20,Math.min(limites.width-20,x-limites.left))+"px";
 aviso.style.top=Math.max(20,Math.min(limites.height-20,y-limites.top))+"px";
 aviso.textContent=tipo==="arrastando"?"Arrastando":"Segure para arrastar";
 aviso.dataset.tipo=tipo;aviso.hidden=false;
 const atual=++identificador;
 if(tipo==="pressionando")setTimeout(()=>{if(atual===identificador&&aviso.dataset.tipo==="pressionando")aviso.hidden=true},900);
});
})();
