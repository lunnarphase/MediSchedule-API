import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, "..", "BACKEND_PORT");
  return {
    plugins: [react()],
    server: {
      proxy: { "/api": `http://127.0.0.1:${env.BACKEND_PORT || 8081}` },
    },
  };
});
