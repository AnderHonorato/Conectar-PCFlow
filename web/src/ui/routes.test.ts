import { describe, expect, it } from "vitest";
import { parseScreen } from "./routes";

describe("rotas", () => {
  it("abre a conexão e reconhece as telas do controle", () => {
    expect(parseScreen("")).toBe("conectar");
    expect(parseScreen("#/controle")).toBe("controle");
    expect(parseScreen("#/ajustes")).toBe("ajustes");
    expect(parseScreen("#/paridade")).toBe("paridade");
    expect(parseScreen("#/outra")).toBe("conectar");
  });
});
