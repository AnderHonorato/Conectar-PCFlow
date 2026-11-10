export type TextVia = "button" | "enter";
export type TextAction = "send-text" | "send-enter";

/** Buffer ligado: Enter envia o texto acumulado. Desligado: Enter envia a tecla Enter. */
export function textAction(buffer: boolean, via: TextVia): TextAction {
  if (via === "button" || buffer) return "send-text";
  return "send-enter";
}
