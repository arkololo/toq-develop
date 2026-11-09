# TOQ sur la VM Ubuntu / Proxmox

Adresse de la VM : `172.16.10.20`, accessible depuis le PC par Tailscale (route vers ce sous-réseau requise). Les ports publiés restent **8080** pour le frontend et **8081** pour Keycloak. Aucun service métier ni PostgreSQL n'est publié.

## Variables

À la racine, `sh scripts/setup-local.sh` prépare les secrets dans `.env` (ignoré par Git). Conserver les mots de passe existants si les volumes existent déjà. Ajouter ou modifier uniquement ces valeurs publiques, sans slash final :

```dotenv
TOQ_BIND_ADDRESS=172.16.10.20
TOQ_PUBLIC_FRONTEND_URL=https://172.16.10.20:8080
TOQ_PUBLIC_KEYCLOAK_URL=https://172.16.10.20:8081
```

`TOQ_BIND_ADDRESS` est une adresse présente sur la VM, pas sur le PC. Cette liaison n'applique pas elle-même une restriction Tailscale : le routage, les ACL Tailscale et le pare-feu doivent autoriser les PC concernés. Les valeurs par défaut restent `127.0.0.1`, `http://localhost:8080` et `http://localhost:8081` pour le développement local.

La même URL Keycloak alimente le hostname public, l'issuer attendu par les cinq services (`URL/realms/TOQ`), le frontend et sa CSP. Les clés JWT sont toujours téléchargées sur `http://keycloak:8080/realms/TOQ/protocol/openid-connect/certs`. Les appels interservices et les connexions PostgreSQL gardent les noms DNS Docker.

Le frontend utilise `VITE_KEYCLOAK_URL` **au build**, transmis automatiquement par Compose. Une modification d'URL nécessite une reconstruction de `frontend-edge`, puis la recréation des services concernés lors d'un déploiement ultérieur. Aucun secret ne doit être transmis via une variable `VITE_*`.

## HTTPS indispensable au login distant

**Le login ne peut pas fonctionner normalement sur `http://172.16.10.20:8080`.** Keycloak JS 26 utilise Web Crypto, disponible en contexte sécurisé : HTTPS avec certificat reconnu, ou localhost pour le développement. Tailscale chiffre le transport mais ne transforme pas une origine HTTP en contexte sécurisé. L'application affiche maintenant une erreur explicite au lieu de rester vide.

Le fichier **`compose.https.yaml`** ajoute Caddy comme terminaison TLS sur les mêmes ports. Il retire la publication HTTP de `frontend-edge` et `keycloak`, tout en conservant leurs noms et ports HTTP internes. Keycloak reçoit les en-têtes du proxy avec `KC_PROXY_HEADERS=xforwarded`. Le projet reste nommé `toq-local` : les données PostgreSQL existantes sont conservées.

Caddy signe les certificats de cette IP avec une autorité privée persistante. Chaque PC doit faire confiance à son certificat racine public. **Cliquer pour ignorer un avertissement de certificat ne remplace pas cette installation.** Aucun domaine ni certificat public n'est nécessaire pour cet usage privé. La configuration HTTP locale reste disponible avec `compose.yaml` et ses valeurs localhost.

Le proxy utilise par défaut `TOQ_BIND_ADDRESS` comme nom TLS de repli pour les clients IP sans SNI derrière Docker. Si la liaison utilise `0.0.0.0`, renseigner aussi `TOQ_TLS_SERVER_NAME=172.16.10.20` pour sélectionner le certificat de l'adresse publique réelle.

### Activer HTTPS sur la VM (à exécuter par l'opérateur)

Transférer d'abord le projet mis à jour, notamment `compose.https.yaml`, `infra/compose/compose.https.yml`, `infra/reverse-proxy/Caddyfile` et les modifications du frontend. Dans le `.env` existant, renseigner les trois valeurs HTTPS du début du guide. Conserver tous les mots de passe existants. Compose >= 2.24.4 est nécessaire.

```sh
python3 scripts/check-https-config.py
docker compose -f compose.https.yaml --parallel 1 up -d --build --wait --wait-timeout 900
docker compose -f compose.https.yaml ps
```

Employer `-f compose.https.yaml` pour toutes les opérations suivantes sur cette stack. Relancer la commande HTTP `docker compose up` rétablirait les ports HTTP et pourrait créer un conflit avec Caddy. Ne pas exécuter `down -v` : cela supprimerait les données et l'autorité privée.

### Installer le certificat sur le PC

Après démarrage, sur la VM :

```sh
mkdir -p .local
docker compose -f compose.https.yaml cp tls-edge:/data/caddy/pki/authorities/local/root.crt .local/toq-root.crt
```

Depuis PowerShell sur le PC, remplacer `UTILISATEUR` et `~/toq` selon l'emplacement réel :

```powershell
scp UTILISATEUR@172.16.10.20:~/toq/.local/toq-root.crt .\toq-root.crt
certutil -user -addstore Root .\toq-root.crt
```

Cette commande ajoute l'autorité au magasin de confiance de l'utilisateur Windows courant. N'importer que le certificat provenant de votre VM. Fermer complètement puis relancer le navigateur. Edge et Chrome utilisent le magasin Windows ; si Firefox ne le reconnaît pas, importer ce certificat dans ses paramètres, Confidentialité et sécurité → Certificats → Autorités. Répéter sur chaque PC de l'équipe.

Seul `root.crt` est transféré : **ne jamais exporter les clés privées** du volume `caddy-data`. Le répertoire `.local` est ignoré par Git. Préserver `caddy-data` lors des mises à jour ; une nouvelle autorité nécessiterait une nouvelle installation sur les PC.

Ouvrir maintenant **`https://172.16.10.20:8080`**. La console d'administration Keycloak est **`https://172.16.10.20:8081/admin/master/console/`**. Le schéma `https://` est obligatoire ; les mêmes ports ne servent plus HTTP. Mettre ensuite à jour le client existant selon la section suivante avant de tester le login. Les anciennes sessions HTTP doivent être fermées.

Référence : [Caddy — certificats internes et confiance des clients](https://caddyserver.com/docs/running).

Référence : [migration Keycloak — contexte sécurisé](https://www.keycloak.org/docs/latest/upgrading/index.html#_javascript_adapter).

## Realm neuf ou déjà existant

Le fichier d'import utilise `${TOQ_PUBLIC_FRONTEND_URL}`, résolu par Keycloak grâce à l'environnement du conteneur. Pour `toq-web` :

| Paramètre | Valeur |
|---|---|
| Valid redirect URIs | `<TOQ_PUBLIC_FRONTEND_URL>/*` |
| Valid post logout redirect URIs | `<TOQ_PUBLIC_FRONTEND_URL>/*` |
| Web origins | `<TOQ_PUBLIC_FRONTEND_URL>` (origine exacte, sans chemin) |
| PKCE | S256 |
| Client authentication | désactivée : client public |

**L'import au démarrage ignore un realm déjà existant.** Sur une VM déjà initialisée, mettre à jour ces trois URL dans la console d'administration du realm TOQ, client `toq-web`. Vérifier aussi qu'un ancien « Frontend URL » au niveau du realm ne force pas une autre adresse : le vider pour utiliser `KC_HOSTNAME`. Ne pas effacer les volumes ni réimporter tout le realm pour modifier des URL. Les sessions ouvertes avant le changement d'issuer devront être renouvelées.

Pour cette VM, les trois valeurs sont respectivement `https://172.16.10.20:8080/*`, `https://172.16.10.20:8080/*` et `https://172.16.10.20:8080`.

Référence : [import Keycloak et variables d'environnement](https://www.keycloak.org/server/importExport).

## API, CORS et CSP

Les appels du navigateur restent relatifs (`/api/v1/...`) : pas d'URL localhost ni de DNS Docker dans le navigateur. Nginx conserve les chemins et route users/assistant vers user-service, recipes/ingredients vers recipe-service, nutrition vers nutrition-service, plans/shopping-lists vers planner-service et social vers social-service. Les routes API inconnues renvoient 404 ; les chemins actuator/internal restent bloqués.

L'API est de même origine que le frontend : aucun CORS permissif n'est nécessaire dans Spring. Le CORS des endpoints OIDC est géré par la web origin exacte de `toq-web`. La CSP autorise `connect-src` et `frame-src` vers la seule origine Keycloak configurée ; les autres restrictions sont conservées.

## Vérifications sans déploiement

```sh
python3 scripts/check-https-config.py
docker compose -f compose.https.yaml config --quiet
cd frontend
npm ci
npm run build
npm test
npm run lint
```

Ne pas publier la sortie complète de `docker compose config` : elle contient les secrets interpolés. Les fichiers `.env` et les certificats privés restent exclus de Git.

## Tests depuis le PC Tailscale, après déploiement par l'opérateur

```sh
python scripts/check-public-endpoints.py --frontend https://172.16.10.20:8080 --keycloak https://172.16.10.20:8081 --ca-file toq-root.crt
```

Ce test en lecture seule vérifie les statuts, les routes API protégées, la CSP et l'issuer annoncé. La validation du certificat reste active. Ouvrir ensuite le frontend :

1. Vérifier `window.isSecureContext === true` et la présence de `window.crypto.subtle` dans la console.
2. Se connecter : la redirection doit viser l'URL publique Keycloak, puis revenir à l'URL publique frontend.
3. Dans Réseau, vérifier les réponses API authentifiées, l'absence de requêtes navigateur vers localhost/DNS Docker, et l'absence d'erreurs CSP/CORS.
4. Vérifier localement le JWT : `iss` égale exactement `<URL_KEYCLOAK>/realms/TOQ`, `aud` inclut `toq-api`. Ne pas copier le jeton dans un service externe.
5. Tester le renouvellement de session et la déconnexion avec retour au frontend.

Les scripts `smoke-test.sh` et `.ps1` existants restent les tests de la stack **locale**, avec leur contrôle volontaire des liaisons à 127.0.0.1.

## Développement local

La stack Compose sans surcharge conserve ses URL localhost. Pour Vite hors Docker, utiliser le port 8080 (client Keycloak local déjà autorisé), libérer ce port en déplaçant le port publié de `frontend-edge` vers 8082 dans un override local, puis lancer `npm run dev`. Vite relaie `/api` vers `http://localhost:8082` ; `TOQ_DEV_API_TARGET` permet de changer cette destination interne au serveur Vite. `VITE_KEYCLOAK_URL` dans `frontend/.env.local` permet de choisir un autre Keycloak. Conserver une web origin et une redirect URI qui correspondent exactement à l'origine Vite.
