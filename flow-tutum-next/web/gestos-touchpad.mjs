// Estado independente do DOM: gestos cancelados ou de rolagem nunca geram clique.
export class GestosTouchpad {
  #dedos = new Map();
  #inicio = 0;
  #movimento = 0;
  #multiplos = false;
  #cancelado = false;
  #rolou = false;

  get quantidade() { return this.#dedos.size; }

  iniciar(id, x, y, instante) {
    if (!Number.isFinite(x) || !Number.isFinite(y)) return false;
    if (this.#dedos.has(id)) return false;
    if (!this.#dedos.size) {
      this.#inicio = instante;
      this.#movimento = 0;
      this.#multiplos = false;
      this.#cancelado = false;
      this.#rolou = false;
    }
    this.#dedos.set(id, { x, y });
    if (this.#dedos.size > 1) this.#multiplos = true;
    return true;
  }

  mover(id, x, y) {
    const anterior = this.#dedos.get(id);
    if (!anterior || !Number.isFinite(x) || !Number.isFinite(y)) return null;
    const dx = x - anterior.x, dy = y - anterior.y;
    anterior.x = x; anterior.y = y;
    this.#movimento += Math.hypot(dx, dy);
    const rolagem = this.#dedos.size > 1;
    if (rolagem && (Math.abs(dx) > 1 || Math.abs(dy) > 1)) this.#rolou = true;
    return { dx, dy, rolagem, moverCursor: !this.#multiplos && !rolagem };
  }

  finalizar(id, instante, { cancelado = false, tolerancia = 9 } = {}) {
    if (!this.#dedos.has(id)) return null;
    if (cancelado) this.#cancelado = true;
    this.#dedos.delete(id);
    if (this.#dedos.size) return null;
    const clicar = !this.#cancelado && !this.#rolou && this.#movimento < tolerancia;
    const botao = this.#multiplos || instante - this.#inicio > 650 ? "right" : "left";
    return clicar ? botao : null;
  }

  cancelarTudo() {
    this.#dedos.clear();
    this.#cancelado = true;
  }
}
