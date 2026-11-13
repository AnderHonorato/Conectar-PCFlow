import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const fonte=readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const modulo=readFileSync(new URL('../web/pausa-sessao.js',import.meta.url),'utf8');
test('bloqueio de comandos ocorre antes da autorização e envio',()=>{
 assert.match(fonte,/if\(state\.pausaLocal\)return false;/);
 assert.ok(fonte.indexOf('if(state.pausaLocal)return false;')<fonte.indexOf('state.seq=Math.max'));
});
test('pausa cancela arraste e exige retomada explícita',()=>{
 assert.match(modulo,/app\.wsSend\(\{phase:"cancel"\}\)/);
 assert.match(modulo,/botao\.addEventListener\("click"/);
 assert.match(modulo,/visibilitychange/);
 assert.match(modulo,/window\.addEventListener\("offline"/);
});