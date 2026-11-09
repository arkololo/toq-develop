import { readFileSync, writeFileSync } from 'node:fs'

// One public origin drives both the browser bundle and its CSP. Never substitute
// Nginx variables ($host, $uri...) or secrets into this file.
const value = process.env.VITE_KEYCLOAK_URL || 'http://localhost:8081'
const url = new URL(value)
if (!['http:', 'https:'].includes(url.protocol) || url.origin !== value) {
  throw new Error('VITE_KEYCLOAK_URL must be an HTTP(S) origin without path or trailing slash')
}
const [input, output] = process.argv.slice(2)
writeFileSync(output, readFileSync(input, 'utf8').replaceAll('__KEYCLOAK_ORIGIN__', url.origin))
