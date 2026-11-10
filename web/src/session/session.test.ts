import { describe, expect, it } from "vitest";
import { SessionController, type TransportFactory } from "./controller";
import {
  applyServerFrame,
  canSendInput,
  initialSession,
  planCommand,
  reduceSession,
  requestPower,
} from "./session";
import type { RemoteTransport, TransportEvent } from "../transport/transport";

class FakeTransport implements RemoteTransport {
  sent: string[] = [];
  open = false;
  private listeners = new Set<(event: TransportEvent) => void>();

  subscribe(listener: (event: TransportEvent) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async connect(): Promise<void> {
    this.open = true;
    this.emit({ type: "open" });
  }

  close(): void {
    this.open = false;
    this.emit({ type: "close" });
  }

  send(frame: string): void {
    this.sent.push(frame);
  }

  isOpen(): boolean {
    return this.open;
  }

  emit(event: TransportEvent): void {
    for (const listener of this.listeners) listener(event);
  }
}

function readyState() {
  let state = reduceSession(initialSession(), { type: "connect-requested", endpoint: "wss://pc.local/pcflow" });
  state = reduceSession(state, { type: "socket-open" });
  state = applyServerFrame(state, JSON.stringify({ name: "session.auth", payload: { ok: true, sessionId: "abc" } }));
  state = applyServerFrame(state, JSON.stringify({ name: "session.capabilities", payload: { names: ["pointer"] } }));
  return state;
}

describe("sessão web", () => {
  it("ReadyRequiresAuth: capacidades sem auth não ficam prontas", () => {
    let state = reduceSession(initialSession(), { type: "connect-requested", endpoint: "wss://pc.local/pcflow" });
    state = reduceSession(state, { type: "socket-open" });
    state = reduceSession(state, { type: "capabilities" });
    expect(state.phase).not.toBe("ready");
    expect(state.authenticated).toBe(false);
    expect(canSendInput(state)).toBe(false);
    const planned = planCommand(state, { name: "pointer.move", payload: { dx: 1, dy: 2 } }, "m1");
    expect(planned.ok).toBe(false);
  });

  it("fica pronta só depois de auth e capacidades, e então envia pointer.move", () => {
    const state = readyState();
    expect(state.phase).toBe("ready");
    expect(state.sessionId).toBe("abc");
    const planned = planCommand(state, { name: "pointer.move", payload: { dx: 4, dy: -2 } }, "m2");
    expect(planned.ok).toBe(true);
    if (planned.ok) {
      expect(planned.frame).toContain("pointer.move");
      expect(planned.frame).toContain('"provisional":true');
    }
  });

  it("recusa shell e energia sem confirmação", () => {
    const state = readyState();
    expect(planCommand(state, { name: "cmd.exe", payload: { line: "whoami" } }, "m3").ok).toBe(false);
    expect(planCommand(state, { name: "system.power", payload: { kind: "shutdown" } }, "m4").ok).toBe(false);
    const confirmed = planCommand(
      state,
      { name: "system.power", payload: { kind: "shutdown" }, confirmed: true },
      "m5",
    );
    expect(confirmed.ok).toBe(true);
    expect(requestPower(initialSession(), "shutdown").type).toBe("blocked");
    expect(requestPower(state, "shutdown").type).toBe("confirm");
  });

  it("não promove título de janela nem ecoa texto livre de erro", () => {
    const early = applyServerFrame(initialSession(), JSON.stringify({ name: "session.window", payload: { title: "Segredo" } }));
    expect(early.windowTitle).toBeNull();
    const ready = readyState();
    const titled = applyServerFrame(ready, JSON.stringify({ name: "session.window", payload: { title: "Bloco de notas" } }));
    expect(titled.windowTitle).toBe("Bloco de notas");
    const noticed = applyServerFrame(
      ready,
      JSON.stringify({ name: "session.error", payload: { code: "POWER_DISABLED_BY_POLICY", message: "texto copiado secreto" } }),
    );
    expect(noticed.phase).toBe("ready");
    expect(noticed.serverNotice).toBe("POWER_DISABLED_BY_POLICY");
    expect(noticed.serverNotice).not.toContain("secreto");
  });

  it("o controlador só solta pointer.move no transporte depois da auth", async () => {
    const fake = new FakeTransport();
    const factory: TransportFactory = () => fake;
    const controller = new SessionController(factory, true);
    controller.connect("ws://127.0.0.1:9/pcflow", "000000");
    await Promise.resolve();
    expect(controller.snapshot.phase).toBe("authenticating");
    expect(fake.sent.some((frame) => frame.includes("pointer.move"))).toBe(false);
    const blocked = controller.send({ name: "pointer.move", payload: { dx: 1, dy: 1 } });
    expect(blocked.ok).toBe(false);
    fake.emit({ type: "frame", raw: JSON.stringify({ name: "session.capabilities", payload: {} }) });
    expect(controller.snapshot.phase).not.toBe("ready");
    fake.emit({
      type: "frame",
      raw: JSON.stringify({ name: "session.auth", payload: { ok: true, deviceName: "PC da sala", sessionId: "s1" } }),
    });
    expect(controller.snapshot.phase).toBe("ready");
    expect(controller.snapshot.deviceName).toBe("PC da sala");
    const sent = controller.send({ name: "pointer.move", payload: { dx: 3, dy: -1 } });
    expect(sent.ok).toBe(true);
    expect(fake.sent.at(-1)).toContain("pointer.move");
    expect(JSON.stringify(controller.snapshot)).not.toContain("000000");
  });

  it("solta o botão preso quando a sessão cai e limpa o estado local", async () => {
    const fake = new FakeTransport();
    const controller = new SessionController(() => fake, true);
    controller.connect("ws://127.0.0.1:9/pcflow", "");
    await Promise.resolve();
    fake.emit({ type: "frame", raw: JSON.stringify({ name: "session.auth", payload: { ok: true, sessionId: "s1" } }) });
    fake.emit({ type: "frame", raw: JSON.stringify({ name: "session.capabilities", payload: {} }) });
    controller.hold({ kind: "button", code: "left" });
    controller.disconnect();
    expect(controller.heldInputs()).toEqual([]);
    expect(controller.snapshot.phase).toBe("disconnected");
    expect(fake.sent.some((frame) => frame.includes('"action":"up"'))).toBe(true);
    expect(fake.sent.some((frame) => frame.includes("pointer.cancel"))).toBe(true);
  });
});
