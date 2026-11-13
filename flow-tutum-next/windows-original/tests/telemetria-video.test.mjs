import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const raiz=new URL("../web/",import.meta.url);
const ler=n=>readFileSync(new URL(n,raiz),"utf8");
test("telemetria ligada ao painel e à apresentação real",()=>{
 const html=ler("index.html"),app=ler("app.js"),mod=ler("telemetria-video.js");
 assert.match(html,/id="video-telemetria"/);
 assert.match(html,/src="\/telemetria-video.js"/);
 assert.match(app,/flow-quadro-apresentado/);
 assert.match(mod,/flow-quadro-apresentado/);
 assert.match(mod,/document.hidden/);
 assert.match(mod,/FPS observados/);
});
