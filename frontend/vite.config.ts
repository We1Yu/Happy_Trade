import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The proxy target differs between local development (localhost) and Docker Compose (the
// `backend` service name), so it comes from the environment.
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
});
