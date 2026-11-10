import { useState } from "react";
import { statusLabel } from "../session/session";
import type { PcFlowModel } from "./usePcFlow";

export function ConnectScreen({ model }: { model: PcFlowModel }) {
  const [address, setAddress] = useState("");
  const [pin, setPin] = useState("");
  const { state } = model;

  return (
    <section className="scroll-screen connect">
      <header className="page-head">
        <h1>PCFlow</h1>
        <p className="lede">Controle o PC na rede local. Sem conta obrigatória.</p>
        <p className="pill" data-phase={state.phase}>
          {statusLabel(state)}
        </p>
      </header>
      <h2>Computadores</h2>

      <details className="note">
        <summary>Como conectar</summary>
        <ol>
          <li>Abra o agente PCFlow no Windows, na sessão de quem vai usar o computador.</li>
          <li>Copie o endereço WebSocket que o agente mostrar. Este navegador não varre a rede e não promete Bluetooth.</li>
          <li>Cole o endereço abaixo. Se o PC pedir, use o código de 6 dígitos exibido nele.</li>
        </ol>
      </details>

      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault();
          model.connectTo(address, pin);
        }}
      >
        <label>
          Endereço do agente
          <input
            value={address}
            onChange={(event) => setAddress(event.target.value)}
            inputMode="url"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            placeholder="wss://nome-do-pc:porta/pcflow"
            aria-describedby="endereco-ajuda"
          />
        </label>
        <p id="endereco-ajuda" className="help">
          {import.meta.env.DEV
            ? "Em desenvolvimento, ws:// e wss:// são aceitos."
            : "Esta página é HTTPS. O endereço precisa ser wss://."}
        </p>
        <label>
          Código de 6 dígitos, se o PC pedir
          <input
            value={pin}
            onChange={(event) => setPin(event.target.value.replace(/\D/g, "").slice(0, 6))}
            inputMode="numeric"
            autoComplete="off"
            maxLength={6}
          />
        </label>
        <button type="submit" className="primary">
          Conectar
        </button>
      </form>

      {state.phase === "error" && state.errorMessage ? (
        <p className="alert" role="alert">
          {state.errorMessage}
        </p>
      ) : null}

      <h2>Nesta lista</h2>
      {model.known.length === 0 ? (
        <p className="empty">Nenhum computador nesta lista. O navegador não varre a rede.</p>
      ) : (
        <ul className="computers">
          {model.known.map((computer) => {
            const active = state.phase === "ready" && state.endpoint === computer.url;
            return (
              <li key={computer.url}>
                <div>
                  <strong>{computer.deviceName ?? "PC sem nome"}</strong>
                  <span>{computer.url}</span>
                  <span>{active ? "Sessão pronta" : "Sem sessão"}</span>
                </div>
                <button type="button" onClick={() => model.connectTo(computer.url, "")}>
                  Conectar
                </button>
                <button type="button" onClick={() => model.forget(computer.url)}>
                  Esquecer
                </button>
              </li>
            );
          })}
        </ul>
      )}

      <nav className="stack links" aria-label="Atalhos">
        <a className="primary link-button" href="#/controle">
          Abrir o controle
        </a>
        <a href="#/ajustes">Ajustes</a>
        <a href="#/paridade">O que este navegador faz</a>
        <a href="#/ajuda">Ajuda</a>
      </nav>
      <p className="foot">O controle do PC não passa pela nuvem. A conta, quando existir, fica fora desse caminho.</p>
    </section>
  );
}
