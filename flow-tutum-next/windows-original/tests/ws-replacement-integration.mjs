import assert from 'node:assert/strict';
const port=Number(process.env.FLOW_TUTUM_TEST_PORT||49981);
const pin=process.env.FLOW_TUTUM_TEST_PIN;
if(!/^\d{6}$/.test(pin||''))throw Error('PIN isolado nao informado');
const base='http://127.0.0.1:'+port;
const paired=await fetch(base+'/api/pair',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pin,name:'Teste de reconexao'})});
assert.equal(paired.status,200,'Pareamento isolado');
const {token}=await paired.json();
const sockets=[];
function open(){
 return new Promise((resolve,reject)=>{
  const ws=new WebSocket('ws://127.0.0.1:'+port+'/ws/input');
  const timeout=setTimeout(()=>reject(Error('Timeout abertura WebSocket')),4000);
  ws.addEventListener('open',()=>{clearTimeout(timeout);sockets.push(ws);resolve(ws)},{once:true});
  ws.addEventListener('error',()=>{clearTimeout(timeout);reject(Error('Falha abertura WebSocket'))},{once:true});
 });
}
function nextMessage(ws){
 return new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>reject(Error('Timeout resposta WS')),4000);
  ws.addEventListener('message',e=>{clearTimeout(timer);resolve(JSON.parse(e.data))},{once:true});
 });
}
try{
 const first=await open();
 let reply=nextMessage(first);first.send(JSON.stringify({token,seq:0}));assert.equal((await reply).ready,true);
 const second=await open();
 reply=nextMessage(second);second.send(JSON.stringify({token,seq:0}));assert.equal((await reply).ready,true);
 const closed=new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>reject(Error('Canal substituido nao encerrou')),4000);
  first.addEventListener('close',e=>{clearTimeout(timer);resolve(e.code)},{once:true});
 });
 first.send(JSON.stringify({seq:1,phase:'noop'}));
 assert.equal(await closed,1008,'Canal antigo deve fechar com violacao de politica');
 reply=nextMessage(second);second.send(JSON.stringify({seq:1,phase:'noop'}));
 const current=await reply;
 assert.notEqual(current.error,'Conexao substituida.','Canal atual deve manter propriedade');
 console.log('WS_REPLACEMENT=PASS: antigo fechado 1008, novo ativo');
}finally{
 for(const ws of sockets)try{ws.close()}catch{}
 await fetch(base+'/api/unpair',{method:'POST',headers:{Authorization:'Bearer '+token}}).catch(()=>{});
}
