// Teste de regressão: a cortina nunca pode reativar uma sessão pausada manualmente.
const test=require("node:test"),assert=require("node:assert/strict"),vm=require("node:vm"),fs=require("node:fs"),path=require("node:path");
function simular(pausaInicial){
 const elementos=new Map(),eventos={};
 function elemento(){return {hidden:false,style:{},classList:{toggle(){}},setAttribute(){},append(){},before(item){this.beforeArgument=item;},addEventListener(nome,fn){this[nome]=fn},click(){this.clicks=(this.clicks||0)+1;if(this===pause)app.state.pausaLocal=!app.state.pausaLocal}}}
 const pause=elemento(),viewer=elemento(),app={state:{pausaLocal:pausaInicial}};
 elementos.set("viewer",viewer);elementos.set("pausa-sessao",pause);
 const document={hidden:false,createElement:elemento,getElementById:id=>elementos.get(id),addEventListener:(n,f)=>eventos[n]=f};
 const window={FlowTutumNext:app,addEventListener:(n,f)=>eventos[n]=f};
 const codigo=fs.readFileSync(path.join(__dirname,"../web/privacidade-local.js"),"utf8");
 vm.runInNewContext(codigo,{window,document,navigator:{onLine:true}});
 const botao=viewer.beforeArgument; // o stub guarda o botão inserido
 return {app,pause,botao,eventos,viewer};
}
test("não retoma pausa anterior ao ocultamento",()=>{
 const s=simular(true);s.botao.click();assert.equal(s.pause.clicks||0,0);s.botao.click();assert.equal(s.app.state.pausaLocal,true);
});
test("retoma apenas a pausa que a cortina criou após confirmação",()=>{
 const s=simular(false);s.botao.click();assert.equal(s.app.state.pausaLocal,true);s.botao.click();assert.equal(s.app.state.pausaLocal,false);
});
test("retorno do segundo plano não reativa entrada automaticamente",()=>{
 const s=simular(false);s.viewer.hidden=false;s.eventos.visibilitychange(); // documento continua visível: nenhum efeito
 assert.equal(s.app.state.pausaLocal,false);
});
