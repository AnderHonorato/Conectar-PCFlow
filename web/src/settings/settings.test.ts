import { describe, expect, it } from "vitest";
import { pagesBase } from "../config";
import {
  applyKeepAwake,
  applyPortraitLock,
  defaultSettings,
  forgetComputer,
  railSide,
  rememberComputer,
  sanitizeSettings,
} from "./settings";

describe("ajustes locais", () => {
  it("ignora chave desconhecida e limita a velocidade", () => {
    const settings = sanitizeSettings({ pointerSpeed: 9, scrollSpeed: 0, temaCopiado: true, wallpaper: "arco-iris" });
    expect(settings.pointerSpeed).toBe(2.4);
    expect(settings.scrollSpeed).toBe(0.5);
    expect(settings.wallpaper).toBe(defaultSettings.wallpaper);
    expect("temaCopiado" in settings).toBe(false);
    expect(railSide({ ...defaultSettings, leftHanded: true })).toBe("left");
    expect(railSide(defaultSettings)).toBe("right");
  });

  it("lembra o endereço sem acumular segredo", () => {
    const once = rememberComputer([], { url: "wss://pc.local/pcflow", deviceName: null });
    const twice = rememberComputer(once, { url: "wss://pc.local/pcflow", deviceName: "Sala" });
    expect(twice).toHaveLength(1);
    expect(twice[0]?.deviceName).toBe("Sala");
    expect(forgetComputer(twice, "wss://pc.local/pcflow")).toEqual([]);
  });

  it("declara a limitação quando o navegador não tem vigília nem orientação", async () => {
    const wake = await applyKeepAwake(true, null, null);
    expect(wake.sentinel).toBeNull();
    expect(wake.reason).toMatch(/não oferece/);
    const orientation = await applyPortraitLock(true, null);
    expect(orientation).toMatch(/não oferece/);
  });

  it("aponta o base do Vite para o GitHub Pages", () => {
    expect(pagesBase).toBe("/Conectar-PCFlow/");
  });
});
