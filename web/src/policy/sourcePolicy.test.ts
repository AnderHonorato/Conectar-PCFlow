import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    if (path.endsWith(".test.ts")) return [];
    if (/\.(ts|tsx|js|css)$/.test(path)) return [path];
    return [];
  });
}

describe("política do cliente web", () => {
  it("não registra teclado, clipboard nem marca de terceiros", () => {
    const files = sourceFiles(join(dirname(fileURLToPath(import.meta.url)), ".."));
    const source = files.map((path) => readFileSync(path, "utf8")).join("\n");
    expect(source).not.toMatch(/console\./);
    expect(source).not.toMatch(/navigator\.clipboard/);
    expect(source).not.toMatch(/clipboard\.(read|write)/);
    expect(source).not.toMatch(/deviceorientation/i);
    expect(source).not.toMatch(/Remote Mouse/);
    expect(source).not.toMatch(/DESKTOP-OSC/);
    expect(source).not.toMatch(/R\$/);
    expect(source).not.toMatch(/document\.addEventListener\(\s*["']keydown/);
    expect(source).not.toMatch(/window\.addEventListener\(\s*["']keydown/);
    expect(source).not.toMatch(/\beval\(/);
  });
});
