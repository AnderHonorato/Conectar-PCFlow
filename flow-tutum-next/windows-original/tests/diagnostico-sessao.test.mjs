import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';
const base=join(dirname(fileURLToPath(import.meta.url)),'..');
const html=readFileSync(join(base,'web/index.html'),'utf8');
const js=readFileSync(join(base,'web/diagnostico-sessao.js'),'utf8');
const cs=readFileSync(join(base,'src/Program.cs'),'utf8');
test('diagnóstico ligado à interface e aos recursos HTTP',()=>{
 for(const id of ['diagnostico-abrir','diagnostico-painel','diagnostico-fechar','diagnostico-rede','diagnostico-tempo','diagnostico-permissao','diagnostico-video','diagnostico-orientacao'])assert.match(html,new RegExp('id="'+id+'"'));
 for(const nome of ['diagnostico-sessao.js','diagnostico-sessao.css']){assert.ok(html.includes('/'+nome));assert.ok(cs.includes('"'+nome+'"'))}
});
test('diagnóstico usa leitura HTTP sem transmitir entradas',()=>{
 assert.match(js,/fetch\("\/api\/health"/);
 assert.match(js,/performance\.now\(\)/);
 assert.match(js,/AbortController/);
 assert.doesNotMatch(js,/wsSend|\/ws\/input|phase:"(tap|key|down)"/);
});
test('painel tem semântica e explica a medição',()=>{
 assert.match(html,/aria-expanded="false"/);
 assert.match(html,/tempo HTTP não representa a latência de vídeo/i);
 assert.match(js,/e\.key==="Escape"/);
});
