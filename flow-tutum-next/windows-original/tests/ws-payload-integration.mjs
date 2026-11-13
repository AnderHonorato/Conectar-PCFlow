import assert from 'node:assert/strict';
const port=Number(process.env.FLOW_TUTUM_TEST_PORT||49981);
if(!Number.isInteger(port)||port<1024||port>65535||port===49900)
  throw Error('Use somente porta isolada de testes');
const url='ws://127.0.0.1:'+port+'/ws/input';
async function expectClose(payload,expected,label){
  const ws=new WebSocket(url);
  const code=await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{ws.close();reject(Error(label+': timeout'))},5000);
    ws.addEventListener('open',()=>ws.send(payload),{once:true});
    ws.addEventListener('close',e=>{clearTimeout(timer);resolve(e.code)},{once:true});
    ws.addEventListener('error',()=>{}, {once:true});
  });
  assert.equal(code,expected,label+': codigo de encerramento');
  console.log('PASS',label,code);
}
const health=await fetch('http://127.0.0.1:'+port+'/api/health',{signal:AbortSignal.timeout(3000)});
assert.equal(health.status,200);
assert.equal((await health.json()).app,'Flow Tutum Next');
await expectClose('{invalido',1007,'JSON invalido');
await expectClose(new Uint8Array([1,2,3]),1003,'Quadro binario');
await expectClose('x'.repeat(8193),1009,'Mensagem acima de 8KiB');
console.log('WS_PAYLOAD_INTEGRATION=3 PASS');
