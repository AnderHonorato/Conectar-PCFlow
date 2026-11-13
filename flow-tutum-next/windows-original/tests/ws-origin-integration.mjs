import assert from 'node:assert/strict';
import { connect } from 'node:net';
const port=Number(process.env.FLOW_TUTUM_TEST_PORT||49981);
if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Porta de teste invalida');
const base='http://127.0.0.1:'+port;
async function waitHealth(){
  for(let i=0;i<45;i++){
    try{const r=await fetch(base+'/api/health',{signal:AbortSignal.timeout(700)});
      if(r.ok){const j=await r.json();assert.equal(j.app,'Flow Tutum Next');return;}}
    catch{}
    await new Promise(resolve=>setTimeout(resolve,300));
  }
  throw Error('Servidor isolado nao iniciou em tempo');
}
function upgrade(origin){
  return new Promise((resolve,reject)=>{
    const socket=connect(port,'127.0.0.1');
    let data='';const timeout=setTimeout(()=>{socket.destroy();reject(Error('Timeout de handshake'))},3000);
    socket.once('error',err=>{clearTimeout(timeout);reject(err)});
    socket.on('data',chunk=>{
      data+=chunk.toString('latin1');
      if(data.includes('\r\n\r\n')){
        clearTimeout(timeout);socket.destroy();
        resolve(Number(/^HTTP\/1\.1 (\d+)/.exec(data)?.[1]));
      }
    });
    socket.once('connect',()=>{
      const headers=[
        'GET /ws/input HTTP/1.1',
        'Host: 127.0.0.1:'+port,
        'Connection: Upgrade','Upgrade: websocket',
        'Sec-WebSocket-Version: 13',
        'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==',
        ...(origin?['Origin: '+origin]:[]),'',''
      ];
      socket.write(headers.join('\r\n'));
    });
  });
}
await waitHealth();
assert.equal(await upgrade('http://evil.example'),403,'Origem externa deve ser recusada');
assert.equal(await upgrade('http://127.0.0.1:'+port),101,'Origem local valida deve conectar');
assert.equal(await upgrade(null),101,'Cliente nativo sem Origin deve conectar');
console.log('WS_ORIGIN_INTEGRATION=3 PASS; health=PASS');
