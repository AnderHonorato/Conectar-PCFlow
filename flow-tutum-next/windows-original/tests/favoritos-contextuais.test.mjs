import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const arquivo=fileURLToPath(new URL('../web/favoritos.js',import.meta.url));
const fonte=readFileSync(arquivo,'utf8');
test('favoritos contextuais respeitam autorização',()=>{
 assert.match(fonte,/app\.state\.approved&&app\.state\.authenticated&&!app\.state\.somenteVisualizacao/);
 assert.match(fonte,/acao\.disabled=!disponivel\(item\)/);
 assert.match(fonte,/if\(!editando&&!mostrarTodos&&candidato&&!disponivel\(candidato\)\)continue/);
});
test('alternância do contexto e persistência continuam disponíveis',()=>{
 assert.match(fonte,/favoritos-contexto/);
 assert.match(fonte,/salvar\(\);renderizar\(\)/);
 assert.match(fonte,/ultimoEstado/);
});
