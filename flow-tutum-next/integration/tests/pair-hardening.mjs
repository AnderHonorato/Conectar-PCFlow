import assert from 'node:assert/strict';

// Rodar somente contra instancia de TESTE isolada do Next, sem modificar a instalada.
// Ex.: FLOW_TUTUM_TEST_URL=http://127.0.0.1:49901 node tests/pair-hardening.mjs
const base=process.env.FLOW_TUTUM_TEST_URL;
if(!base || !/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base))
  throw new Error('Defina FLOW_TUTUM_TEST_URL para servidor de teste local isolado.');
const tests=[
  {name:'JSON malformado',body:'{"pin":',expected:400},
  {name:'Corpo maior que 4 KiB',body:JSON.stringify({pin:'000000',name:'x'.repeat(5000)}),expected:413},
  {name:'PIN incorreto',body:JSON.stringify({pin:'000000',name:'Teste'}),expected:401}
];
for(const t of tests){
  const response=await fetch(base+'/api/pair',{
    method:'POST',headers:{'content-type':'application/json'},body:t.body,
    signal:AbortSignal.timeout(4000)
  });
  assert.equal(response.status,t.expected,`${t.name}: esperado ${t.expected}, recebido ${response.status}`);
  console.log('PASS',t.name);
}
console.log('PAIR_HARDENING=3 TESTES PASSARAM');
