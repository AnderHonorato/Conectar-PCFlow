import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
const fonte=readFileSync(fileURLToPath(new URL("../web/mapa-navegacao.js",import.meta.url)),"utf8");
test("toque inicial no minimapa não dispara navegação",()=>{
 assert.match(fonte,/if\(dedos\.size===1\)\{arrastando=true;/);
 assert.doesNotMatch(fonte,/arrastando=true;navegar\(e\)/);
});
test("arraste exige movimento mínimo e o modo Explorar",()=>{
 assert.match(fonte,/Math\.hypot\(e\.clientX-/);
 assert.match(fonte,/function explorar\(\)/);
 assert.match(fonte,/!geometria\|\|!explorar\(\)/);
});
test("pinça só altera visualização e mostra zoom real",()=>{
 assert.match(fonte,/zoomNoPonto\(zoom,/);
 assert.match(fonte,/Zoom \$\{Math\.round\(state\.zoom\*100\)\}%/);
 assert.doesNotMatch(fonte,/\\nwindow\.addEventListener/);
});
