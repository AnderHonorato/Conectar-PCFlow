import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const codigo=readFileSync(new URL('../web/navegacao-rapida.js',import.meta.url),'utf8');

test('favoritos podem ser reordenados pelos controles visíveis',()=>{
 assert.match(codigo,/button\[data-mover\]\[data-chave\]/);
 assert.match(codigo,/moverFavorito\(mover\.dataset\.chave,Number\(mover\.dataset\.mover\)\)/);
 assert.match(codigo,/salvarFavoritos\(\);atualizarLista\(\)/);
});
test('busca não percorre a área dos favoritos clonados',()=>{
 assert.match(codigo,/querySelectorAll\("\.navegacao-grupos \[data-comando\]"\)/);
});
test('favoritos armazenados são filtrados, únicos e limitados',()=>{
 assert.match(codigo,/new Set\(salvos\.filter/);
 assert.match(codigo,/\.slice\(0,autorizados\.size\)/);
});
test('atalhos exigem sessão e têm intervalo mínimo contra toque duplicado',()=>{
 assert.match(codigo,/state\.approved&&state\.authenticated&&state\.sessionReady/);
 assert.match(codigo,/agora-ultimoEnvio<220/);
 assert.match(codigo,/if\(enviado\)ultimoEnvio=agora/);
});
