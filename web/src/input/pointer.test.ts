import { describe, expect, it } from "vitest";
import { applyModifier, initialModifiers, releaseModifiers } from "./modifiers";
import { isTap, pointerDelta, railScroll } from "./pointer";
import { textAction } from "./textAction";

describe("ponteiro e teclado", () => {
  it("escala o delta e inverte o trilho quando o scroll é natural", () => {
    expect(pointerDelta({ x: 0, y: 0 }, { x: 10, y: -4 }, 1.5)).toEqual({ dx: 15, dy: -6 });
    expect(pointerDelta({ x: 0, y: 0 }, { x: 1, y: 1 }, 99).dx).toBe(2);
    expect(railScroll(10, 1, false)).toBe(10);
    expect(railScroll(10, 1, true)).toBe(-10);
    expect(isTap(3)).toBe(true);
    expect(isTap(12)).toBe(false);
  });

  it("toque envia o modificador, segurar ativa e o segundo toque trava", () => {
    const tap = applyModifier(initialModifiers(), { type: "tap", id: "Control" }, true);
    expect(tap.state.Control).toBe("inactive");
    expect(tap.send.map((item) => item.action)).toEqual(["down", "up"]);

    const off = applyModifier(initialModifiers(), { type: "tap", id: "Control" }, false);
    expect(off.send).toEqual([]);

    const held = applyModifier(initialModifiers(), { type: "hold-start", id: "Shift" }, true);
    expect(held.state.Shift).toBe("held");
    const locked = applyModifier(held.state, { type: "lock", id: "Shift" }, true);
    expect(locked.state.Shift).toBe("locked");
    expect(locked.send).toEqual([]);
    const released = applyModifier(locked.state, { type: "tap", id: "Shift" }, false);
    expect(released.state.Shift).toBe("inactive");
    expect(released.send).toEqual([{ action: "up", code: "Shift" }]);

    const dropped = releaseModifiers(locked.state);
    expect(dropped.state.Shift).toBe("inactive");
    expect(dropped.send).toEqual([{ action: "up", code: "Shift" }]);
  });

  it("separa buffer e Enter", () => {
    expect(textAction(true, "enter")).toBe("send-text");
    expect(textAction(false, "enter")).toBe("send-enter");
    expect(textAction(false, "button")).toBe("send-text");
  });
});
