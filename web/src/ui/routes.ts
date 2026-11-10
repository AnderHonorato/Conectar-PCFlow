export type ScreenId = "conectar" | "controle" | "ajustes" | "paridade" | "ajuda" | "conta" | "pro";

export function parseScreen(hash: string): ScreenId {
  const path = (hash.replace(/^#/, "") || "/conectar").split("?")[0];
  switch (path) {
    case "/controle":
      return "controle";
    case "/ajustes":
      return "ajustes";
    case "/paridade":
      return "paridade";
    case "/ajuda":
      return "ajuda";
    case "/conta":
      return "conta";
    case "/pro":
      return "pro";
    default:
      return "conectar";
  }
}

export const screenTitle: Record<ScreenId, string> = {
  conectar: "PCFlow — Conectar",
  controle: "PCFlow — Controle",
  ajustes: "PCFlow — Configurações",
  paridade: "PCFlow — O que este navegador faz",
  ajuda: "PCFlow — Ajuda",
  conta: "PCFlow — Conta",
  pro: "PCFlow — Pro",
};
