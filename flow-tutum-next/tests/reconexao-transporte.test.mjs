import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { SessaoDeEntrada } from "../web/entrada.mjs";

test("falha no envio bloqueia comandos até a renovação explícita do transporte", () => {
  const enviados = [];
  let falhar = true;
  const sessao = new SessaoDeEntrada(comando => {
    enviados.push(comando);
    return !falhar;
  });
  assert.equal(sessao.conectar(), true);
  assert.equal(sessao.enviar({ phase: "key", keys: "enter" }), false);
  assert.equal(sessao.bloqueada, true);
  assert.equal(sessao.conectar(), false);
  assert.equal(sessao.enviar({ phase: "key", keys: "enter" }), false);
  assert.equal(enviados.length, 1, "não deve repetir comando após falha");
  falhar = false;
  assert.equal(sessao.restabelecerTransporte(), true);
  assert.equal(sessao.enviar({ phase: "key", keys: "enter" }), true);
  assert.equal(enviados.length, 2);
});

test("exceção do WebSocket bloqueia envio; desconexão normal não bloqueia", () => {
  const sessao = new SessaoDeEntrada(() => { throw new Error("socket encerrado"); });
  sessao.conectar();
  assert.equal(sessao.enviar({ phase: "wheel", wheel: 1 }), false);
  assert.equal(sessao.bloqueada, true);
  sessao.desconectar();
  assert.equal(sessao.conectar(), false);
  sessao.restabelecerTransporte();
  assert.equal(sessao.bloqueada, false);
  sessao.desconectar();
  assert.equal(sessao.conectar(), true);
});

test("painel recupera apenas após substituição do WebSocket autenticado", () => {
  const js = readFileSync(new URL("../web/painel.mjs", import.meta.url), "utf8");
  assert.match(js, /socketAtual !== socketAutorizado/);
  assert.match(js, /sessao\.restabelecerTransporte\(\)/);
  assert.match(js, /!sessao\.bloqueada/);
});
