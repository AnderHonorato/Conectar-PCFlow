(()=>{"use strict";
const app=window.FlowTutumNext;if(!app)return;
const {state,paint}=app;
const perfis=[
 {id:"leitura",nome:"Leitura",descricao:"Texto nítido e toque seguro",qualidade:1920,toque:"move",sensibilidade:1,ajuste:"fit"},
 {id:"controle",nome:"Controle",descricao:"Resposta equilibrada para uso diário",qualidade:960,toque:"click",sensibilidade:1.5,ajuste:"fit"},
 {id:"economia",nome:"Economia",descricao:"Menos dados para redes instáveis",qualidade:640,toque:"click",sensibilidade:1.25,ajuste:"fit"}
];
const area=document.querySelector(".view-head");if(!area)return;
const sec=document.createElement("section");sec.className="perfis-uso";sec.setAttribute("aria-label","Perfis de uso do controle remoto");
sec.innerHTML='<div class="perfis-cabecalho"><div><strong>Meu jeito de controlar</strong><small>Escolha um perfil para adaptar imagem e toque</small></div><span role="status" aria-live="polite" id="perfil-estado"></span></div><div class="perfis-botoes" role="group" aria-label="Escolher perfil"></div>';
area.insertAdjacentElement("afterend",sec);
const botoes=sec.querySelector(".perfis-botoes"),status=sec.querySelector("#perfil-estado");
for(const perfil of perfis){
 const botao=document.createElement("button");botao.type="button";botao.dataset.perfil=perfil.id;
 botao.innerHTML='<strong></strong><small></small>';botao.querySelector("strong").textContent=perfil.nome;botao.querySelector("small").textContent=perfil.descricao;
 botao.addEventListener("click",()=>aplicar(perfil.id,true));botoes.append(botao);
}
function marcar(id){for(const botao of botoes.children){const ativo=botao.dataset.perfil===id;botao.setAttribute("aria-pressed",String(ativo));}}
function aplicar(id,persistir){
 const p=perfis.find(item=>item.id===id);if(!p)return;
 state.tapBehavior=p.toque;state.sensitivity=p.sensibilidade;
 const toque=document.getElementById("touch-behavior"),sens=document.getElementById("pointer-sensitivity"),qual=document.getElementById("quality");
 if(toque)toque.value=p.toque;if(sens)sens.value=String(p.sensibilidade);
 if(qual&&Number(qual.value)!==p.qualidade){qual.value=String(p.qualidade);qual.dispatchEvent(new Event("change",{bubbles:true}));}
 state.fillMode=p.ajuste;state.zoom=1;state.panX=0;state.panY=0;paint();
 marcar(id);status.textContent="Perfil "+p.nome+" ativo";
 if(persistir){try{localStorage.setItem("flow-next-perfil",id)}catch{}}
}
for(const id of ["touch-behavior","pointer-sensitivity","quality"]){
 document.getElementById(id)?.addEventListener("change",()=>{marcar("");status.textContent="Configuração personalizada";});
}
let salvo="controle";try{salvo=localStorage.getItem("flow-next-perfil")||salvo}catch{}
aplicar(perfis.some(p=>p.id===salvo)?salvo:"controle",false);
})();