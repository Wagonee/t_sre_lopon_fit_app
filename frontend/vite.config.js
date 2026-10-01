import { defineConfig } from 'vite';

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  server: { port: 5173, proxy: { '/api': backend } },
  preview: { port: 4173, proxy: { '/api': backend } },
  build: { sourcemap: false, chunkSizeWarningLimit: 600 },
});
