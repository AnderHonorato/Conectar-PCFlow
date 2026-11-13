import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';
const fonte=readFileSync(new URL('../web/leitura-remota.js',import.meta.url),'utf8');
test('filtros são locais e não transmitem comandos',()=>{assert.match(fonte,/canvas\.style\.filter/);assert.doesNotMatch(fonte,/wsSend\(|fetch\(|WebSocket\(/)});
test('controles têm limites e persistência',()=>{assert.match(fonte,/Math\.min\(max,Math\.max\(min/);assert.match(fonte,/localStorage\.setItem/);assert.match(fonte,/aria-expanded/)});
test('perfis de leitura e restauração existem',()=>{for(const id of ['leitura-texto','leitura-noite','leitura-restaurar'])assert.ok(fonte.includes(id))});
