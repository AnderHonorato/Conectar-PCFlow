import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {permitir}=require("../web/politica-visualizacao.js");
test("controle normal mantém comandos disponíveis",()=>{
 for(const phase of ["move","tap","down","up","wheel","key"])assert.equal(permitir(false,{phase}),true);
});
test("somente visualização bloqueia toda entrada remota",()=>{
 for(const phase of ["move","tap","down","up","wheel","hwheel","key"])assert.equal(permitir(true,{phase}),false);
});
test("cancelamento permanece disponível para liberar arrasto",()=>{
 assert.equal(permitir(true,{phase:"cancel"}),true);
 assert.equal(permitir(true,{}),false);
});
