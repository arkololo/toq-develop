# Phase 2 — identité et profil local

Cette phase reste entièrement locale, via Docker Compose.

## Fonctionnalités

- Le realm Keycloak `TOQ` est importé depuis `infra/keycloak/realm-import/TOQ-realm.json`.
- Le client web public `toq-web` utilise Authorization Code avec PKCE S256, les redirections limitées à `http://localhost:8080/*`.
- Le rôle realm `USER` est accordé par défaut aux nouveaux comptes.
- `user-service` valide la signature, l'émetteur et l'audience `toq-api`, puis protège les routes `/api/v1/users/**` par le rôle `USER`.
- Le frontend fournit la connexion, la déconnexion et la saisie du profil, des préférences et d'un objectif nutritionnel.

## Démarrage

Depuis la racine du dépôt :

```powershell
docker compose up -d --build
```

Ouvrir `http://localhost:8080`, choisir **Se connecter**, puis utiliser l'inscription Keycloak. Les données de profil sont créées lors du premier enregistrement.

## API protégée

- `GET` et `PUT /api/v1/users/me`
- `GET` et `PUT /api/v1/users/me/preferences`
- `GET` et `PUT /api/v1/users/me/nutrition-goals`

Les données restent dans la base `toq_user`; l'identifiant Keycloak `sub` est la seule référence d'identité persistée par le service.

### MODIFICATION A FAIRE SUR LA PHASE 2

<!-- - Après inscription, ne pas proposer l'option Fuseau horaire, et Langue, on va se limiter au français et à europe/paris. donc ne plus display ces options ci. seulement conserver le Nom affiché comme option
- Quand on clique sur 'enregistrer le profil', erreur CORS : 
Request URL
http://localhost:8080/api/v1/users/me
Request method
PUT
Status code
403 Forbidden
Remote address
127.0.0.1:8080
Referrer policy
strict-origin-when-cross-origin
cache-control
no-cache, no-store, max-age=0, must-revalidate
connection
keep-alive
content-length
0
content-security-policy
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' http://localhost:8081; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
date
Wed, 23 Sep 2026 12:25:12 GMT
expires
0
permissions-policy
camera=(), microphone=(), geolocation=()
pragma
no-cache
referrer-policy
strict-origin-when-cross-origin
server
nginx
www-authenticate
Bearer error="insufficient_scope", error_description="The request requires higher privileges than provided by the access token.", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
x-content-type-options
nosniff
x-content-type-options
nosniff
x-frame-options
DENY
x-frame-options
DENY
x-xss-protection
0
accept
*/*
accept-encoding
gzip, deflate, br, zstd
accept-language
fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7
authorization
Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI4YkxuWHdvZFdDOGsyVC1va2xucFU3M21Ed2M2aFA5eUI4MElGdzZxak1vIn0.eyJleHAiOjE3OTAxNjY2MDcsImlhdCI6MTc5MDE2NjMwNywianRpIjoib25ydGFjOjU2NTVhMGY0LTdiZWEtYjU3NS00ODk0LTNlMWUwODhlOTNhMCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MS9yZWFsbXMvVE9RIiwiYXVkIjoidG9xLWFwaSIsInR5cCI6IkJlYXJlciIsImF6cCI6InRvcS13ZWIiLCJzaWQiOiJqRFlVZzVaV3FCNkE5c2R1ZDlOeVYxT3ciLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiZGVmYXVsdC1yb2xlcy10b3EiLCJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIiwiVVNFUiJdfSwic2NvcGUiOiJvcGVuaWQgdG9xLWFwaS1hdWRpZW5jZSB0b3EtcmVhbG0tcm9sZXMifQ.BrLrPA1oiHqQpf8rzPhIECYOpEZU70pvsa3sJC7-NTKfacO4TzvbJs971SQad1tHk3SPvgCJPL2V5mw_M7KfH9TdCzEQWMMCpTyc9ofk7bJlg74KytN_UMj8wTvMfZCTf9hDz7LCmdJv4RCo-XQwf47BqenTSKs6_4MlvDmi-9GTJdGH1rm5UH_gxztflrznifY6Nl6NE9MQAUCOpRy7ziyxtzNSrm33iVxMEjnbo_S7CSncGOmr9iIS6bEhm5zHCW0OhYXNVI8wp4Qk8pDRkxYUp-pTpt6SkEiN-QK2VwrbpQTrrz6HtTufEnzkBtP8hXE1bwed8-M6O9cNxPgYDg
connection
keep-alive
content-length
66
content-type
application/json
host
localhost:8080
origin
http://localhost:8080
referer
http://localhost:8080/
sec-ch-ua
"Google Chrome";v="153", "Not_A Brand";v="8", "Chromium";v="153"
sec-ch-ua-mobile
?0
sec-ch-ua-platform
"Windows"
sec-fetch-dest
empty
sec-fetch-mode
cors
sec-fetch-site
same-origin
user-agent
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36 -->