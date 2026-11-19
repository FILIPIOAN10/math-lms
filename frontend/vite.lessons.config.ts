// A second dev server for trying new code next to the usual one: port 5174, API from the backend on 8082
// (.claude/run-backend-lessons.cmd). Same app, same settings otherwise.
import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config'

export default mergeConfig(base, defineConfig({
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8082', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8082', changeOrigin: true },
    },
  },
}))
