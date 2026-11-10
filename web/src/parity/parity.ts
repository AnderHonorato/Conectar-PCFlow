/**
 * Apêndice G — o que este navegador faz.
 * A mesma tabela alimenta a tela e docs/web/parity.md.
 * Bluetooth nunca é universal: a maioria dos navegadores não oferece o transporte.
 */

export type Support =
  | "available"
  | "requires-connection"
  | "browser-dependent"
  | "permission-required"
  | "unavailable"
  | "unconfigured";

export interface ParityRow {
  id: string;
  label: string;
  support: Support;
  detail: string;
}

export const supportLabel: Record<Support, string> = {
  available: "disponível",
  "requires-connection": "precisa de conexão",
  "browser-dependent": "dependente do navegador",
  "permission-required": "depende de permissão",
  unavailable: "indisponível",
  unconfigured: "não configurado",
};

export const parityTable: readonly ParityRow[] = [
  {
    id: "connection-list",
    label: "Lista de computadores",
    support: "requires-connection",
    detail:
      "O navegador não varre a rede. A lista só guarda endereços que chegaram a abrir o WebSocket.",
  },
  {
    id: "status",
    label: "Estado da sessão",
    support: "available",
    detail: "A tela começa desconectada. Conectado só existe depois de autenticar e de receber capacidades.",
  },
  {
    id: "lan",
    label: "Endereço WebSocket",
    support: "requires-connection",
    detail: "Sem mDNS e sem UDP. Página HTTPS exige wss://. ws:// fica restrito ao desenvolvimento.",
  },
  {
    id: "discovery",
    label: "Descoberta automática",
    support: "unavailable",
    detail: "O navegador não anuncia nem escuta a descoberta do agente na LAN.",
  },
  {
    id: "bluetooth",
    label: "Bluetooth",
    support: "browser-dependent",
    detail:
      "Não vale para todos os navegadores. Este cliente não usa Bluetooth como transporte do controle.",
  },
  {
    id: "touchpad",
    label: "Touchpad",
    support: "requires-connection",
    detail: "A área ocupa a maior parte da tela. O movimento só sai com a sessão pronta.",
  },
  {
    id: "scroll",
    label: "Trilho de scroll",
    support: "requires-connection",
    detail: "Setas e arraste no trilho. A direção segue o ajuste local de scroll natural.",
  },
  {
    id: "mouse-buttons",
    label: "Botões do mouse",
    support: "requires-connection",
    detail: "Esquerdo, meio e direito, com press e release separados quando a sessão está pronta.",
  },
  {
    id: "keyboard",
    label: "Teclado",
    support: "requires-connection",
    detail:
      "Não há alfabeto desenhado. As letras ficam no teclado do sistema e saem em bloco. Atalhos globais fora do campo não são capturados. Teclas não são registradas em log.",
  },
  {
    id: "media",
    label: "Mídia",
    support: "requires-connection",
    detail: "Reproduzir ou pausar, faixa, volume e parar. Sem botão sem nome.",
  },
  {
    id: "windows",
    label: "Janelas e aplicativos",
    support: "requires-connection",
    detail: "A lista vem do agente. Sem sessão, a tela diz que não há aplicativos.",
  },
  {
    id: "browser",
    label: "Navegador remoto",
    support: "requires-connection",
    detail: "Voltar, avançar, nova aba, fechar, zoom, recarregar e início. Comandos sem rótulo não entram.",
  },
  {
    id: "power",
    label: "Energia",
    support: "requires-connection",
    detail: "Desligar, repouso, reiniciar e encerrar sessão só com confirmação e sessão pronta.",
  },
  {
    id: "clipboard",
    label: "Área de transferência",
    support: "permission-required",
    detail: "Nada é lido nem escrito. A permissão do navegador não é pedida neste corte.",
  },
  {
    id: "gyro",
    label: "Gyro",
    support: "browser-dependent",
    detail: "O botão não lê o sensor. Giroscópio no navegador depende de gesto, permissão e sessão.",
  },
  {
    id: "settings",
    label: "Ajustes locais",
    support: "available",
    detail: "Lista vertical com seções, interruptores e controles deslizantes. Não é grade de cartões.",
  },
  {
    id: "account",
    label: "Conta",
    support: "unconfigured",
    detail: "O backend de conta é dependência externa do dono. Não há formulário de login.",
  },
  {
    id: "billing",
    label: "Pro",
    support: "unconfigured",
    detail: "Três períodos, sem quantia e sem compra. O pagamento não está configurado.",
  },
  {
    id: "wake",
    label: "Manter a tela acesa",
    support: "browser-dependent",
    detail: "Usa Wake Lock só onde o navegador oferece a API e o usuário ligou o ajuste.",
  },
  {
    id: "orientation",
    label: "Trava de retrato",
    support: "browser-dependent",
    detail: "Pede a trava de orientação só onde a API existe. Se o navegador recusar, o ajuste explica.",
  },
  {
    id: "volume-keys",
    label: "Botões de volume do aparelho",
    support: "unavailable",
    detail: "O navegador não entrega esses botões ao site. O volume do PC fica no painel de mídia.",
  },
  {
    id: "install",
    label: "Instalação",
    support: "available",
    detail: "Manifesto e service worker guardam a casca do app. Não mantêm o controle em segundo plano.",
  },
];

export function bluetoothSupport(rows: readonly ParityRow[] = parityTable): Support | "universal" | "missing" {
  const row = rows.find((item) => item.id === "bluetooth");
  if (!row) return "missing";
  return row.support;
}
