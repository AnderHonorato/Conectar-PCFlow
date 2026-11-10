export type WallpaperId = "carvao" | "verde" | "noite" | "musgo";

export interface PanelVisibility {
  media: boolean;
  apps: boolean;
  browser: boolean;
  keyboard: boolean;
}

export interface WebSettings {
  showMouseButtons: boolean;
  showGyroButton: boolean;
  oneHand: boolean;
  leftHanded: boolean;
  pointerSpeed: number;
  scrollSpeed: number;
  naturalScroll: boolean;
  inputFeedback: boolean;
  keyboardBuffer: boolean;
  modifierOnTap: boolean;
  touchSounds: boolean;
  autoReconnect: boolean;
  keepAwake: boolean;
  portraitLock: boolean;
  panels: PanelVisibility;
  wallpaper: WallpaperId;
}

export const defaultSettings: WebSettings = {
  showMouseButtons: true,
  showGyroButton: true,
  oneHand: false,
  leftHanded: false,
  pointerSpeed: 1,
  scrollSpeed: 1,
  naturalScroll: false,
  inputFeedback: true,
  keyboardBuffer: false,
  modifierOnTap: true,
  touchSounds: false,
  autoReconnect: true,
  keepAwake: false,
  portraitLock: false,
  panels: { media: true, apps: true, browser: true, keyboard: true },
  wallpaper: "carvao",
};

const wallpapers = new Set<WallpaperId>(["carvao", "verde", "noite", "musgo"]);

function boolean(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function speed(value: unknown, fallback: number, min: number, max: number): number {
  if (typeof value !== "number" || Number.isNaN(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}

export function sanitizeSettings(raw: unknown): WebSettings {
  const source = raw && typeof raw === "object" ? (raw as Partial<WebSettings>) : {};
  const panels: Partial<PanelVisibility> =
    source.panels && typeof source.panels === "object" ? source.panels : {};
  const wallpaper = wallpapers.has(source.wallpaper as WallpaperId) ? (source.wallpaper as WallpaperId) : defaultSettings.wallpaper;
  return {
    showMouseButtons: boolean(source.showMouseButtons, defaultSettings.showMouseButtons),
    showGyroButton: boolean(source.showGyroButton, defaultSettings.showGyroButton),
    oneHand: boolean(source.oneHand, defaultSettings.oneHand),
    leftHanded: boolean(source.leftHanded, defaultSettings.leftHanded),
    pointerSpeed: speed(source.pointerSpeed, defaultSettings.pointerSpeed, 0.4, 2.4),
    scrollSpeed: speed(source.scrollSpeed, defaultSettings.scrollSpeed, 0.5, 2),
    naturalScroll: boolean(source.naturalScroll, defaultSettings.naturalScroll),
    inputFeedback: boolean(source.inputFeedback, defaultSettings.inputFeedback),
    keyboardBuffer: boolean(source.keyboardBuffer, defaultSettings.keyboardBuffer),
    modifierOnTap: boolean(source.modifierOnTap, defaultSettings.modifierOnTap),
    touchSounds: boolean(source.touchSounds, defaultSettings.touchSounds),
    autoReconnect: boolean(source.autoReconnect, defaultSettings.autoReconnect),
    keepAwake: boolean(source.keepAwake, defaultSettings.keepAwake),
    portraitLock: boolean(source.portraitLock, defaultSettings.portraitLock),
    panels: {
      media: boolean(panels.media, true),
      apps: boolean(panels.apps, true),
      browser: boolean(panels.browser, true),
      keyboard: boolean(panels.keyboard, true),
    },
    wallpaper,
  };
}

export function railSide(settings: WebSettings): "left" | "right" {
  return settings.leftHanded ? "left" : "right";
}

export interface KnownComputer {
  url: string;
  deviceName: string | null;
}

export function rememberComputer(list: readonly KnownComputer[], item: KnownComputer): KnownComputer[] {
  const rest = list.filter((computer) => computer.url !== item.url);
  return [{ url: item.url, deviceName: item.deviceName }, ...rest].slice(0, 8);
}

export function forgetComputer(list: readonly KnownComputer[], url: string): KnownComputer[] {
  return list.filter((computer) => computer.url !== url);
}

export async function applyKeepAwake(
  enabled: boolean,
  locks: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> } | null,
  current: { release: () => Promise<void> } | null,
): Promise<{ sentinel: { release: () => Promise<void> } | null; reason: string | null }> {
  if (!enabled) {
    await current?.release().catch(() => undefined);
    return { sentinel: null, reason: null };
  }
  if (!locks) {
    return { sentinel: null, reason: "Este navegador não oferece a API de manter a tela acesa." };
  }
  try {
    const sentinel = await locks.request("screen");
    return { sentinel, reason: null };
  } catch {
    return { sentinel: null, reason: "O navegador recusou manter a tela acesa." };
  }
}

export async function applyPortraitLock(
  enabled: boolean,
  orientation: { lock: (value: "portrait") => Promise<void>; unlock: () => void } | null,
): Promise<string | null> {
  if (!orientation) {
    return enabled ? "Este navegador não oferece trava de orientação." : null;
  }
  if (!enabled) {
    orientation.unlock();
    return null;
  }
  try {
    await orientation.lock("portrait");
    return null;
  } catch {
    return "O navegador recusou a trava de retrato.";
  }
}
