import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/rental/",
  plugins: [react()],
  build: { outDir: "../src/main/resources/static", emptyOutDir: true },
  server: { port: 5181, proxy: { "/rental/api": "http://localhost:3018" } },
});
