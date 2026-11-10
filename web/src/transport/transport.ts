export class EndpointError extends Error {
  readonly code: "ENDPOINT_INVALID" | "ENDPOINT_INSECURE" | "ENDPOINT_CREDENTIALS";

  constructor(code: EndpointError["code"], message: string) {
    super(message);
    this.name = "EndpointError";
    this.code = code;
  }
}

export function assertEndpoint(input: string, allowInsecure: boolean): URL {
  let url: URL;
  try {
    url = new URL(input.trim());
  } catch {
    throw new EndpointError("ENDPOINT_INVALID", "Informe um endereço ws:// ou wss://.");
  }
  if (url.username || url.password) {
    throw new EndpointError("ENDPOINT_CREDENTIALS", "O endereço não pode carregar usuário ou senha.");
  }
  if (url.protocol === "ws:") {
    if (!allowInsecure) {
      throw new EndpointError(
        "ENDPOINT_INSECURE",
        "Esta página é HTTPS. O endereço do PC precisa ser wss://.",
      );
    }
  } else if (url.protocol !== "wss:") {
    throw new EndpointError("ENDPOINT_INVALID", "Informe um endereço ws:// ou wss://.");
  }
  return url;
}

export type TransportEvent =
  | { type: "open" }
  | { type: "close" }
  | { type: "error"; code: string; message: string }
  | { type: "frame"; raw: string };

export interface RemoteTransport {
  connect(): Promise<void>;
  close(): void;
  send(frame: string): void;
  isOpen(): boolean;
  subscribe(listener: (event: TransportEvent) => void): () => void;
}

export interface SocketLike {
  readyState: number;
  send(data: string): void;
  close(): void;
  onopen: (() => void) | null;
  onclose: (() => void) | null;
  onerror: (() => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
}

export interface SocketFactory {
  (url: string): SocketLike;
}

const socketOpen = 1;

/**
 * Cliente WebSocket isolado. O slice 1 liga o mesmo contrato no agente;
 * esta classe só abre o socket e entrega frames.
 */
export function createWebSocketTransport(url: string, factory?: SocketFactory): RemoteTransport {
  const listeners = new Set<(event: TransportEvent) => void>();
  let socket: SocketLike | null = null;
  let opened = false;
  let emitError = (code: string, message: string) => {
    listeners.forEach((listener) => listener({ type: "error", code, message }));
  };

  const transport: RemoteTransport = {
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    isOpen() {
      return opened && socket?.readyState === socketOpen;
    },
    async connect() {
      const makeSocket = factory ?? ((target: string) => new WebSocket(target) as unknown as SocketLike);
      socket = makeSocket(url);
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => {
          if (opened) return;
          socket?.close();
          emitError("PC_NOT_FOUND", "PC não encontrado na rede");
          reject(new Error("timeout"));
        }, 8000);
        socket!.onopen = () => {
          opened = true;
          clearTimeout(timer);
          listeners.forEach((listener) => listener({ type: "open" }));
          resolve();
        };
        socket!.onerror = () => {
          if (opened) return;
          clearTimeout(timer);
          emitError("PC_NOT_FOUND", "PC não encontrado na rede");
          reject(new Error("socket"));
        };
        socket!.onclose = () => {
          opened = false;
          listeners.forEach((listener) => listener({ type: "close" }));
        };
        socket!.onmessage = (event) => {
          if (typeof event.data !== "string") return;
          if (event.data.length > 65_536) return;
          listeners.forEach((listener) => listener({ type: "frame", raw: event.data as string }));
        };
      });
    },
    send(frame: string) {
      if (!transport.isOpen() || !socket) return;
      socket.send(frame);
    },
    close() {
      socket?.close();
      opened = false;
    },
  };

  return transport;
}
