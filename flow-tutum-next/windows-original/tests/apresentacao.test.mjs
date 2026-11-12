import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const base=new URL('../web/',import.meta.url);
test('painel possui comandos de apresentacao reais e nao envia sem autorizacao',()=>{
 const codigo=readFileSync(new URL('apresentacao.js',base),'utf8');
 for(const comando of ['f5','shift+f5','escape','right','left','home','end','b','w'])assert.ok(codigo.includes('"'+comando+'"'),comando);
 assert.match(codigo,/if\(!valido\(\)\)return/);
 assert.match(codigo,/FlowTutumNext\.wsSend/);
});
test('painel acessivel em retrato e paisagem',()=>{
 const css=readFileSync(new URL('apresentacao.css',base),'utf8');
 assert.match(css,/max-width:380px/);assert.match(css,/orientation:landscape/);
 assert.match(css,/prefers-reduced-motion/);assert.match(css,/min-height:44px/);
});
