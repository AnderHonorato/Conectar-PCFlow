/* Backup local de favoritos: não exporta tokens, PINs ou credenciais. */
(function(){
"use strict";
const CHAVE="flow-next-favoritos-perfis-v1";
const PERFIS=["Pessoal","Trabalho","Mídia"];
const IDS=new Set(["touchpad","tela","teclado","explorar","zoom","play","volume","anterior","proxima","volumeMais","volumeMenos","desktop"]);
function limpar(lista){return Array.isArray(lista)?[...new Set(lista.filter(x=>typeof x==="string"&&IDS.has(x)))].slice(0,12):[]}
function validar(dados){
 if(!dados||typeof dados!=="object"||Array.isArray(dados)||dados.formato!=="flow-tutum-favoritos"||dados.versao!==1)throw Error("Arquivo incompatível.");
 if(!dados.listas||typeof dados.listas!=="object"||Array.isArray(dados.listas))throw Error("Perfis ausentes.");
 const listas={};for(const perfil of PERFIS){if(!Array.isArray(dados.listas[perfil]))throw Error("Perfil inválido: "+perfil);listas[perfil]=limpar(dados.listas[perfil])}
 return {ativo:PERFIS.includes(dados.ativo)?dados.ativo:"Pessoal",listas};
}
function exportar(dados){const p=validar({formato:"flow-tutum-favoritos",versao:1,ativo:dados.ativo,listas:dados.listas});return JSON.stringify({formato:"flow-tutum-favoritos",versao:1,...p},null,2)}
if(typeof module!=="undefined"&&module.exports)module.exports={validar,exportar,limpar};
if(typeof document==="undefined")return;
const painel=document.querySelector(".favoritos");if(!painel)return;
const controles=document.createElement("div");controles.className="favoritos-backup";
controles.innerHTML='<button type="button" id="favoritos-exportar">Salvar backup</button><button type="button" id="favoritos-importar">Restaurar backup</button><input type="file" id="favoritos-arquivo" accept=".json,application/json" hidden><p id="favoritos-backup-estado" role="status" aria-live="polite"></p>';
painel.append(controles);
const arquivo=controles.querySelector("#favoritos-arquivo"),estado=controles.querySelector("#favoritos-backup-estado");
controles.querySelector("#favoritos-exportar").addEventListener("click",()=>{
 try{
 const dados=JSON.parse(localStorage.getItem(CHAVE)||"{}");
 const conteudo=exportar({ativo:dados.ativo,listas:dados.listas});
 const url=URL.createObjectURL(new Blob([conteudo],{type:"application/json"}));
 const link=document.createElement("a");link.href=url;link.download="Flow-Tutum-Next-favoritos.json";document.body.append(link);link.click();link.remove();
 setTimeout(()=>URL.revokeObjectURL(url),1000);estado.textContent="Backup preparado. Nenhuma credencial incluída.";
 }catch{estado.textContent="Não foi possível exportar os perfis."}
});
controles.querySelector("#favoritos-importar").addEventListener("click",()=>arquivo.click());
arquivo.addEventListener("change",async()=>{
 const file=arquivo.files?.[0];arquivo.value="";if(!file)return;
 if(file.size>32768){estado.textContent="Arquivo muito grande (limite de 32 KB).";return}
 try{
 const dados=validar(JSON.parse(await file.text()));
 if(!window.confirm("Substituir os favoritos dos três perfis neste aparelho?"))return;
 localStorage.setItem(CHAVE,JSON.stringify(dados));
 localStorage.setItem("flow-next-favoritos-v1",JSON.stringify(dados.listas.Pessoal));
 window.dispatchEvent(new CustomEvent("flow-favoritos-alterados",{detail:{perfil:dados.ativo}}));
 estado.textContent="Perfis restaurados neste aparelho.";
 }catch(e){estado.textContent=e instanceof SyntaxError?"JSON inválido.":"Backup recusado: "+e.message}
});
})();