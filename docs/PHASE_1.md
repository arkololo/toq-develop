# Phase 1 — Bootstrap local

Date : 23 septembre 2026. Périmètre : première phase d’implémentation après validation de l’architecture, exclusivement sur la machine de développement.

## Objectif et décisions

Un lancement Docker Compose fournit une page React, Nginx, quatre services Spring Boot, Keycloak et PostgreSQL. Le socle est réellement exécutable ; la connexion applicative, les profils et la logique métier restent dans les phases suivantes.

| Composant | Version / configuration |
|---|---|
| Java | Cible de compilation et runtime conteneur Java 21 |
| Spring Boot | 4.0.8, dépendances gérées par le parent Boot |
| Maven | 3.9.12, wrapper fourni |
| React / React DOM | 19.3.0 |
| TypeScript / Vite | 5.9.3 / 8.3.0 |
| Node pour les builds Docker | 22.23.2 Alpine |
| Nginx | Image non privilégiée 1.30.5 Alpine |
| PostgreSQL | 17.11 Alpine, une base/rôle pour chaque service et Keycloak |
| Keycloak | 26.7.4, `start-dev`, administration locale |

Les images de base et d’infrastructure sont épinglées par digest. Les dépendances frontend sont verrouillées dans `frontend/package-lock.json`. Les images applicatives construites sont nommées `toq/<service>:local`, sans publication sur un registre.

Sept conteneurs sont actifs. Social reste réservé. Les sondes Spring écoutent sur le port management 8081 interne ; elles ne traversent pas Nginx. Les API restent fermées avec `401` avant la configuration JWT. Les services n’ont pas de mot de passe Spring généré ni de formulaire de login local.

Le [mode local](adr/0013-developpement-local.md) utilise HTTP et des secrets aléatoires dans `.env`, exclu de Git et du contexte de build. Les ports hôte sont `127.0.0.1:8080` et `127.0.0.1:8081`. Nginx et Keycloak sont reliés au réseau `edge` pour permettre ces publications ; les backends utilisent seulement les réseaux internes `backend` et `data`, PostgreSQL seulement `data`.

## Fichiers créés ou modifiés

| Ensemble | Fichiers et rôle |
|---|---|
| Monorepo | `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `.dockerignore`, `.gitattributes` |
| Backend | Pour chacun des quatre services : POM, Dockerfile, Application, SecurityConfiguration, application.yml, répertoire de migrations, BootstrapTest et README |
| Social | `services/social-service/README.md`, sans application active |
| Frontend | Configurations React/Vite/TypeScript/ESLint/Vitest, lockfile, landing, styles, copies du logo et de TOQi avec provenance, tests et Dockerfile |
| Infrastructure | `compose.yaml`, `infra/compose/compose.dev.yml`, `infra/postgres/init/001-databases.sql`, `infra/reverse-proxy/default.conf`, `.env.example` |
| Scripts | `setup-local.ps1/.sh`, `check-local.ps1/.sh`, `smoke-test.ps1/.sh`, `check-database-isolation.sql` |
| CI | `.gitlab-ci.yml`, définition de jobs de validation/tests uniquement |
| Documentation | README, ce bilan, STATUS, registre ADR, ADR-013, annotation de validation et périmètre local dans l’architecture |

Le dépôt Git est initialisé sur `develop`, sans commit ni remote configuré. Les documents sous `base/` sont conservés. Les fichiers `.github/modernize/` et le lockfile npm vide à la racine, présents à la reprise, n’ont pas été modifiés ; le frontend utilise son propre lockfile.

## Implémentation et commandes

Depuis la racine sous PowerShell :

```powershell
.\scripts\setup-local.ps1
docker compose --parallel 2 up --build --wait --wait-timeout 240
.\scripts\smoke-test.ps1
```

Résultat attendu : sept conteneurs `healthy`, TOQ sur `http://localhost:8080`, Keycloak sur `http://localhost:8081/admin/`. Le compte admin est `toq-admin`, son secret se consulte localement dans `.env`. Le script setup préserve les secrets existants. Les données restent présentes après `docker compose down` sans `--volumes`.

Avec Java et Node installés pour les tests sur l’hôte : `.\scripts\check-local.ps1`. La commande `docker compose config --quiet` vérifie la configuration sans afficher les secrets interpolés. Les variantes shell sont documentées dans le [README](../README.md).

## Tests

- **Backend : 12 tests réussis**, trois par service : vrai démarrage Spring, santé sur port séparé, routes refusées, absence de session et disponibilité dégradée lors d’une perte de base. H2 est utilisé uniquement dans les tests ; les conteneurs emploient PostgreSQL.
- **Frontend : lint, deux tests et build réussis.** Navigation vers les sections existantes et analyse sémantique d’accessibilité.
- **Docker :** construction des cinq images applicatives, démarrage Compose des sept conteneurs et sondes de disponibilité vérifiés.
- **Smoke tests :** page et sonde Nginx, découverte OIDC du realm master, refus des API, blocage des chemins de gestion, absence de ports internes publiés et isolation des cinq rôles PostgreSQL.
- **Navigateur réel :** Chrome headless, logo/mascotte chargés, navigation par ancre et lien d’évitement au clavier. Aucun débordement horizontal à 390 et 320 px, aucune erreur JavaScript détectée. Analyse axe-core desktop sans violation WCAG A/AA détectée, contraste inclus ; ce résultat n’est pas une certification d’accessibilité.
- **Persistance :** redémarrage de PostgreSQL et Keycloak, conservation de l’identifiant du realm master et retour des services à l’état sain.

Les vérifications CI ont été exécutées localement ; aucun pipeline GitLab distant n’a été lancé. Les scripts PowerShell constituent le chemin testé sur cette machine. Les variantes shell restent à exécuter sur Linux/macOS.

## Limites et prochaines étapes

La page publique présente le projet ; recettes, planning et nutrition ne sont pas encore des écrans métier. Aucune intégration OpenFoodFacts ne s’exécute. Le realm TOQ, le client PKCE, les rôles et le service User métier appartiennent à la Phase 2.

Flyway peut afficher « No migrations found » : aucune table métier n’est attendue en Phase 1. Le volume PostgreSQL contient les tables gérées par Keycloak et les tables de suivi Flyway. Le profil Keycloak `start-dev` peut annoncer des avertissements de développement ; il n’est pas destiné à la production.

Les limites mémoire Compose totalisent environ 4,625 Gio ; les builds et Docker Desktop demandent une marge supplémentaire. Les mesures au repos ne remplacent pas un test de charge. La persistance locale ne remplace pas une sauvegarde.

Oracle, staging distant, certificats publics, sauvegardes hors hôte et Runner externalisé sont différés. Le fichier CI n’exige aucun compte ou service distant pour utiliser l’application localement.

## Definition of Done

- [x] Architecture validée, restriction locale documentée.
- [x] Monorepo et quatre squelettes backend indépendants.
- [x] Frontend React et assets de la charte.
- [x] Dockerfiles multi-stage, processus applicatifs non-root, Compose et healthchecks.
- [x] PostgreSQL et Keycloak persistants, accès locaux et bases isolées.
- [x] Commandes et tests reproductibles documentés.
- [x] Définition CI minimale, sans activation distante.
- [x] Vérifications locales réalisées et état du projet mis à jour.

La Phase 2 n’est pas commencée. La validation de cette étape précède son lancement.
