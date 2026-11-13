import assert from "node:assert/strict";

// Integração HTTP real com o servidor original Flow Tutum Next da conta AS.
// Não executa no CI sem servidor isolado: exige URL loopback e porta explícita.
// FLOW_TUTUM_TEST_URL=http://127.0.0.1:54939 node flow-tutum-next/tests/pareamento-http.mjs
const base = process.env.FLOW_TUTUM_TEST_URL;
if (!base || !/^http:\/\/(?:127\.0\.0\.1|localhost):\d{2,5}$/.test(base)) {
  throw new Error("Defina FLOW_TUTUM_TEST_URL com URL loopback de um servidor de teste isolado.");
}
const cenarios = [
  { nome: "tipo de conteudo nao suportado", tipo: "text/plain", corpo: '{"pin":"0"}', status: 415 },
  { nome: "JSON malformado", tipo: "application/json", corpo: '{"pin":', status: 400 },
  { nome: "PIN incorreto", tipo: "application/json", corpo: '{"pin":"000000"}', status: 401 },
  { nome: "corpo acima de 4 KiB", tipo: "application/json", corpo: JSON.stringify({ pin: "000000", name: "x".repeat(5000) }), status: 413 }
];
for (const c of cenarios) {
  const resposta = await fetch(base + "/api/pair", {
    method: "POST",
    headers: { "content-type": c.tipo },
    body: c.corpo,
    signal: AbortSignal.timeout(4000)
  });
  assert.equal(resposta.status, c.status, c.nome + ": HTTP inesperado");
  console.log("PASSOU:", c.nome, "HTTP", resposta.status);
}
console.log("PAREAMENTO_HTTP: 4/4 PASSARAM");
