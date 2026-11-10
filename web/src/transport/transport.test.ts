import { describe, expect, it } from "vitest";
import { decodeServerFrame } from "./protocolAdapter";
import { assertEndpoint, createWebSocketTransport, EndpointError, type SocketLike } from "./transport";

class FakeSocket implements SocketLike {
  static last: FakeSocket | null = null;
  readyState = 0;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;

  constructor(readonly url: string) {
    FakeSocket.last = this;
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.readyState = 3;
    this.onclose?.();
  }
}

describe("transporte web", () => {
  it("rejeita endereço que não é websocket e ws em página segura", () => {
    expect(() => assertEndpoint("http://pc.local/pcflow", true)).toThrow(EndpointError);
    expect(() => assertEndpoint("wss://user:segredo@pc.local/pcflow", true)).toThrow(EndpointError);
    expect(() => assertEndpoint("ws://pc.local/pcflow", false).toString()).toThrow(EndpointError);
    try {
      assertEndpoint("ws://pc.local/pcflow", false);
    } catch (error) {
      expect((error as EndpointError).code).toBe("ENDPOINT_INSECURE");
    }
    expect(assertEndpoint("wss://pc.local/pcflow", false).protocol).toBe("wss:");
    expect(assertEndpoint("ws://127.0.0.1:9/pcflow", true).protocol).toBe("ws:");
  });

  it("entrega frames e ignora binário ou payload enorme", async () => {
    const seen: string[] = [];
    const transport = createWebSocketTransport("wss://pc.local/pcflow", (url) => new FakeSocket(url));
    transport.subscribe((event) => {
      if (event.type === "frame") seen.push(event.raw);
    });
    const pending = transport.connect();
    FakeSocket.last!.readyState = 1;
    FakeSocket.last!.onopen?.();
    await pending;
    transport.send("{\"name\":\"pointer.move\"}");
    expect(FakeSocket.last!.sent).toEqual(["{\"name\":\"pointer.move\"}"]);
    FakeSocket.last!.onmessage?.({ data: "{\"name\":\"session.capabilities\"}" });
    FakeSocket.last!.onmessage?.({ data: new Uint8Array([1]) });
    FakeSocket.last!.onmessage?.({ data: "x".repeat(70_000) });
    expect(seen).toEqual(["{\"name\":\"session.capabilities\"}"]);
  });

  it("descarta json inválido sem lançar", () => {
    expect(decodeServerFrame("não-json").type).toBe("invalid");
    expect(decodeServerFrame("{\"name\":\"session.auth\",\"payload\":{\"ok\":false,\"message\":\"pin 123456\"}}").type).toBe(
      "auth-rejected",
    );
  });
});
