import Keycloak from 'keycloak-js'

export const keycloak = new Keycloak({
  url: 'http://localhost:8081',
  realm: 'TOQ',
  clientId: 'toq-web',
})

export async function initializeAuthentication(): Promise<boolean> {
  return keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  })
}

export async function authenticatedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const refreshed = await keycloak.updateToken(30)
  if (!keycloak.token) {
    throw new Error('Votre session a expiré. Connectez-vous à nouveau.')
  }
  if (refreshed) {
    // The adapter keeps the refreshed token in memory; it is deliberately not persisted.
  }
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${keycloak.token}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return fetch(path, { ...init, headers })
}
