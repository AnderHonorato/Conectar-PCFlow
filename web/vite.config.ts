import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { pagesBase } from "./src/config.ts";

export default defineConfig({
  base: pagesBase,
  plugins: [react()],
  server: { host: "127.0.0.1" },
  preview: { host: "127.0.0.1" },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
