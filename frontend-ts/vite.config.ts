import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // Beim Entwickeln gehen alle Requests an /api an das lokal gestartete Backend
    proxy: {
      '/api': 'http://localhost:6969',
    },
  },
})
