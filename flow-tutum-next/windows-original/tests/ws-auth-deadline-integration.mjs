import assert from 'node:assert/strict';
const port=Number(process.env.FLOW_TUTUM_TEST_PORT||49981);
if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Porta invalida');
const url='ws://127.0.0.1:'+port+'/ws/input';
const started=Date.now();
const ws=new WebSocket(url);
const result=await new Promise((resolve,reject)=>{
  const watchdog=setTimeout(()=>{ws.close();reject(Error('Socket sem autenticacao permaneceu aberto > 16s'));},16000);
  ws.addEventListener('open',()=>{});
  ws.addEventListener('error',()=>{}); // encerramento abrupto pode gerar erro antes do close
  ws.addEventListener('close',event=>{clearTimeout(watchdog);resolve({elapsed:Date.now()-started,code:event.code});});
});
assert.ok(result.elapsed>=8000 && result.elapsed<16000,
  'Deadline de autenticacao deveria encerrar socket em aproximadamente 10s: '+result.elapsed+'ms');
console.log('WS_AUTH_DEADLINE=PASS',result.elapsed+'ms','close='+result.code);
