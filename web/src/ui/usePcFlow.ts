import { useEffect, useRef, useState } from "react";
import { SessionController } from "../session/controller";
import type { SessionState } from "../session/session";
import { createWebSocketTransport } from "../transport/transport";
import {
  applyKeepAwake,
  applyPortraitLock,
  forgetComputer,
  type KnownComputer,
  rememberComputer,
  sanitizeSettings,
  type WebSettings,
} from "../settings/settings";

export interface PcFlowModel {
  state: SessionState;
  settings: WebSettings;
  known: KnownComputer[];
  platformNotice: string | null;
  updateSettings: (patch: Partial<Omit<WebSettings, "panels">> & { panels?: Partial<WebSettings["panels"]> }) => void;
  connectTo: (url: string, pin: string) => void;
  disconnect: () => void;
  forget: (url: string) => void;
  send: SessionController["send"];
  hold: SessionController["hold"];
  releaseLocal: SessionController["releaseLocal"];
}

const settingsKey = "pcflow.settings";
const knownKey = "pcflow.known";
const lastKey = "pcflow.last-endpoint";

function readJson(key: string): unknown {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as unknown) : null;
  } catch {
    return null;
  }
}

function readKnown(): KnownComputer[] {
  const raw = readJson(knownKey);
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((item) => {
    if (!item || typeof item !== "object") return [];
    const record = item as { url?: unknown; deviceName?: unknown };
    if (typeof record.url !== "string") return [];
    if (!record.url.startsWith("ws://") && !record.url.startsWith("wss://")) return [];
    return [{ url: record.url, deviceName: typeof record.deviceName === "string" ? record.deviceName : null }];
  });
}

export function usePcFlow(): PcFlowModel {
  const controllerRef = useRef<SessionController | null>(null);
  if (!controllerRef.current) {
    controllerRef.current = new SessionController((url) => createWebSocketTransport(url), import.meta.env.DEV);
  }
  const controller = controllerRef.current;
  const [state, setState] = useState<SessionState>(controller.snapshot);
  const [settings, setSettings] = useState<WebSettings>(() => sanitizeSettings(readJson(settingsKey)));
  const [known, setKnown] = useState<KnownComputer[]>(readKnown);
  const [platformNotice, setPlatformNotice] = useState<string | null>(null);
  const booted = useRef(false);
  const previousPhase = useRef(state.phase);

  useEffect(() => controller.subscribe(() => setState(controller.snapshot)), [controller]);

  useEffect(() => {
    try {
      localStorage.setItem(settingsKey, JSON.stringify(settings));
    } catch {
      /* armazenamento local indisponível */
    }
  }, [settings]);

  useEffect(() => {
    try {
      localStorage.setItem(knownKey, JSON.stringify(known));
    } catch {
      /* armazenamento local indisponível */
    }
  }, [known]);

  useEffect(() => {
    if (booted.current) return;
    booted.current = true;
    if (!settings.autoReconnect) return;
    let last: string | null = null;
    try {
      last = localStorage.getItem(lastKey);
    } catch {
      last = null;
    }
    if (last) controller.connect(last, "");
  }, [controller, settings.autoReconnect]);

  useEffect(() => {
    if (!state.endpoint) return;
    if (state.phase !== "authenticating" && state.phase !== "negotiating" && state.phase !== "ready") return;
    setKnown((list) => rememberComputer(list, { url: state.endpoint as string, deviceName: state.deviceName }));
    try {
      localStorage.setItem(lastKey, state.endpoint);
    } catch {
      /* armazenamento local indisponível */
    }
  }, [state.phase, state.endpoint, state.deviceName]);

  useEffect(() => {
    if (previousPhase.current !== "ready" && state.phase === "ready") {
      window.location.hash = "#/controle";
    }
    previousPhase.current = state.phase;
  }, [state.phase]);

  useEffect(() => {
    let stopped = false;
    let sentinel: { release: () => Promise<void> } | null = null;
    const locks =
      "wakeLock" in navigator && navigator.wakeLock
        ? { request: (type: "screen") => navigator.wakeLock.request(type) }
        : null;
    void applyKeepAwake(settings.keepAwake, locks, null).then((result) => {
      if (stopped) {
        void result.sentinel?.release();
        return;
      }
      sentinel = result.sentinel;
      if (result.reason) setPlatformNotice(result.reason);
    });
    return () => {
      stopped = true;
      void sentinel?.release();
    };
  }, [settings.keepAwake]);

  useEffect(() => {
    const orientation = screen.orientation as
      | { lock?: (value: "portrait") => Promise<void>; unlock?: () => void }
      | undefined;
    const api =
      orientation && typeof orientation.lock === "function" && typeof orientation.unlock === "function"
        ? { lock: orientation.lock.bind(orientation), unlock: orientation.unlock.bind(orientation) }
        : null;
    void applyPortraitLock(settings.portraitLock, api).then((reason) => {
      if (reason) setPlatformNotice(reason);
    });
  }, [settings.portraitLock]);

  return {
    state,
    settings,
    known,
    platformNotice,
    updateSettings(patch) {
      setSettings((current) =>
        sanitizeSettings({
          ...current,
          ...patch,
          panels: { ...current.panels, ...patch.panels },
        }),
      );
    },
    connectTo(url, pin) {
      setPlatformNotice(null);
      controller.connect(url, pin);
    },
    disconnect() {
      controller.disconnect();
    },
    forget(url) {
      setKnown((list) => forgetComputer(list, url));
      try {
        if (localStorage.getItem(lastKey) === url) localStorage.removeItem(lastKey);
      } catch {
        /* armazenamento local indisponível */
      }
    },
    send: controller.send.bind(controller),
    hold: controller.hold.bind(controller),
    releaseLocal: controller.releaseLocal.bind(controller),
  };
}
