import {
  type ClientCommand,
  commandShapeError,
  decodeServerFrame,
  encodeCommand,
  type ServerEvent,
} from "../transport/protocolAdapter";

export type Phase =
  | "disconnected"
  | "connecting"
  | "authenticating"
  | "negotiating"
  | "ready"
  | "reconnecting"
  | "error";

export interface SessionState {
  phase: Phase;
  endpoint: string | null;
  deviceName: string | null;
  windowTitle: string | null;
  authenticated: boolean;
  capabilitiesKnown: boolean;
  sessionId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  serverNotice: string | null;
}

export type SessionEvent =
  | { type: "connect-requested"; endpoint: string }
  | { type: "socket-open" }
  | { type: "socket-closed" }
  | { type: "socket-error"; code: string; message: string }
  | { type: "auth-accepted"; deviceName?: string; sessionId: string | null }
  | { type: "auth-rejected" }
  | { type: "capabilities" }
  | { type: "window-title"; title: string }
  | { type: "server-notice"; message: string }
  | { type: "disconnect-requested" };

export interface SendResult {
  ok: boolean;
  reason: string | null;
}

export function initialSession(): SessionState {
  return {
    phase: "disconnected",
    endpoint: null,
    deviceName: null,
    windowTitle: null,
    authenticated: false,
    capabilitiesKnown: false,
    sessionId: null,
    errorCode: null,
    errorMessage: null,
    serverNotice: null,
  };
}

export function statusLabel(state: SessionState): string {
  switch (state.phase) {
    case "disconnected":
      return "Desconectado";
    case "connecting":
      return "Conectando";
    case "authenticating":
      return "Autenticando";
    case "negotiating":
      return "Negociando capacidades";
    case "ready":
      return "Conectado";
    case "reconnecting":
      return "Reconectando";
    case "error":
      return "Falha na conexão";
  }
}

export function controlBlockReason(state: SessionState): string | null {
  if (state.phase === "ready" && state.authenticated && state.capabilitiesKnown) return null;
  if (state.phase === "disconnected") {
    return "Desconectado. Conecte um PC para usar este controle.";
  }
  if (state.phase === "error") {
    const detail = state.errorMessage ?? "Falha na conexão.";
    return `${detail} Este controle espera uma sessão pronta.`;
  }
  return `${statusLabel(state)}. Este controle espera a sessão ficar pronta.`;
}

export function canSendInput(state: SessionState): boolean {
  return controlBlockReason(state) === null;
}

function promote(state: SessionState): SessionState {
  if (
    state.authenticated &&
    state.capabilitiesKnown &&
    (state.phase === "authenticating" || state.phase === "negotiating" || state.phase === "connecting")
  ) {
    return { ...state, phase: "ready", errorCode: null, errorMessage: null };
  }
  if (state.authenticated && !state.capabilitiesKnown && state.phase === "authenticating") {
    return { ...state, phase: "negotiating" };
  }
  return state;
}

export function reduceSession(state: SessionState, event: SessionEvent): SessionState {
  switch (event.type) {
    case "connect-requested":
      return { ...initialSession(), phase: "connecting", endpoint: event.endpoint };
    case "socket-open":
      if (state.phase !== "connecting" && state.phase !== "reconnecting") return state;
      return { ...state, phase: "authenticating", errorCode: null, errorMessage: null };
    case "auth-accepted":
      return promote({
        ...state,
        authenticated: true,
        deviceName: event.deviceName ?? state.deviceName,
        sessionId: event.sessionId ?? state.sessionId,
      });
    case "auth-rejected":
      return {
        ...state,
        phase: "error",
        authenticated: false,
        capabilitiesKnown: false,
        errorCode: "AUTH_REJECTED",
        errorMessage: "O PC recusou a autenticação.",
      };
    case "capabilities":
      return promote({ ...state, capabilitiesKnown: true });
    case "window-title":
      if (state.phase !== "ready") return state;
      return { ...state, windowTitle: event.title };
    case "server-notice":
      return { ...state, serverNotice: event.message };
    case "socket-error":
      return {
        ...initialSession(),
        phase: "error",
        endpoint: state.endpoint,
        errorCode: event.code,
        errorMessage: event.message,
      };
    case "socket-closed":
      if (state.phase === "disconnected" || state.phase === "error") return state;
      if (state.phase === "connecting" || state.phase === "reconnecting") {
        return {
          ...initialSession(),
          phase: "error",
          endpoint: state.endpoint,
          errorCode: "PC_NOT_FOUND",
          errorMessage: "PC não encontrado na rede",
        };
      }
      return {
        ...initialSession(),
        phase: "error",
        endpoint: state.endpoint,
        errorCode: "SOCKET_CLOSED",
        errorMessage: "A conexão com o PC caiu.",
      };
    case "disconnect-requested":
      return initialSession();
    default:
      return state;
  }
}

export type PlannedCommand = { ok: true; frame: string } | { ok: false; reason: string };

export function planCommand(state: SessionState, command: ClientCommand, messageId: string): PlannedCommand {
  const blocked = controlBlockReason(state);
  if (blocked) return { ok: false, reason: blocked };
  const shape = commandShapeError(command);
  if (shape) return { ok: false, reason: shape };
  return {
    ok: true,
    frame: encodeCommand(command, { messageId, sessionId: state.sessionId }),
  };
}

export function applyServerFrame(state: SessionState, raw: string): SessionState {
  const event = decodeServerFrame(raw);
  return applyServerEvent(state, event);
}

export function applyServerEvent(state: SessionState, event: ServerEvent): SessionState {
  switch (event.type) {
    case "auth-accepted":
      return reduceSession(state, {
        type: "auth-accepted",
        deviceName: event.deviceName,
        sessionId: event.sessionId,
      });
    case "auth-rejected":
      return reduceSession(state, { type: "auth-rejected" });
    case "capabilities":
      return reduceSession(state, { type: "capabilities" });
    case "window-title":
      return reduceSession(state, { type: "window-title", title: event.title });
    case "session-error":
      if (event.code === "PROTOCOL_VERSION_UNSUPPORTED") {
        return reduceSession(state, {
          type: "socket-error",
          code: event.code,
          message: event.message,
        });
      }
      return reduceSession(state, { type: "server-notice", message: event.message });
    case "invalid":
      return state;
  }
}

export type PowerKind = "shutdown" | "sleep" | "restart" | "logoff";

export function requestPower(
  state: SessionState,
  kind: PowerKind,
): { type: "confirm"; kind: PowerKind } | { type: "blocked"; reason: string } {
  const reason = controlBlockReason(state);
  if (reason) return { type: "blocked", reason };
  return { type: "confirm", kind };
}

export function clipboardReason(state: SessionState): string {
  const blocked = controlBlockReason(state);
  const limit = "A área de transferência não é lida nem escrita neste cliente.";
  if (blocked) return `${blocked} ${limit}`;
  return `${limit} A permissão do navegador ainda não é pedida.`;
}

export function gyroReason(state: SessionState): string {
  const sensor =
    "O giroscópio do navegador só pode ser lido depois de um gesto e de permissão, e este botão não lê o sensor.";
  const blocked = controlBlockReason(state);
  if (blocked) return `${blocked} ${sensor}`;
  return sensor;
}
