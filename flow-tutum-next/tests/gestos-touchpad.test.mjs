import test from "node:test";
import assert from "node:assert/strict";
import { GestosTouchpad } from "../web/gestos-touchpad.mjs";

test("toque simples emite clique esquerdo e toque longo emite direito", () => {
  const g = new GestosTouchpad();
  g.iniciar(1, 10, 10, 0);
  assert.equal(g.finalizar(1, 100), "left");
  g.iniciar(2, 10, 10, 200);
  assert.equal(g.finalizar(2, 900), "right");
});

test("rolagem com dois dedos não emite clique fantasma ao levantar o último dedo", () => {
  const g = new GestosTouchpad();
  g.iniciar(1, 10, 10, 0);
  g.iniciar(2, 20, 10, 1);
  assert.equal(g.mover(1, 10, 12).rolagem, true);
  assert.equal(g.finalizar(1, 30), null);
  assert.equal(g.finalizar(2, 35), null);
});

test("cancelamento do Android não gera clique nem após levantar o outro dedo", () => {
  const g = new GestosTouchpad();
  g.iniciar(1, 10, 10, 0);
  g.iniciar(2, 20, 10, 1);
  assert.equal(g.finalizar(1, 20, { cancelado: true }), null);
  assert.equal(g.finalizar(2, 25), null);
  g.iniciar(3, 10, 10, 40);
  assert.equal(g.finalizar(3, 60), "left");
});

test("arrastar dedo não clica; eventos órfãos não alteram estado", () => {
  const g = new GestosTouchpad();
  assert.equal(g.mover(999, 1, 1), null);
  g.iniciar(1, 0, 0, 0);
  assert.equal(g.iniciar(1, 0, 0, 1), false);
  assert.equal(g.mover(1, 20, 0).moverCursor, true);
  assert.equal(g.finalizar(1, 100), null);
  assert.equal(g.finalizar(1, 101), null);
});

test("dois dedos parados ainda permitem clique direito deliberado", () => {
  const g = new GestosTouchpad();
  g.iniciar(1, 10, 10, 0);
  g.iniciar(2, 20, 10, 1);
  assert.equal(g.finalizar(1, 50), null);
  assert.equal(g.finalizar(2, 55), "right");
});
