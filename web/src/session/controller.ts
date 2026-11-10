import { assertEndpoint, EndpointError, type RemoteTransport, type TransportEvent } from "../transport/transport";
import { encodeCommand, type ClientCommand } from "../transport/protocolAdapter";
import {
  applyServerFrame,
  initialSession,
  planCommand,
  reduceSession,
  type SendResult,
  type SessionState,
} from "./session";

export interface HeldInput {
  kind: "button" | "key";
  code: string;
}

export type TransportFactory = (url: string) => RemoteTransport;

function sameHold(left: HeldInput, right: HeldInput): boolean {
  return left.kind === right.kind && left.code === right.code;
}

export class SessionController {
  private state: SessionState = initialSession();
  private transport: RemoteTransport | null = null;
  private unsubscribe: (() => void) | null = null;
  private generation = 0;
  private sequence = 0;
  private held: HeldInput[] = [];
  private readonly listeners = new Set<() => void>();

  constructor(
    private readonly factory: TransportFactory,
    private readonly allowInsecure: boolean,
  ) {}

  get snapshot(): SessionState {
    return this.state;
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  connect(url: string, pin: string): void {
    let endpoint: URL;
    try {
      endpoint = assertEndpoint(url, this.allowInsecure);
    } catch (error) {
      const endpointError = error as EndpointError;
      this.replace({
        ...initialSession(),
        phase: "error",
        errorCode: endpointError.code,
        errorMessage: endpointError.message,
      });
      return;
    }
    this.stopTransport(false);
    const generation = ++this.generation;
    this.replace(reduceSession(initialSession(), { type: "connect-requested", endpoint: endpoint.toString() }));
    const transport = this.factory(endpoint.toString());
    this.transport = transport;
    this.unsubscribe = transport.subscribe((event) => {
      if (generation !== this.generation) return;
      this.onTransport(event, pin);
    });
    void transport.connect().catch(() => {
      if (generation !== this.generation) return;
      if (this.state.phase === "connecting" || this.state.phase === "reconnecting") {
        this.fail("PC_NOT_FOUND", "PC não encontrado na rede");
      }
    });
  }

  disconnect(): void {
    this.releaseHeld();
    this.stopTransport(true);
    this.generation += 1;
    this.replace(reduceSession(this.state, { type: "disconnect-requested" }));
  }

  send(command: ClientCommand): SendResult {
    const planned = planCommand(this.state, command, this.nextId());
    if (!planned.ok) return { ok: false, reason: planned.reason };
    if (!this.transport?.isOpen()) {
      return { ok: false, reason: "Desconectado. Conecte um PC para usar este controle." };
    }
    this.transport.send(planned.frame);
    return { ok: true, reason: null };
  }

  hold(input: HeldInput): void {
    if (this.held.some((item) => sameHold(item, input))) return;
    this.held.push(input);
  }

  releaseLocal(input: HeldInput): void {
    this.held = this.held.filter((item) => !sameHold(item, input));
  }

  heldInputs(): readonly HeldInput[] {
    return this.held;
  }

  private onTransport(event: TransportEvent, pin: string): void {
    if (event.type === "open") {
      this.replace(reduceSession(this.state, { type: "socket-open" }));
      this.transport?.send(
        encodeCommand({ name: "session.hello", payload: { client: "web" } }, { messageId: this.nextId(), sessionId: null }),
      );
      this.transport?.send(
        encodeCommand(
          { name: "session.auth", payload: { code: pin } },
          { messageId: this.nextId(), sessionId: null },
        ),
      );
      return;
    }
    if (event.type === "frame") {
      this.replace(applyServerFrame(this.state, event.raw));
      return;
    }
    if (event.type === "error") {
      this.fail(event.code, event.message);
      return;
    }
    if (event.type === "close") {
      this.releaseHeld();
      this.replace(reduceSession(this.state, { type: "socket-closed" }));
    }
  }

  private fail(code: string, message: string): void {
    this.releaseHeld();
    this.replace(reduceSession(this.state, { type: "socket-error", code, message }));
  }

  private releaseHeld(): void {
    if (!this.transport?.isOpen()) {
      this.held = [];
      return;
    }
    for (const item of this.held) {
      const name = item.kind === "button" ? "pointer.button" : "keyboard.key";
      const payload: Record<string, string | number | boolean | null> =
        item.kind === "button" ? { action: "up", button: item.code } : { action: "up", code: item.code };
      this.transport.send(
        encodeCommand(
          { name, payload },
          { messageId: this.nextId(), sessionId: this.state.sessionId },
        ),
      );
    }
    this.transport.send(
      encodeCommand(
        { name: "pointer.cancel", payload: {} },
        { messageId: this.nextId(), sessionId: this.state.sessionId },
      ),
    );
    this.held = [];
  }

  private stopTransport(close: boolean): void {
    this.unsubscribe?.();
    this.unsubscribe = null;
    if (close) this.transport?.close();
    this.transport = null;
  }

  private replace(next: SessionState): void {
    this.state = next;
    for (const listener of this.listeners) listener();
  }

  private nextId(): string {
    this.sequence += 1;
    return `web-${this.sequence}`;
  }
}
