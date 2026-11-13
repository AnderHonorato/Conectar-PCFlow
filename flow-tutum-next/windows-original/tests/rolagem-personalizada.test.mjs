import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const fonte=readFileSync(fileURLToPath(new URL('../web/enhancements.js',import.meta.url)),'utf8');
test('configuração oferece quatro modos de rolagem com persistência',()=>{
 for(const modo of ['automatica','livre','vertical','horizontal'])assert.match(fonte,new RegExp('value="'+modo+'"'));
 assert.match(fonte,/localStorage\.setItem\('flow-direcao-rolagem',direcaoRolagem\)/);
 assert.match(fonte,/localStorage\.getItem\('flow-direcao-rolagem'\)/);
});
test('dois dedos movidos não geram clique direito acidental',()=>{
 assert.match(fonte,/distanciaDoisDedos\+=Math\.hypot\(dx,dy\)/);
 assert.match(fonte,/!padMultiScrolled&&distanciaDoisDedos<8&&duracao<550&&state\.approved/);
 assert.match(fonte,/distanciaDoisDedos=0/);
});
test('a seleção manual respeita o eixo e o modo automático preserva a detecção',()=>{
 assert.match(fonte,/padEixo=direcaoRolagem==="automatica"/);
 assert.match(fonte,/padEixo!=="horizontal"&&padEixo/);
 assert.match(fonte,/padEixo!=="vertical"&&padEixo/);
});
