import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const begin=source.indexOf('let recovering=false,lastRecovery=0,discoveryReady=false;');
const end=source.indexOf('window.addEventListener("offline",markNetworkOffline);',begin);
assert.ok(begin>0&&end>begin,'Funcao de recuperacao deve existir no frontend real');
const snippet=source.slice(begin,end);
let now=10000,requests=0,loadCalls=0,dialogs=[],toasts=[],fail=false;
const state={stopped:false,info:null,token:'token-valido',localSupported:false};
const context={
 state,navigator:{onLine:true},document:{hidden:false},Date:{now:()=>now},
 AbortSignal:{timeout:()=>({})},
 fetch:async()=>{requests++;if(fail)throw Error('Wi-Fi desconectado');return {ok:true,json:async()=>({localDirect:true})}},
 loadInfo:async()=>{loadCalls++;state.info={device:'PC'}},
 showDialog:id=>dialogs.push(id),toast:(...args)=>toasts.push(args)
};
vm.createContext(context);
vm.runInContext(snippet+'\nthis.recoverConnection=recoverConnection;',context);
await context.recoverConnection();
assert.equal(requests,1);
assert.equal(loadCalls,1);
assert.equal(state.localSupported,true);
await context.recoverConnection();
assert.equal(requests,1,'Nao deve redescobrir com sessao estabelecida');
state.info=null;now+=6000;fail=true;
await context.recoverConnection();
assert.equal(requests,2);
assert.ok(toasts.some(([s])=>s.includes('reconectando')),'Falha deve ser visivel');
fail=false;now+=6000;
await context.recoverConnection();
assert.equal(requests,3);
assert.equal(loadCalls,2,'Deve recuperar apos a rede voltar');
state.info=null;state.token='';now+=6000;
await context.recoverConnection();
assert.equal(dialogs.at(-1),'discovery-dialog');
const stableRequests=requests;
now+=6000;await context.recoverConnection();
assert.equal(requests,stableRequests,'Descoberta sem token nao deve abrir dialogos repetidos');
context.navigator.onLine=false;now+=6000;await context.recoverConnection();
assert.equal(requests,stableRequests,'Nao consulta rede durante estado offline');
context.navigator.onLine=true;
state.stopped=true;now+=6000;await context.recoverConnection();
assert.equal(requests,stableRequests,'Desconexao voluntaria bloqueia reconexao');
console.log('RECUPERACAO_INICIAL=PASS: falha, retorno, token, descoberta, antirepeticao e desconexao');
