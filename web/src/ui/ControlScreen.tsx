import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";
import { applyModifier, initialModifiers, type ModifierGesture, type ModifierId, type ModifierState } from "../input/modifiers";
import { isTap, pointerDelta, railScroll } from "../input/pointer";
import { textAction } from "../input/textAction";
import {
  clipboardReason,
  controlBlockReason,
  gyroReason,
  requestPower,
  statusLabel,
  type PowerKind,
} from "../session/session";
import { AppsIcon, BackIcon, BrowserIcon, KeyboardIcon, MediaIcon, MenuIcon, MoreIcon } from "./icons";
import { tapSound } from "./feedback";
import type { PcFlowModel } from "./usePcFlow";

type PanelId = "menu" | "media" | "apps" | "browser" | "keyboard" | "more";

const powerCopy: Record<PowerKind, string> = {
  shutdown: "Desligar o PC agora?",
  sleep: "Colocar o PC em repouso agora?",
  restart: "Reiniciar o PC agora?",
  logoff: "Encerrar a sessão do PC agora?",
};

const browserActions = [
  ["back", "Voltar"],
  ["forward", "Avançar"],
  ["new", "Nova aba"],
  ["close", "Fechar"],
  ["zoomOut", "Zoom menos"],
  ["zoomIn", "Zoom mais"],
  ["reload", "Recarregar"],
  ["home", "Início"],
] as const;

const specialKeys = [
  ["Escape", "Esc"],
  ["Tab", "Tab"],
  ["Insert", "Insert"],
  ["Home", "Home"],
  ["PageUp", "PgUp"],
  ["Delete", "Delete"],
  ["End", "End"],
  ["PageDown", "PgDn"],
  ["ArrowUp", "Cima"],
  ["ArrowLeft", "Esquerda"],
  ["ArrowDown", "Baixo"],
  ["ArrowRight", "Direita"],
  ...Array.from({ length: 12 }, (_, index) => [`F${index + 1}`, `F${index + 1}`] as const),
  ["Numpad7", "7"],
  ["Numpad8", "8"],
  ["Numpad9", "9"],
  ["NumpadDivide", "/"],
  ["Numpad4", "4"],
  ["Numpad5", "5"],
  ["Numpad6", "6"],
  ["NumpadMultiply", "*"],
  ["Numpad1", "1"],
  ["Numpad2", "2"],
  ["Numpad3", "3"],
  ["NumpadSubtract", "−"],
  ["Numpad0", "0"],
  ["NumpadDecimal", "."],
  ["NumpadEnter", "Enter"],
  ["NumpadAdd", "+"],
] as const;

export function ControlScreen({ model }: { model: PcFlowModel }) {
  const { state, settings } = model;
  const [panel, setPanel] = useState<PanelId>("menu");
  const [notice, setNotice] = useState<string | null>(null);
  const [dot, setDot] = useState<{ x: number; y: number } | null>(null);
  const [fn, setFn] = useState(false);
  const [text, setText] = useState("");
  const [modifiers, setModifiers] = useState<ModifierState>(initialModifiers);
  const [pendingPower, setPendingPower] = useState<PowerKind | null>(null);
  const origin = useRef<{ x: number; y: number; moved: number } | null>(null);
  const railOrigin = useRef<number | null>(null);
  const modifierTimers = useRef<Partial<Record<ModifierId, number>>>({});
  const modifierHeld = useRef<Partial<Record<ModifierId, boolean>>>({});
  const lastTap = useRef<Partial<Record<ModifierId, number>>>({});

  useEffect(() => {
    if (panel === "media" && !settings.panels.media) setPanel("menu");
    if (panel === "apps" && !settings.panels.apps) setPanel("menu");
    if (panel === "browser" && !settings.panels.browser) setPanel("menu");
    if (panel === "keyboard" && !settings.panels.keyboard) setPanel("menu");
  }, [panel, settings.panels]);

  useEffect(() => {
    setNotice(null);
    if (state.phase !== "ready") {
      setModifiers(initialModifiers());
      setText("");
      setPendingPower(null);
    }
  }, [state.phase]);

  const blocked = controlBlockReason(state);
  const statusText = notice ?? state.serverNotice ?? blocked ?? "Sessão pronta. Os comandos seguem para o PC.";

  function explain(reason: string | null) {
    if (reason) setNotice(reason);
    tapSound(settings.touchSounds);
  }

  function sendNamed(name: string, payload: Record<string, string | number | boolean | null>, confirmed = false) {
    const result = model.send({ name, payload, confirmed });
    if (!result.ok) explain(result.reason);
    else tapSound(settings.touchSounds);
    return result.ok;
  }

  function onTouchDown(event: ReactPointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture(event.pointerId);
    origin.current = { x: event.clientX, y: event.clientY, moved: 0 };
    if (settings.inputFeedback) {
      const bounds = event.currentTarget.getBoundingClientRect();
      setDot({ x: event.clientX - bounds.left, y: event.clientY - bounds.top });
    }
  }

  function onTouchMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (!origin.current) return;
    const delta = pointerDelta(origin.current, { x: event.clientX, y: event.clientY }, settings.pointerSpeed);
    origin.current = {
      x: event.clientX,
      y: event.clientY,
      moved: origin.current.moved + Math.abs(delta.dx) + Math.abs(delta.dy),
    };
    if (settings.inputFeedback) {
      const bounds = event.currentTarget.getBoundingClientRect();
      setDot({ x: event.clientX - bounds.left, y: event.clientY - bounds.top });
    }
    if (delta.dx === 0 && delta.dy === 0) return;
    sendNamed("pointer.move", { dx: delta.dx, dy: delta.dy });
  }

  function onTouchUp() {
    if (origin.current && isTap(origin.current.moved)) {
      const down = model.send({ name: "pointer.button", payload: { action: "down", button: "left" } });
      if (!down.ok) explain(down.reason);
      else {
        model.send({ name: "pointer.button", payload: { action: "up", button: "left" } });
        tapSound(settings.touchSounds);
      }
    }
    origin.current = null;
    setDot(null);
  }

  function scrollBy(deltaY: number) {
    const delta = railScroll(deltaY, settings.scrollSpeed, settings.naturalScroll);
    if (delta === 0) return;
    sendNamed("pointer.scroll", { delta });
  }

  function mouseDown(button: "left" | "middle" | "right") {
    if (sendNamed("pointer.button", { action: "down", button })) {
      model.hold({ kind: "button", code: button });
    }
  }

  function mouseUp(button: "left" | "middle" | "right") {
    model.releaseLocal({ kind: "button", code: button });
    sendNamed("pointer.button", { action: "up", button });
  }

  function applyGesture(gesture: ModifierGesture) {
    if (blocked) {
      explain(blocked);
      return;
    }
    const effect = applyModifier(modifiers, gesture, settings.modifierOnTap);
    setModifiers(effect.state);
    for (const pulse of effect.send) {
      const ok = sendNamed("keyboard.key", { action: pulse.action, code: pulse.code });
      if (!ok) return;
      if (pulse.action === "down" && effect.state[gesture.id] !== "inactive") {
        model.hold({ kind: "key", code: pulse.code });
      }
      if (pulse.action === "up") model.releaseLocal({ kind: "key", code: pulse.code });
    }
  }

  function modifierPointerDown(id: ModifierId) {
    modifierHeld.current[id] = false;
    window.clearTimeout(modifierTimers.current[id]);
    modifierTimers.current[id] = window.setTimeout(() => {
      modifierHeld.current[id] = true;
      applyGesture({ type: "hold-start", id });
    }, 400);
  }

  function modifierPointerUp(id: ModifierId) {
    window.clearTimeout(modifierTimers.current[id]);
    if (modifierHeld.current[id]) {
      modifierHeld.current[id] = false;
      applyGesture({ type: "hold-end", id });
      return;
    }
    const now = performance.now();
    if (now - (lastTap.current[id] ?? 0) < 350) {
      lastTap.current[id] = 0;
      applyGesture({ type: "lock", id });
      return;
    }
    lastTap.current[id] = now;
    applyGesture({ type: "tap", id });
  }

  function tapKey(code: string) {
    if (!sendNamed("keyboard.key", { action: "down", code })) return;
    sendNamed("keyboard.key", { action: "up", code });
  }

  function submitText(via: "button" | "enter") {
    if (blocked) {
      explain(blocked);
      return;
    }
    const action = textAction(settings.keyboardBuffer, via);
    if (action === "send-enter") {
      tapKey("Enter");
      return;
    }
    if (!text) return;
    if (sendNamed("keyboard.text", { text })) setText("");
  }

  function askPower(kind: PowerKind) {
    const decision = requestPower(state, kind);
    if (decision.type === "blocked") explain(decision.reason);
    else setPendingPower(kind);
  }

  return (
    <section className="control">
      <header className="header">
        <a className="icon-button" href="#/conectar" aria-label="Voltar para computadores">
          <BackIcon />
        </a>
        <div className="titles">
          <h1>{state.deviceName ?? "Nenhum PC"}</h1>
          <p>{state.phase === "ready" ? (state.windowTitle ?? "Janela desconhecida") : "Nenhuma janela"}</p>
        </div>
        <p className="pill" data-phase={state.phase}>
          {statusLabel(state)}
        </p>
      </header>
      <p className="banner" role="status">
        {statusText}
      </p>
      <div className="stage">
        <div
          className="touchpad"
          role="application"
          aria-label="Touchpad"
          onPointerDown={onTouchDown}
          onPointerMove={onTouchMove}
          onPointerUp={onTouchUp}
          onPointerCancel={onTouchUp}
        >
          {dot ? <span className="dot" style={{ left: dot.x, top: dot.y }} /> : null}
        </div>
        <div
          className="rail"
          aria-label="Trilho de scroll"
          onPointerDown={(event) => {
            railOrigin.current = event.clientY;
          }}
          onPointerMove={(event) => {
            if (railOrigin.current === null) return;
            const delta = event.clientY - railOrigin.current;
            railOrigin.current = event.clientY;
            scrollBy(delta);
          }}
          onPointerUp={() => {
            railOrigin.current = null;
          }}
        >
          <button type="button" aria-label="Rolar para cima" onClick={() => scrollBy(-40)}>
            ↑
          </button>
          <span />
          <button type="button" aria-label="Rolar para baixo" onClick={() => scrollBy(40)}>
            ↓
          </button>
        </div>
      </div>
      <div className="mouse-row">
        {settings.showMouseButtons ? (
          <>
            <button type="button" aria-label="Botão esquerdo" onPointerDown={() => mouseDown("left")} onPointerUp={() => mouseUp("left")} onPointerCancel={() => mouseUp("left")} />
            <button type="button" aria-label="Botão do meio" onPointerDown={() => mouseDown("middle")} onPointerUp={() => mouseUp("middle")} onPointerCancel={() => mouseUp("middle")} />
            <button type="button" aria-label="Botão direito" onPointerDown={() => mouseDown("right")} onPointerUp={() => mouseUp("right")} onPointerCancel={() => mouseUp("right")} />
          </>
        ) : null}
        {settings.showGyroButton ? (
          <button type="button" className="gyro" aria-label="Gyro mouse" onClick={() => explain(gyroReason(state))}>
            <span />
          </button>
        ) : null}
      </div>
      <div className="toolbar" role="toolbar" aria-label="Módulos">
        <ToolButton label="Menu" pressed={panel === "menu"} onClick={() => setPanel("menu")}>
          <MenuIcon />
        </ToolButton>
        {settings.panels.media ? (
          <ToolButton label="Mídia" pressed={panel === "media"} onClick={() => setPanel("media")}>
            <MediaIcon />
          </ToolButton>
        ) : null}
        {settings.panels.apps ? (
          <ToolButton label="Apps" pressed={panel === "apps"} onClick={() => setPanel("apps")}>
            <AppsIcon />
          </ToolButton>
        ) : null}
        {settings.panels.browser ? (
          <ToolButton label="Navegador" pressed={panel === "browser"} onClick={() => setPanel("browser")}>
            <BrowserIcon />
          </ToolButton>
        ) : null}
        {settings.panels.keyboard ? (
          <ToolButton label="Teclado" pressed={panel === "keyboard"} onClick={() => setPanel("keyboard")}>
            <KeyboardIcon />
          </ToolButton>
        ) : null}
        <ToolButton label="Mais" pressed={panel === "more"} onClick={() => setPanel("more")}>
          <MoreIcon />
        </ToolButton>
      </div>
      <div className={panel === "keyboard" ? "panel panel-keyboard" : "panel"} role="region" aria-label="Painel">
        {panel === "menu" ? <MenuPanel /> : null}
        {panel === "media" ? (
          <div className="media">
            <div className="radial" role="group" aria-label="Mídia">
              <button type="button" className="up" aria-label="Aumentar volume" onClick={() => sendNamed("media.volume", { action: "up" })}>
                +
              </button>
              <button type="button" className="left" aria-label="Faixa anterior" onClick={() => sendNamed("media.transport", { action: "previous" })}>
                ‹
              </button>
              <button type="button" className="center" aria-label="Reproduzir ou pausar" onClick={() => sendNamed("media.transport", { action: "playpause" })}>
                ▶❚❚
              </button>
              <button type="button" className="right" aria-label="Próxima faixa" onClick={() => sendNamed("media.transport", { action: "next" })}>
                ›
              </button>
              <button type="button" className="down" aria-label="Diminuir volume" onClick={() => sendNamed("media.volume", { action: "down" })}>
                −
              </button>
            </div>
            <button type="button" className="stop" aria-label="Parar" onClick={() => sendNamed("media.transport", { action: "stop" })}>
              ■
            </button>
          </div>
        ) : null}
        {panel === "apps" ? <p className="empty">Nenhum aplicativo. A lista vem do PC conectado.</p> : null}
        {panel === "browser" ? (
          <div className="browser-grid">
            {browserActions.map(([action, label]) => (
              <button key={action} type="button" onClick={() => sendNamed("browser.command", { action })}>
                {label}
              </button>
            ))}
            <p className="empty">Nenhum favorito. Eles só aparecem quando o agente enviar a lista.</p>
          </div>
        ) : null}
        {panel === "keyboard" ? (
          <div className="keyboard">
            <div className="modifier-row">
              <button type="button" aria-pressed={fn} onClick={() => setFn((value) => !value)}>
                Fn
              </button>
              {(["Shift", "Control", "Meta", "Alt"] as const).map((id) => (
                <button
                  key={id}
                  type="button"
                  aria-pressed={modifiers[id] !== "inactive"}
                  onPointerDown={() => modifierPointerDown(id)}
                  onPointerUp={() => modifierPointerUp(id)}
                  onPointerCancel={() => modifierPointerUp(id)}
                >
                  {id === "Meta" ? "Win" : id === "Control" ? "Ctrl" : id}
                </button>
              ))}
            </div>
            {fn ? (
              <div className="key-grid">
                {specialKeys.map(([code, label]) => (
                  <button key={code} type="button" onClick={() => tapKey(code)}>
                    {label}
                  </button>
                ))}
              </div>
            ) : (
              <>
                <label>
                  Texto para o PC
                  <input
                    value={text}
                    disabled={state.phase !== "ready"}
                    aria-describedby="teclado-ajuda"
                    onChange={(event) => setText(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key !== "Enter") return;
                      event.preventDefault();
                      submitText("enter");
                    }}
                  />
                </label>
                <p id="teclado-ajuda" className="help">
                  As letras ficam no teclado do sistema. Sem sessão, este campo não envia.
                </p>
                <button type="button" onClick={() => submitText("button")}>
                  Enviar texto
                </button>
              </>
            )}
          </div>
        ) : null}
        {panel === "more" ? (
          <div className="more">
            <h2>Energia</h2>
            <div className="power-grid">
              <button type="button" className="danger" onClick={() => askPower("shutdown")}>
                Desligar
              </button>
              <button type="button" className="danger" onClick={() => askPower("sleep")}>
                Repouso
              </button>
              <button type="button" className="danger" onClick={() => askPower("restart")}>
                Reiniciar
              </button>
              <button type="button" className="danger" onClick={() => askPower("logoff")}>
                Encerrar sessão
              </button>
            </div>
            <h2>Área de transferência</h2>
            <button type="button" onClick={() => explain(clipboardReason(state))}>
              Texto para o PC
            </button>
            <button type="button" onClick={() => explain(clipboardReason(state))}>
              Imagem para o PC
            </button>
            <button type="button" onClick={() => explain(clipboardReason(state))}>
              Trazer do PC
            </button>
            <p className="help">Nenhum destes botões lê ou escreve a área de transferência.</p>
            {state.phase === "ready" ? (
              <button type="button" onClick={() => model.disconnect()}>
                Desconectar
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
      {pendingPower ? (
        <div className="dialog-backdrop">
          <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="power-title" onKeyDown={(event) => {
            if (event.key === "Escape") setPendingPower(null);
          }}>
            <h2 id="power-title">{powerCopy[pendingPower]}</h2>
            <p>A ação só segue depois desta confirmação. O agente ainda pode recusar pela política.</p>
            <button
              type="button"
              className="danger"
              onClick={() => {
                sendNamed("system.power", { kind: pendingPower }, true);
                setPendingPower(null);
              }}
            >
              Confirmar
            </button>
            <button type="button" onClick={() => setPendingPower(null)}>
              Cancelar
            </button>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function MenuPanel() {
  return (
    <nav className="menu-links" aria-label="Menu do controle">
      <a href="#/ajustes">Ajustes</a>
      <a href="#/ajuda">Ajuda</a>
      <a href="#/paridade">O que este navegador faz</a>
      <a href="#/conta">Conta</a>
    </nav>
  );
}

function ToolButton({
  label,
  pressed,
  onClick,
  children,
}: {
  label: string;
  pressed: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button type="button" aria-label={label} aria-pressed={pressed} onClick={onClick}>
      {children}
    </button>
  );
}
