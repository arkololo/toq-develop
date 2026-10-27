# Sécurité et exploitation locale

TOQ est exposé uniquement sur `127.0.0.1:8080` (frontend/API) et `127.0.0.1:8081` (Keycloak). Les services Spring et PostgreSQL ne publient aucun port hôte. Les services User, Recipe et Nutrition ont une sortie réseau Docker dédiée pour consulter Gemini, TheMealDB et Open Food Facts ; les autres services métier restent sur des réseaux internes. Nginx refuse les chemins d'administration et n'inscrit ni jetons ni paramètres dans ses journaux d'accès. Chaque service utilise une base et un rôle PostgreSQL distincts. Les conteneurs applicatifs utilisent un système de fichiers en lecture seule, un utilisateur non privilégié et une limite de ressources.

Les sessions passent par Keycloak avec OIDC et PKCE. Les services valident la signature, l'émetteur, la durée et l'audience `toq-api` du JWT. Les requêtes métier utilisent le `sub` du jeton pour limiter les données au propriétaire ; les publications sont volontairement visibles par tous les comptes connectés. Aucun secret n'est stocké dans Git. `scripts/setup-local.ps1` ou `.sh` génère `.env` localement.

La configuration locale utilise HTTP et Keycloak `start-dev`, conformément à la décision de ne pas déployer d'infrastructure externe pour le moment. Ne pas exposer ces ports à Internet. Aucun certificat public ni sauvegarde hors hôte n'est mis en place.

Les photos des recettes restent privées au propriétaire ; les photos des publications sont visibles aux comptes connectés. Elles sont stockées dans les bases respectives et incluses dans les sauvegardes PostgreSQL. Le serveur vérifie la signature PNG/JPEG et limite chaque fichier à 3 Mo et 3000 × 3000 pixels ; Nginx limite la requête à 4 Mo. Les images distantes du catalogue ne sont affichées que depuis `themealdb.com` en HTTPS. Les termes de recherche sont convertis localement en mots-clés anglais. Lorsque l'utilisateur ouvre un modèle en français ou demande la traduction d'une recette, le contenu de cette recette est transmis à Gemini depuis le User Service.

## Assistant Gemini

L'assistant utilise la Gemini API depuis le User Service. La clé est injectée depuis `.env`, ignoré par Git et protégé par le script de configuration locale ; elle n'est jamais exposée au navigateur ni renvoyée par l'API. Le serveur valide le JWT et limite les appels à 20 par heure et par utilisateur. Création d'une ou plusieurs recettes, traduction et modification d'une recette existante, ajout, modification ou retrait d'un repas et objectif nutritionnel exigent une confirmation explicite. Les propositions sont liées au `sub` Keycloak, expirent après dix minutes et sont à usage unique. Les mises à jour de recette vérifient la version au moment de confirmer ; le Recipe Service conserve l'image existante. Le contenu des échanges, des recettes à traduire et le contexte utile du compte partent vers Google ; ils ne sont pas conservés par le backend TOQ. Ne pas envoyer d'information médicale confidentielle dans le chat.

## Sauvegarde locale

Avec la pile démarrée, exécuter `./scripts/backup-local.ps1` dans PowerShell. Le script produit six archives PostgreSQL au format personnalisé sous `.local/backups/<horodatage>/` ; ce dossier est ignoré par Git. Ces fichiers contiennent des données personnelles : limiter leur accès et leur durée de conservation. L'opération ne sauvegarde pas les images Docker ni `.env` ; conserver ce fichier local pour pouvoir redémarrer les rôles existants.

Pour vérifier une archive sans restaurer :

```powershell
docker compose cp .local/backups/<horodatage>/toq_recipe.dump postgres:/tmp/toq-restore.dump
docker compose exec -T postgres pg_restore -l /tmp/toq-restore.dump
```

Pour restaurer, arrêter d'abord les services métier, conserver une seconde copie des données courantes puis utiliser `pg_restore --clean --if-exists --no-owner` dans **la base correspondante**. Une restauration écrase les objets présents ; elle doit être préparée avec le nom exact de la base et de l'archive, puis vérifiée avec `docker compose ps` et `scripts/smoke-test.ps1`. Les migrations Flyway s'exécutent au redémarrage.

## Limites actuelles

La revue OWASP complète, un scan SAST/dépendances/containers automatisé, la rotation des secrets, l'export chiffré hors hôte et la supervision centralisée restent à réaliser avant tout usage hors poste local. Open Food Facts fournit des données collaboratives ; TOQ signale les valeurs absentes, mais ne peut pas garantir l'exactitude médicale des allergènes.
