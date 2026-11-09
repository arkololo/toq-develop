import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8080,
    strictPort: true,
    proxy: { '/api': { target: process.env.TOQ_DEV_API_TARGET || 'http://localhost:8082', changeOrigin: true } },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './tests/setup.ts',
    include: ['tests/**/*.test.tsx'],
  },
})
