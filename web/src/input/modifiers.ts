export const modifierIds = ["Shift", "Control", "Meta", "Alt"] as const;
export type ModifierId = (typeof modifierIds)[number];
export type ModifierMode = "inactive" | "held" | "locked";
export type ModifierState = Record<ModifierId, ModifierMode>;

export interface KeyPulse {
  action: "down" | "up";
  code: ModifierId;
}

export type ModifierGesture = { type: "tap" | "lock" | "hold-start" | "hold-end"; id: ModifierId };

export function initialModifiers(): ModifierState {
  return { Shift: "inactive", Control: "inactive", Meta: "inactive", Alt: "inactive" };
}

export function applyModifier(
  state: ModifierState,
  gesture: ModifierGesture,
  tapSends: boolean,
): { state: ModifierState; send: KeyPulse[] } {
  const mode = state[gesture.id];
  const next: ModifierState = { ...state };

  if (mode === "locked" && (gesture.type === "tap" || gesture.type === "lock")) {
    next[gesture.id] = "inactive";
    return { state: next, send: [{ action: "up", code: gesture.id }] };
  }

  if (!tapSends && (gesture.type === "tap" || gesture.type === "lock") && mode === "inactive") {
    return { state, send: [] };
  }

  if (gesture.type === "tap") {
    return {
      state,
      send: [
        { action: "down", code: gesture.id },
        { action: "up", code: gesture.id },
      ],
    };
  }

  if (gesture.type === "lock") {
    if (mode === "held") {
      next[gesture.id] = "locked";
      return { state: next, send: [] };
    }
    next[gesture.id] = "locked";
    return { state: next, send: [{ action: "down", code: gesture.id }] };
  }

  if (gesture.type === "hold-start") {
    if (mode === "locked" || mode === "held") return { state, send: [] };
    next[gesture.id] = "held";
    return { state: next, send: [{ action: "down", code: gesture.id }] };
  }

  if (mode === "held") {
    next[gesture.id] = "inactive";
    return { state: next, send: [{ action: "up", code: gesture.id }] };
  }
  return { state, send: [] };
}

export function releaseModifiers(state: ModifierState): { state: ModifierState; send: KeyPulse[] } {
  const send: KeyPulse[] = [];
  for (const id of modifierIds) {
    if (state[id] === "held" || state[id] === "locked") send.push({ action: "up", code: id });
  }
  return { state: initialModifiers(), send };
}
