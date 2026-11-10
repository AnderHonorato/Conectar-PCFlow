import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { bluetoothSupport, parityTable, supportLabel } from "./parity";

const docPath = join(dirname(fileURLToPath(import.meta.url)), "../../../docs/web/parity.md");

describe("paridade do navegador", () => {
  it("não marca Bluetooth como universal", () => {
    expect(bluetoothSupport()).toBe("browser-dependent");
    expect(bluetoothSupport()).not.toBe("universal");
    expect(parityTable.some((row) => (row.support as string) === "universal")).toBe(false);
    expect(JSON.stringify(parityTable)).not.toContain("universal");
  });

  it("publica a mesma tabela no documento do Apêndice G", () => {
    const doc = readFileSync(docPath, "utf8");
    expect(doc).toContain("O que este navegador faz");
    expect(doc).toContain("dependente do navegador");
    expect(doc).not.toContain("| universal |");
    for (const row of parityTable) {
      expect(doc).toContain(row.id);
      expect(doc).toContain(supportLabel[row.support]);
    }
  });
});
