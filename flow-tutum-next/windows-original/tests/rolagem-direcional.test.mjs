import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const fonte=readFileSync(fileURLToPath(new URL('../web/enhancements.js',import.meta.url)),'utf8');
test('dois dedos usam limiar antes de decidir eixo',()=>{
 assert.match(fonte,/Math\.hypot\(padAcumulado\.x,padAcumulado\.y\)>=12/);
 assert.match(fonte,/padEixo!=="horizontal"&&padEixo&&Math\.abs\(dy\)>=scrollThreshold/);
 assert.match(fonte,/padEixo!=="vertical"&&padEixo&&Math\.abs\(dx\)>=scrollThreshold/);
});
test('eixo e acumuladores são reiniciados ao encerrar gesto',()=>{
 assert.match(fonte,/padEixo=null;padAcumulado=\{x:0,y:0\}/);
 assert.match(fonte,/if\(padGestureMulti\)/);
});
