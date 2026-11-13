import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {limitarPan,posicaoPan} from '../web/mapa-navegacao.mjs';
const raiz=resolve(dirname(fileURLToPath(import.meta.url)),'../web');
test('minimapa oferece setas, centralização e teclado sem comando remoto',()=>{
 const js=readFileSync(resolve(raiz,'mapa-navegacao.js'),'utf8');
 for(const nome of ['esquerda','direita','cima','baixo','centro'])assert.match(js,new RegExp('data-direcao="'+nome+'"'));
 assert.match(js,/state\.exploring/);
 assert.match(js,/ArrowLeft/);
 assert.match(js,/Home:"centro"/);
 assert.doesNotMatch(js,/\\nwindow\.addEventListener/);
});
test('navegação ultrawide limita pan e permite centralização',()=>{
 assert.deepEqual(limitarPan(2000,-2000,3440,1440,800,600),{x:1320,y:-420});
 assert.deepEqual(limitarPan(0,0,3440,1440,800,600),{x:0,y:0});
 assert.deepEqual(posicaoPan(0,1,3440,1440,800,600),{x:1320,y:-420});
});
