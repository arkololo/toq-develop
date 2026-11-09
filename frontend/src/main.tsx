import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { ErrorBoundary } from './app/ErrorBoundary'
import { LanguageProvider } from './app/Language'
import { initializeAuthentication } from './auth/keycloak'
import './styles/global.css'

async function render() {
  const authenticated = await initializeAuthentication()
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <LanguageProvider><ErrorBoundary><App authenticated={authenticated} /></ErrorBoundary></LanguageProvider>
    </StrictMode>,
  )
}

void render().catch((error: unknown) => {
  const root = document.getElementById('root')!
  const message = document.createElement('p')
  message.setAttribute('role', 'alert')
  message.textContent = error instanceof Error ? error.message : 'Impossible de connecter le service d?authentification.'
  root.replaceChildren(message)
})
