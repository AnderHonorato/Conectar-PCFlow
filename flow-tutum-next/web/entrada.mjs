// Adaptação segura para o WebSocket do Flow Tutum Next.
// O módulo não cria sockets nem concede acesso. Só usa a sessão já autenticada.
const botoes = new Set(["left", "middle", "right"]);
const teclas = new Set([
  "enter", "tab", "escape", "backspace", "delete", "home", "end",
  "arrowleft", "arrowright", "arrowup", "arrowdown", "pageup", "pagedown"
]);
const coordenada = n => {
  if (!Number.isFinite(n)) throw new RangeError("Coordenada inválida");
  return Math.min(1, Math.max(0, n));
};
const delta = n => {
  if (!Number.isFinite(n)) throw new RangeError("Rolagem inválida");
  return Math.trunc(Math.max(-1200, Math.min(1200, n)));
};

export function comandoMouse(fase, x, y, botao = "left") {
  if (!["move", "tap", "down", "up"].includes(fase)) throw new RangeError("Fase inválida");
  if (!botoes.has(botao)) throw new RangeError("Botão inválido");
  return { phase: fase, x: coordenada(x), y: coordenada(y), button: botao };
}

export function comandoRolagem(horizontal, quantidade) {
  return { phase: horizontal ? "hwheel" : "wheel", wheel: delta(quantidade) };
}

export function comandoTecla(tecla) {
  const t = String(tecla).toLowerCase();
  if (!teclas.has(t)) throw new RangeError("Tecla não suportada");
  return { phase: "key", keys: t };
}

// Segmentação por grafemas para não dividir emoji, acentos combinantes e modificadores.
export function separarGrafemas(texto) {
  const valor = String(texto);
  if (typeof Intl.Segmenter === "function") {
    return [...new Intl.Segmenter("pt-BR", { granularity: "grapheme" }).segment(valor)]
      .map(segmento => segmento.segment);
  }
  return Array.from(valor);
}

// Envia apenas alterações confirmadas pelo IME, nunca pré-edição de acentos.
export function diferencaDeDigitacao(anterior, atual, { maximo = 2000 } = {}) {
  if (typeof anterior !== "string" || typeof atual !== "string") {
    throw new TypeError("O teclado requer texto");
  }
  if (atual.length > maximo || anterior.length > maximo) {
    throw new RangeError("Limite de texto excedido");
  }
  const a = separarGrafemas(anterior);
  const b = separarGrafemas(atual);
  let prefixo = 0;
  while (prefixo < Math.min(a.length, b.length) && a[prefixo] === b[prefixo]) prefixo++;
  let sufixo = 0;
  while (sufixo < Math.min(a.length - prefixo, b.length - prefixo)
    && a[a.length - 1 - sufixo] === b[b.length - 1 - sufixo]) sufixo++;

  const comandos = [];
  for (let i = 0; i < sufixo; i++) comandos.push(comandoTecla("arrowleft"));
  for (let i = 0; i < a.length - prefixo - sufixo; i++) {
    comandos.push(comandoTecla("backspace"));
  }
  const inserido = b.slice(prefixo, b.length - sufixo).join("");
  if (inserido) {
    const linhas = inserido.split("\n");
    for (let i = 0; i < linhas.length; i++) {
      if (linhas[i]) comandos.push({ phase: "text", text: linhas[i] });
      if (i < linhas.length - 1) comandos.push(comandoTecla("enter"));
    }
  }
  for (let i = 0; i < sufixo; i++) comandos.push(comandoTecla("arrowright"));
  return comandos;
}

// Bloqueia comandos de outra sessão e impede um botão pressionado ficar "preso".
export class SessaoDeEntrada {
  #enviar;
  #ativa = false;
  #pressionados = new Set();
  constructor(enviar) {
    if (typeof enviar !== "function") throw new TypeError("Função de envio obrigatória");
    this.#enviar = enviar;
  }
  get ativa() { return this.#ativa; }
  get arrastando() { return this.#pressionados.size > 0; }

  conectar() { this.#ativa = true; }
  enviar(comando) {
    if (!this.#ativa) return false;
    try { return this.#enviar(comando) !== false; }
    catch { this.desconectar(); return false; }
  }
  pressionar(botao, x, y) {
    if (!this.#ativa || this.#pressionados.has(botao)) return false;
    if (!this.enviar(comandoMouse("down", x, y, botao))) return false;
    this.#pressionados.add(botao);
    return true;
  }
  soltar(botao, x, y) {
    if (!this.#pressionados.has(botao)) return false;
    this.#pressionados.delete(botao);
    return this.enviar(comandoMouse("up", x, y, botao));
  }
  desconectar(x = 0.5, y = 0.5) {
    if (this.#ativa) {
      for (const botao of this.#pressionados) {
        try { this.#enviar(comandoMouse("up", x, y, botao)); } catch {}
      }
    }
    this.#pressionados.clear();
    this.#ativa = false;
  }
}
