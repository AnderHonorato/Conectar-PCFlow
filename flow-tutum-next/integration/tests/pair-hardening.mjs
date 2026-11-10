import assert from 'node:assert/strict';

// Testes destrutivos de limite de tentativas: somente servidor Next descartável,
// em loopback e porta dedicada. Nunca apontar para o serviço instalado do usuário.
const base=process.env.FLOW_TUTUM_TEST_URL;
if(!base)throw new Error('Defina FLOW_TUTUM_TEST_URL para a instância isolada.');
let url;
try{url=new URL(base);}catch{throw new Error('FLOW_TUTUM_TEST_URL inválida.');}
if(url.protocol!=='http:'||!['127.0.0.1','localhost'].includes(url.hostname)||
   !url.port||url.port==='49900'||url.username||url.password||
   url.pathname!=='/'||url.search||url.hash)
  throw new Error('Use http://127.0.0.1:<porta-de-teste> (nunca a porta padrão 49900).');

async function pair(body,{chunked=false}={}){
  const headers={'content-type':'application/json'};
  const response=await fetch(new URL('/api/pair',url),{
    method:'POST',headers,
    body:chunked?new ReadableStream({start(controller){
      controller.enqueue(new TextEncoder().encode(body));
      controller.close();
    }}):body,
    ...(chunked?{duplex:'half'}:{}),
    signal:AbortSignal.timeout(4000)
  });
  return response;
}
const invalidPin=JSON.stringify({pin:'000000',name:'Teste de segurança'});
const cases=[
  {name:'JSON malformado',body:'{"pin":',expected:400},
  {name:'Corpo maior que 4 KiB com Content-Length',body:JSON.stringify({pin:'000000',name:'x'.repeat(5000)}),expected:413},
  {name:'Corpo maior que 4 KiB sem Content-Length',body:JSON.stringify({pin:'000000',name:'x'.repeat(5000)}),expected:413,chunked:true},
  {name:'PIN incorreto',body:invalidPin,expected:401}
];
let passed=0;
for(const t of cases){
  const response=await pair(t.body,{chunked:t.chunked});
  assert.equal(response.status,t.expected,`${t.name}: esperado ${t.expected}, recebido ${response.status}`);
  assert.equal(response.headers.has('set-cookie'),false,`${t.name}: não criar sessão`);
  console.log('PASS',t.name);passed++;
}
// A tentativa acima já consumiu 1 falha; o limite atual do servidor é 6/10 min.
// O sétimo PIN incorreto deve receber 429 e nunca criar credenciais.
for(let attempt=2;attempt<=7;attempt++){
  const response=await pair(invalidPin);
  const expected=attempt<=6?401:429;
  assert.equal(response.status,expected,`Tentativa ${attempt}: esperado ${expected}, recebido ${response.status}`);
  if(expected===429){
    const body=await response.text();
    assert.equal(/"token"\s*:/.test(body),false,'Rate limit não pode emitir token');
  }
  console.log('PASS',`limite de tentativas ${attempt}/7 => HTTP ${expected}`);passed++;
}
console.log(`PAIR_HARDENING=${passed} TESTES PASSARAM`);
