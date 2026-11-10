import type { WallpaperId } from "../settings/settings";
import type { PcFlowModel } from "./usePcFlow";
import { SectionTabs } from "./InfoScreens";

const wallpapers: { id: WallpaperId; label: string }[] = [
  { id: "carvao", label: "Carvão" },
  { id: "verde", label: "Verde" },
  { id: "noite", label: "Noite" },
  { id: "musgo", label: "Musgo" },
];

export function SettingsScreen({ model }: { model: PcFlowModel }) {
  const { settings, updateSettings } = model;

  return (
    <section className="scroll-screen">
      <header className="page-head row-head">
        <a className="back" href="#/conectar">
          Voltar
        </a>
        <h1>Configurações</h1>
      </header>
      <SectionTabs current="ajustes" />
      {model.platformNotice ? <p className="alert">{model.platformNotice}</p> : null}

      <h2>Conta</h2>
      <a className="row" href="#/conta">
        <span>
          <strong>Conta não configurada</strong>
          <small>O backend de conta ainda não foi ligado.</small>
        </span>
        <span aria-hidden="true">›</span>
      </a>
      <a className="row" href="#/pro">
        <span>
          <strong>PCFlow Pro</strong>
          <small>Pagamento não configurado</small>
        </span>
        <span aria-hidden="true">›</span>
      </a>

      <h2>Interface</h2>
      <details className="row details">
        <summary>Personalizar painéis</summary>
        <PanelToggle label="Mídia" checked={settings.panels.media} onChange={(media) => updateSettings({ panels: { media } })} />
        <PanelToggle label="Aplicativos" checked={settings.panels.apps} onChange={(apps) => updateSettings({ panels: { apps } })} />
        <PanelToggle label="Navegador" checked={settings.panels.browser} onChange={(browser) => updateSettings({ panels: { browser } })} />
        <PanelToggle label="Teclado" checked={settings.panels.keyboard} onChange={(keyboard) => updateSettings({ panels: { keyboard } })} />
      </details>
      <Switch
        label="Botões do mouse"
        checked={settings.showMouseButtons}
        onChange={(showMouseButtons) => updateSettings({ showMouseButtons })}
      />
      <Switch
        label="Botão Gyro Mouse"
        subtitle="O botão aparece no controle. O sensor do navegador não é lido neste cliente."
        checked={settings.showGyroButton}
        onChange={(showGyroButton) => updateSettings({ showGyroButton })}
      />
      <Switch
        label="Controle com uma mão"
        subtitle="Aumenta a largura do trilho de scroll."
        checked={settings.oneHand}
        onChange={(oneHand) => updateSettings({ oneHand })}
      />
      <Switch
        label="Modo canhoto"
        subtitle="Inverte o trilho e os botões."
        checked={settings.leftHanded}
        onChange={(leftHanded) => updateSettings({ leftHanded })}
      />

      <h2>Touchpad</h2>
      <Slider
        label="Velocidade do movimento"
        minLabel="Devagar"
        maxLabel="Rápido"
        min={0.4}
        max={2.4}
        step={0.1}
        value={settings.pointerSpeed}
        onChange={(pointerSpeed) => updateSettings({ pointerSpeed })}
      />
      <details className="row details">
        <summary>Avançado</summary>
        <Slider
          label="Velocidade do scroll"
          minLabel="Curto"
          maxLabel="Longo"
          min={0.5}
          max={2}
          step={0.1}
          value={settings.scrollSpeed}
          onChange={(scrollSpeed) => updateSettings({ scrollSpeed })}
        />
        <Switch
          label="Scroll natural"
          subtitle="Inverte a direção do trilho."
          checked={settings.naturalScroll}
          onChange={(naturalScroll) => updateSettings({ naturalScroll })}
        />
      </details>

      <h2>Entrada</h2>
      <Switch
        label="Mostrar feedback de entrada"
        subtitle="Desenha o ponto do dedo no touchpad."
        checked={settings.inputFeedback}
        onChange={(inputFeedback) => updateSettings({ inputFeedback })}
      />
      <Switch
        label="Buffer do teclado"
        subtitle="Ligado, o Enter envia o texto acumulado. Desligado, o Enter envia a tecla Enter."
        checked={settings.keyboardBuffer}
        onChange={(keyboardBuffer) => updateSettings({ keyboardBuffer })}
      />
      <Switch
        label="Modificador ao tocar"
        subtitle="Ligado: um toque envia a tecla, segurar ativa e um segundo toque trava."
        checked={settings.modifierOnTap}
        onChange={(modifierOnTap) => updateSettings({ modifierOnTap })}
      />

      <h2>Geral</h2>
      <div className="row static">
        <span>
          <strong>Botões de volume do aparelho</strong>
          <small>Indisponíveis neste navegador. O volume do PC fica no painel de mídia.</small>
        </span>
      </div>
      <Switch
        label="Sons de toque"
        checked={settings.touchSounds}
        onChange={(touchSounds) => updateSettings({ touchSounds })}
      />
      <Switch
        label="Reconexão automática"
        subtitle="Na próxima abertura, tenta o último endereço que chegou a abrir o socket."
        checked={settings.autoReconnect}
        onChange={(autoReconnect) => updateSettings({ autoReconnect })}
      />
      <Switch
        label="Manter a tela acesa"
        subtitle="Depende da API do navegador."
        checked={settings.keepAwake}
        onChange={(keepAwake) => updateSettings({ keepAwake })}
      />
      <Switch
        label="Bloquear modo retrato"
        subtitle="Depende da API de orientação."
        checked={settings.portraitLock}
        onChange={(portraitLock) => updateSettings({ portraitLock })}
      />

      <h2>Aparência</h2>
      <div className="row static">
        <span>
          <strong>Tema</strong>
          <small>Escuro</small>
        </span>
      </div>
      <div className="wallpaper-pick">
        <span>Fundo do touchpad</span>
        <div>
          {wallpapers.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`swatch swatch-${item.id}`}
              aria-pressed={settings.wallpaper === item.id}
              onClick={() => updateSettings({ wallpaper: item.id })}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div className="row static">
        <span>
          <strong>Idioma</strong>
          <small>Português</small>
        </span>
      </div>

      <h2>Sobre este navegador</h2>
      <a className="row" href="#/paridade">
        <span>
          <strong>O que este navegador faz</strong>
          <small>Limites reais de plataforma, sem promessa universal.</small>
        </span>
        <span aria-hidden="true">›</span>
      </a>
      <a className="row" href="#/ajuda">
        <span>
          <strong>Ajuda</strong>
          <small>Texto do PCFlow.</small>
        </span>
        <span aria-hidden="true">›</span>
      </a>
    </section>
  );
}

function Switch({
  label,
  subtitle,
  checked,
  onChange,
}: {
  label: string;
  subtitle?: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  const id = `ajuste-${label.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div className="row">
      <span>
        <strong id={id}>{label}</strong>
        {subtitle ? <small>{subtitle}</small> : null}
      </span>
      <button type="button" className="switch" role="switch" aria-checked={checked} aria-labelledby={id} onClick={() => onChange(!checked)}>
        <span />
      </button>
    </div>
  );
}

function Slider({
  label,
  minLabel,
  maxLabel,
  min,
  max,
  step,
  value,
  onChange,
}: {
  label: string;
  minLabel: string;
  maxLabel: string;
  min: number;
  max: number;
  step: number;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="row slider">
      <span>
        <strong>{label}</strong>
        <small>
          {minLabel} — {maxLabel}
        </small>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={value}
        onChange={(event) => onChange(Number(event.target.value))}
      />
    </label>
  );
}

function PanelToggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return <Switch label={label} checked={checked} onChange={onChange} />;
}
