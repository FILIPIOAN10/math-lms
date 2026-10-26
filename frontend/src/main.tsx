import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import 'katex/dist/katex.min.css'
import App from './App.tsx'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { captureInstallPrompt, registerServiceWorker } from '@/lib/pwa'
import { initTheme } from '@/lib/theme'

// E11: before React draws anything - the install prompt can fire that early.
captureInstallPrompt()
registerServiceWorker()
initTheme()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </StrictMode>,
)
