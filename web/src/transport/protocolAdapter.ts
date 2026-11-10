/**
 * Adaptador provisório do cliente web.
 * O schema compartilhado (protocol/schema) pertence ao slice 1 e ainda não está neste repositório.
 * Quando ele existir, a troca fica neste arquivo: a UI continua falando ClientCommand.
 * Não registrar payload. Texto, tecla e área de transferência não entram em log.
 */

export interface ClientCommand {
  name: string;
  payload: Record<string, string | number | boolean | null>;
  confirmed?: boolean;
}

export interface EncodeContext {
  messageId: string;
  sessionId: string | null;
}

const allowedCommands = new Set([
  "session.hello",
  "session.auth",
  "pointer.move",
  "pointer.button",
  "pointer.scroll",
  "pointer.cancel",
  "keyboard.text",
  "keyboard.key",
  "media.transport",
  "media.volume",
  "browser.command",
  "system.power",
]);

const powerKinds = new Set(["sleep", "shutdown", "restart", "logoff"]);
const mediaTransport = new Set(["playpause", "next", "previous", "stop"]);
const mediaVolume = new Set(["up", "down", "mute"]);
const browserCommands = new Set(["back", "forward", "new", "close", "zoomIn", "zoomOut", "reload", "home"]);
const buttons = new Set(["left", "right", "middle"]);
const actions = new Set(["down", "up"]);

export function encodeCommand(command: ClientCommand, context: EncodeContext): string {
  return JSON.stringify({
    protocolVersion: 0,
    provisional: true,
    name: command.name,
    messageId: context.messageId,
    sessionId: context.sessionId,
    payload: command.payload,
  });
}

export function commandShapeError(command: ClientCommand): string | null {
  if (!allowedCommands.has(command.name)) {
    return "Comando fora do catálogo.";
  }
  if (command.name === "system.power") {
    if (command.confirmed !== true) return "Confirme a ação de energia antes de enviar.";
    if (!powerKinds.has(String(command.payload.kind))) return "Ação de energia desconhecida.";
  }
  if (command.name === "media.transport" && !mediaTransport.has(String(command.payload.action))) {
    return "Comando de mídia desconhecido.";
  }
  if (command.name === "media.volume" && !mediaVolume.has(String(command.payload.action))) {
    return "Volume desconhecido.";
  }
  if (command.name === "browser.command" && !browserCommands.has(String(command.payload.action))) {
    return "Comando de navegador desconhecido.";
  }
  if (command.name === "pointer.button") {
    if (!buttons.has(String(command.payload.button)) || !actions.has(String(command.payload.action))) {
      return "Botão do mouse desconhecido.";
    }
  }
  if (command.name === "keyboard.text" && typeof command.payload.text !== "string") {
    return "Texto ausente.";
  }
  if (command.name === "keyboard.key" && (typeof command.payload.code !== "string" || !actions.has(String(command.payload.action)))) {
    return "Tecla desconhecida.";
  }
  return null;
}

export type ServerEvent =
  | { type: "auth-accepted"; deviceName?: string; sessionId: string | null }
  | { type: "auth-rejected" }
  | { type: "capabilities" }
  | { type: "window-title"; title: string }
  | { type: "session-error"; code: string; message: string }
  | { type: "invalid" };

const knownErrorMessages: Record<string, string> = {
  PROTOCOL_VERSION_UNSUPPORTED: "PROTOCOL_VERSION_UNSUPPORTED",
  POWER_DISABLED_BY_POLICY: "POWER_DISABLED_BY_POLICY",
  INPUT_BLOCKED_UIPI: "INPUT_BLOCKED_UIPI",
  BLUETOOTH_OFF: "BLUETOOTH_OFF",
  CLIPBOARD_TOO_LARGE: "CLIPBOARD_TOO_LARGE",
  LOCAL_NETWORK_PERMISSION_DENIED: "LOCAL_NETWORK_PERMISSION_DENIED",
};

export function cleanId(value: unknown): string | null {
  if (typeof value !== "string") return null;
  if (!/^[A-Za-z0-9_-]{1,80}$/.test(value)) return null;
  return value;
}

export function cleanTitle(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.replace(/[\u0000-\u001F]/g, "").trim();
  if (!trimmed || trimmed.length > 120) return null;
  return trimmed;
}

export function decodeServerFrame(raw: string): ServerEvent {
  if (raw.length > 65_536) return { type: "invalid" };
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return { type: "invalid" };
  }
  if (!data || typeof data !== "object") return { type: "invalid" };
  const record = data as { name?: unknown; payload?: unknown };
  const payload = record.payload && typeof record.payload === "object" ? (record.payload as Record<string, unknown>) : {};
  if (record.name === "session.auth") {
    if (payload.ok === true) {
      const deviceName = cleanTitle(payload.deviceName) ?? undefined;
      return { type: "auth-accepted", deviceName, sessionId: cleanId(payload.sessionId) };
    }
    return { type: "auth-rejected" };
  }
  if (record.name === "session.capabilities") return { type: "capabilities" };
  if (record.name === "window.activate" || record.name === "session.window") {
    const title = cleanTitle(payload.title);
    if (!title) return { type: "invalid" };
    return { type: "window-title", title };
  }
  if (record.name === "session.error") {
    const code = cleanId(payload.code) ?? "SESSION_ERROR";
    return {
      type: "session-error",
      code,
      message: knownErrorMessages[code] ?? "O PC recusou o comando.",
    };
  }
  return { type: "invalid" };
}
