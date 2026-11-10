import { useEffect, useState } from "react";
import { ConnectScreen } from "./ui/ConnectScreen";
import { ControlScreen } from "./ui/ControlScreen";
import { AccountScreen, HelpScreen, ParityScreen, ProScreen } from "./ui/InfoScreens";
import { parseScreen, screenTitle, type ScreenId } from "./ui/routes";
import { SettingsScreen } from "./ui/SettingsScreen";
import { usePcFlow } from "./ui/usePcFlow";

export function App() {
  const model = usePcFlow();
  const [screen, setScreen] = useState<ScreenId>(() => parseScreen(window.location.hash));

  useEffect(() => {
    const onHash = () => setScreen(parseScreen(window.location.hash));
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  useEffect(() => {
    document.title = screenTitle[screen];
  }, [screen]);

  return (
    <div className={`app wallpaper-${model.settings.wallpaper}`}>
      <a className="skip" href="#conteudo">
        Ir ao conteúdo
      </a>
      <div
        className="device"
        id="conteudo"
        data-left-handed={model.settings.leftHanded ? "true" : "false"}
        data-one-hand={model.settings.oneHand ? "true" : "false"}
      >
        {screen === "conectar" && <ConnectScreen model={model} />}
        {screen === "controle" && <ControlScreen model={model} />}
        {screen === "ajustes" && <SettingsScreen model={model} />}
        {screen === "paridade" && <ParityScreen />}
        {screen === "ajuda" && <HelpScreen />}
        {screen === "conta" && <AccountScreen />}
        {screen === "pro" && <ProScreen />}
      </div>
    </div>
  );
}
