(()=>{"use strict";
const app=window.FlowTutumNext,viewer=document.getElementById("viewer"),tela=document.getElementById("screen");
if(!app||!viewer||!tela)return;
const barra=document.createElement("div");barra.className="historico-enquadramento";barra.setAttribute("role","group");barra.setAttribute("aria-label","Histórico de enquadramento");
barra.innerHTML='<button type="button" data-historico="desfazer" aria-label="Voltar ao enquadramento anterior" title="Desfazer enquadramento">↶ <span>Voltar visão</span></button><button type="button" data-historico="refazer" aria-label="Refazer enquadramento" title="Refazer enquadramento">↷ <span>Refazer</span></button><span class="historico-ajuda" role="status" aria-live="polite"></span>';
viewer.append(barra);
const aviso=barra.querySelector(".historico-ajuda");
let historico=null,anterior=null,temporizador=null,restaurando=false;
const foto=()=>({zoom:app.state.zoom,panX:app.state.panX,panY:app.state.panY,fillMode:app.state.fillMode});
const diferente=(a,b)=>a&&b&&(Math.abs(a.zoom-b.zoom)>.001||Math.abs(a.panX-b.panX)>.5||Math.abs(a.panY-b.panY)>.5||a.fillMode!==b.fillMode);
function botoes(){if(!historico)return;const e=historico.estado();barra.querySelector('[data-historico="desfazer"]').disabled=!e.desfazer;barra.querySelector('[data-historico="refazer"]').disabled=!e.refazer}
function registrar(){if(restaurando||!historico)return;const atual=foto();if(!anterior){anterior=atual;return}if(!diferente(anterior,atual))return;clearTimeout(temporizador);temporizador=setTimeout(()=>{historico.registrar(anterior,foto());anterior=foto();botoes()},260)}
function aplicar(estado){if(!estado)return;restaurando=true;Object.assign(app.state,estado);app.paint();window.dispatchEvent(new Event("flow-zoom-alterado"));restaurando=false;anterior=foto();aviso.textContent="Enquadramento restaurado";botoes()}
barra.addEventListener("click",e=>{const botao=e.target.closest("[data-historico]");if(!botao||!historico)return;clearTimeout(temporizador);registrarPendente();const atual=foto();aplicar(botao.dataset.historico==="desfazer"?historico.desfazer(atual):historico.refazer(atual))});
function registrarPendente(){if(anterior&&diferente(anterior,foto())){historico.registrar(anterior,foto());anterior=foto()}}
window.addEventListener("flow-zoom-alterado",registrar);
tela.addEventListener("pointerdown",()=>{if(!app.state.exploring)return;clearTimeout(temporizador);registrarPendente();anterior=foto()},true);
tela.addEventListener("pointerup",()=>{if(app.state.exploring)registrar()},true);
document.getElementById("monitor")?.addEventListener("change",()=>{historico?.limpar();anterior=foto();botoes()});
import("./historico-enquadramento.mjs").then(m=>{historico=m.criarHistoricoEnquadramento();anterior=foto();botoes()}).catch(()=>{barra.hidden=true});
})();
