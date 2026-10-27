# TOQ — Plan technique d’implémentation pour GPT-6 Astra

> **Objectif de ce document** : servir de plan d’exécution à GPT-6 Astra pour réaliser la partie technique de TOQ de manière progressive, testable et conforme aux consignes du projet.
>
> **Règle principale** : ne pas générer tout le projet d’un coup. Travailler phase par phase. Chaque phase doit laisser le dépôt dans un état exécutable, documenté et testé.

---

# 1. Contexte produit

TOQ est un **réseau social d’organisation et de partage de repas**.

Fonctions prévues :

- création de recettes avec ingrédients, quantités et nombre de portions ;
- calcul et suivi des calories et macronutriments ;
- organisation des repas par semaine ;
- génération automatique d’une liste de courses ;
- import/enrichissement de données via une API externe ;
- allergènes, labels et préférences alimentaires ;
- partage de recettes, likes, commentaires et favoris ;
- intégration Strava éventuelle et non prioritaire.

Le frontend doit rester suffisamment simple pour concentrer l’effort sur :

1. la logique métier backend ;
2. l’architecture microservices ;
3. le DevOps / CI/CD ;
4. la sécurité ;
5. la conteneurisation ;
6. l’exploitation de l’application.

---

# 2. Contraintes pédagogiques impératives

Le projet doit intégrer :

- un frontend ;
- au moins deux services backend distincts ;
- une ou plusieurs bases de données ;
- au moins une API externe appelée depuis le backend ;
- une logique métier significative ;
- Docker / Docker Compose ;
- une chaîne CI/CD complète ;
- des **GitLab Runners auto-hébergés** reliés à GitLab.com ;
- un service d’authentification partagé, de préférence **Keycloak** ;
- une démarche DevSecOps ;
- chiffrement en transit ;
- chiffrement au repos ;
- sauvegardes ;
- contrôle des accès ;
- analyses de sécurité et de dépendances.

Technologies à privilégier car étudiées dans la formation :

- React ;
- Java ;
- Spring Boot ;
- Git ;
- Docker ;
- Docker Compose.

Tout choix important hors de ce socle doit être signalé comme **décision à valider avec l’équipe enseignante**.

---

# 3. Mode de travail imposé à Astra

Pour chaque phase :

1. analyser l’existant avant toute modification ;
2. expliquer l’objectif ;
3. annoncer les décisions techniques ;
4. lister les fichiers créés/modifiés ;
5. fournir une implémentation réelle, pas du pseudo-code ;
6. donner les commandes de lancement ;
7. fournir les tests ;
8. indiquer le résultat attendu ;
9. terminer par une **Definition of Done** ;
10. mettre à jour `docs/STATUS.md`.

Ne jamais :

- réécrire silencieusement l’architecture ;
- ajouter une technologie lourde sans justification ;
- stocker de secrets dans Git ;
- exposer les bases de données directement sur Internet ;
- lancer plusieurs phases en parallèle sans validation de la précédente.

---

# 4. Priorités

## P0 — MVP

- authentification Keycloak ;
- profil utilisateur ;
- préférences alimentaires / allergies ;
- objectifs nutritionnels ;
- CRUD recettes ;
- ingrédients et portions ;
- calcul des macros ;
- planning hebdomadaire ;
- liste de courses automatique ;
- appel d’une API externe ;
- Docker ;
- CI/CD ;
- sécurité de base.

## P1 — fonctions différenciantes

- réseau social ;
- likes ;
- commentaires ;
- favoris ;
- partage ;
- labels ;
- variantes de recette.

## P2 — extensions

- ranking des recettes ;
- intégration Strava éventuelle et non prioritaire.

---

# 5. Architecture cible

```text
Utilisateur
    |
    v
Frontend React
    |
 HTTPS / JSON
    |
    v
Reverse Proxy / API Gateway
    |
    +-------------------> Keycloak
    |
    +----> User Service
    |
    +----> Recipe Service
    |
    +----> Nutrition Service
    |
    +----> Planner Service
    |
    +----> Social Service
```

Architecture initiale : **REST synchrone** entre services. Le découpage doit rester modulaire afin de permettre l’ajout ultérieur de nouveaux domaines métier sans refonte globale.

RabbitMQ ou un bus d’événements ne doit être ajouté que lorsqu’un besoin concret apparaît.

---

# 6. Découpage des microservices

## 6.1 User Service

**Stack : Java + Spring Boot**

Responsabilités :

- profil TOQ ;
- liaison avec le `sub` Keycloak ;
- préférences alimentaires ;
- allergies ;
- ingrédients exclus ;
- objectifs calorie/macros ;
- paramètres utilisateur.

Keycloak reste responsable des identités, mots de passe, tokens et rôles.

Entités principales :

```text
UserProfile
DietaryPreference
Allergy
NutritionGoal
ExcludedIngredient
```

---

## 6.2 Recipe Service

**Stack : Java + Spring Boot**

Responsabilités :

- CRUD recettes ;
- ingrédients ;
- quantités ;
- unités ;
- portions ;
- étapes ;
- tags ;
- labels ;
- allergènes ;
- images ;
- import ;
- variantes parent/enfant.

Entités :

```text
Recipe
RecipeIngredient
IngredientReference
RecipeStep
Label
Allergen
RecipeVariation
```

---

## 6.3 Nutrition Service

**Stack : Java + Spring Boot**

Responsabilités :

- récupération des données nutritionnelles ;
- normalisation des aliments ;
- calories ;
- protéines ;
- glucides ;
- lipides ;
- fibres/sucres/sel si disponibles ;
- calcul par recette ;
- calcul par portion ;
- comparaison aux objectifs utilisateur ;
- règles déterministes de labels.

Exemple :

```text
valeur réelle = quantité en grammes / 100 × valeur pour 100 g
macro recette = somme des macros de chaque ingrédient
macro portion = macro recette / nombre de portions
```

---

## 6.4 Planner Service

**Stack : Java + Spring Boot**

Responsabilités :

- planning hebdomadaire ;
- repas/jour ;
- type de repas ;
- recette associée ;
- nombre de portions ;
- génération de la liste de courses.

Entités :

```text
WeekPlan
PlannedMeal
MealSlot
ShoppingList
ShoppingItem
```

La fonction Shopping List reste dans Planner Service au début afin de limiter le nombre de microservices.

---

## 6.5 Social Service

**Stack : Java + Spring Boot**

Responsabilités :

- publication de recette ;
- feed ;
- likes ;
- commentaires ;
- favoris ;
- partages.

Entités :

```text
Post
Like
Comment
Favorite
Share
```

Ne stocker que les identifiants des recettes/utilisateurs appartenant aux autres domaines.

---

# 7. API externe

Intégrer **OpenFoodFacts** pour les informations disponibles concernant :

- aliments ;
- nutriments ;
- allergènes ;
- labels ;
- produits.

Créer une abstraction :

```java
public interface FoodDataProvider {
    FoodData search(...);
}
```

Implémentation :

```text
OpenFoodFactsProvider
```

Pour une future API de recettes, créer :

```text
RecipeProvider
```

sans lier directement le domaine métier à un fournisseur précis.

---

# 8. Modèle de données / bases

Utiliser PostgreSQL.

Pour économiser les ressources, utiliser un serveur PostgreSQL unique avec isolation logique :

```text
toq_user
toq_recipe
toq_nutrition
toq_planner
toq_social
keycloak
```

Règle absolue :

> Un microservice ne lit jamais directement les tables d’un autre microservice.

Les échanges passent par API.

Utiliser des migrations versionnées, par exemple Flyway.

Identifiants métier : UUID.

---

# 9. API REST

Toutes les APIs doivent être versionnées :

```text
/api/v1/...
```

Exemples :

```text
GET    /api/v1/users/me
PUT    /api/v1/users/me

GET    /api/v1/recipes
POST   /api/v1/recipes
GET    /api/v1/recipes/{id}
PUT    /api/v1/recipes/{id}
DELETE /api/v1/recipes/{id}

GET    /api/v1/plans/{week}
POST   /api/v1/plans/{week}/meals
POST   /api/v1/plans/{week}/shopping-list

GET    /api/v1/social/feed
POST   /api/v1/posts
POST   /api/v1/posts/{id}/likes
POST   /api/v1/posts/{id}/comments

```

Utiliser :

- DTO d’entrée/sortie ;
- Bean Validation ;
- pagination ;
- erreurs standardisées ;
- OpenAPI / Swagger.

---

# 10. Keycloak et sécurité d’accès

Realm :

```text
TOQ
```

Client frontend :

```text
toq-web
```

Rôles :

```text
USER
MODERATOR
ADMIN
```

Frontend :

```text
OIDC Authorization Code Flow + PKCE
```

Backends :

```text
Spring Security OAuth2 Resource Server
```

Ne jamais accepter un `userId` du navigateur comme identité de confiance.

L’utilisateur courant doit être déterminé depuis le JWT.

---

# 11. Frontend

**Stack : React**

Pages principales :

```text
/
/login
/dashboard
/think
/planning
/planning/shopping-list
/recipes
/recipes/new
/recipes/{id}
/social
/profile
/settings
```

Navbar connectée :

```text
Accueil
Think
Planning
Recettes
Partager
Profil / Paramètres
```

Le frontend doit prioritairement permettre de démontrer la logique backend.

---

# 12. Logique métier essentielle

## 12.1 Liste de courses

Pipeline :

```text
planning semaine
-> recettes
-> portions
-> ingrédients
-> conversion des unités
-> normalisation
-> regroupement
-> somme des quantités
-> catégorisation
-> ShoppingList
```

Exemple :

```text
Recette A : tomate 300 g
Recette B : tomate 500 g
Résultat  : tomate 800 g
```

Ne pas sommer naïvement des unités incompatibles.

---

## 12.2 Nutrition

Créer un composant métier testable :

```text
NutritionCalculator
```

Il gère :

- conversions ;
- calculs par ingrédient ;
- agrégation ;
- portions ;
- valeurs manquantes ;
- arrondis.

---

## 12.3 Labels

Les labels factuels doivent être basés sur des règles métier explicites et testables.

Exemples :

```text
VEGAN
VEGETARIAN
HIGH_PROTEIN
LOW_CARB
```

Documenter la règle exacte associée à chaque label.

---

# 13. Infrastructure

Le schéma fourni prévoit :

```text
Internet
   |
Firewall / Reverse Proxy
   |
   +---- Serveur APP
```

## Serveur APP

Conteneurs possibles :

```text
reverse-proxy
frontend
keycloak
postgres
user-service
recipe-service
nutrition-service
planner-service
social-service
```

# 14. Réseau

N’exposer publiquement que :

```text
80/tcp
443/tcp
```

Restreindre SSH.

Ne pas exposer publiquement :

```text
5432 PostgreSQL
ports internes Spring Boot
```

Réseaux Docker proposés :

```text
edge
backend
data
```

---

# 15. Docker / Compose

Prévoir :

```text
compose.dev.yml
compose.prod.yml
```

Chaque composant développé par l’équipe doit avoir son Dockerfile.

Utiliser :

- builds multi-stage ;
- utilisateur non-root lorsque possible ;
- healthchecks ;
- variables d’environnement ;
- volumes persistants uniquement lorsque nécessaire.

---

# 16. Arborescence recommandée

```text
toq/
├── frontend/
├── services/
│   ├── user-service/
│   ├── recipe-service/
│   ├── nutrition-service/
│   ├── planner-service/
│   └── social-service/
├── infra/
│   ├── compose/
│   ├── reverse-proxy/
│   ├── keycloak/
│   ├── postgres/
│   └── monitoring/
├── scripts/
├── docs/
│   ├── architecture/
│   └── adr/
├── .gitlab-ci.yml
├── .env.example
├── Makefile
└── README.md
```

Utiliser un monorepo pour simplifier la coordination du projet étudiant.

---

# 17. Git

Flux proposé :

```text
feature/*
    |
    v
develop
    |
    v
main
```

Utiliser des Merge Requests.

Protéger `develop` et `main`.

Ne jamais commiter :

```text
.env
*.pem
*.key
secrets/
```

---

# 18. CI/CD GitLab

Le pipeline doit tourner sur un **GitLab Runner auto-hébergé**.

Pipeline cible :

```text
commit
-> validate
-> unit tests
-> integration tests
-> SAST
-> dependency scan
-> build containers
-> scan containers
-> push registry
-> deploy staging
-> smoke tests
-> deploy production
```

Stages proposés :

```yaml
stages:
  - validate
  - test
  - security
  - build
  - package
  - deploy
  - verify
```

Règles :

```text
Merge Request : lint + tests + sécurité + build

develop       : build + push + staging + smoke tests

main          : build versionné + production + healthcheck
```

Ne pas déployer uniquement avec le tag `latest`.

Tags recommandés :

```text
<service>:<commit-sha>
<service>:<version>
```

Prévoir un mécanisme de rollback vers l’image précédente.

---

# 19. Tests

## Backend

- unitaires ;
- services métier ;
- repositories ;
- contrôleurs ;
- intégration ;
- permissions ;
- API externe ;

À tester en priorité :

- calcul nutritionnel ;
- conversion d’unités ;
- calcul des portions ;
- fusion de la liste de courses ;
- autorisations ;
- défaillance d’OpenFoodFacts ;

## Frontend

Tests E2E prioritaires :

```text
login
-> création recette
-> ajout au planning
-> génération liste de courses
```

---

# 20. DevSecOps

Prévoir dans la CI :

- SAST : SonarQube ou outil validé ;
- audit dépendances Java ;
- `npm audit` ;
- scan images : Trivy ;
- scan secrets : Gitleaks ;
- DAST en staging : OWASP ZAP si réalisable.

---

# 21. Sécurité applicative

Mettre en place :

- TLS ;
- HSTS ;
- headers de sécurité ;
- CSP ;
- CORS restrictif ;
- validation stricte des entrées ;
- limite taille upload ;
- validation MIME ;
- rate limiting sur endpoints sensibles ;
- aucun token dans les logs ;
- aucun secret dans les images Docker ;
- requêtes SQL paramétrées / ORM ;
- erreurs centralisées sans stack trace publique ;
- principe du moindre privilège.

---

# 22. Secrets

Développement :

```text
.env local non commité
```

CI/CD :

```text
GitLab CI/CD Protected Variables
```

Production :

secrets uniquement présents sur la machine ou via un mécanisme sécurisé adapté.

---

# 23. Confidentialité / RGPD

Minimiser :

- email ;
- pseudonyme ;
- préférences alimentaires ;
- historique de repas ;
- objectifs nutritionnels.

Prévoir :

- suppression compte ;
- suppression/anonymisation des données ;
- export utilisateur futur ;
- politique de conservation ;
- absence de secrets/tokens dans les logs.

---

# 24. Intégrité / disponibilité

Intégrité :

- contraintes SQL ;
- transactions ;
- migrations ;
- validation métier ;
- sauvegardes ;
- versionnement des images.

Disponibilité :

- Docker restart policies ;
- healthchecks ;
- monitoring ;
- procédure de redéploiement ;
- rollback ;
- restauration de sauvegarde.

La vraie haute disponibilité peut rester une **évolution documentée** si l’infrastructure du projet étudiant ne permet pas de la démontrer.

---

# 25. Sauvegardes

Sauvegarder :

```text
PostgreSQL
configuration Keycloak
médias utilisateurs
configuration infrastructure
```

Créer :

```text
scripts/backup.sh
scripts/restore.sh
docs/BACKUP_RESTORE.md
```

Prévoir une restauration testée.

---

# 26. Observabilité

Tous les services Spring Boot doivent au minimum exposer :

```text
/actuator/health
```

Ajouter progressivement :

- logs structurés ;
- trace/correlation ID ;
- métriques ;
- monitoring.

Stack possible si les ressources le permettent :

```text
Prometheus
Grafana
Loki
```

Ne pas rendre cette stack obligatoire au fonctionnement métier de TOQ.

---

# 27. Résilience

Une panne de :

```text
OpenFoodFacts
API recette
Strava
```

ne doit pas provoquer une panne globale.

Prévoir :

- timeouts ;
- erreurs propres ;
- retries limités lorsque pertinents ;
- cache ;
- circuit breaker si justifié plus tard.

---

# 28. Phases d’implémentation

## Phase 0 — Architecture

Produire :

- C4 Context ;
- C4 Container ;
- Deployment Diagram ;
- responsabilités des services ;
- modèle de données initial ;
- contrats REST principaux ;
- ADR initiaux ;
- liste des décisions à valider.

**DoD** : architecture validée avant développement métier.

---

## Phase 1 — Bootstrap

Créer :

- monorepo ;
- Spring Boot services ;
- React ;
- Dockerfiles ;
- Compose local ;
- README ;
- CI minimale.

**DoD** : `docker compose up` lance l’environnement de développement.

---

## Phase 2 — Keycloak + User Service

Implémenter :

- realm ;
- client ;
- rôles ;
- login ;
- JWT ;
- profil ;
- objectifs ;
- préférences ;
- allergies.

**DoD** : utilisateur authentifié capable de lire/modifier son profil via API protégée.

---

## Phase 3 — Recipe Service

Implémenter :

- modèle ;
- CRUD ;
- ingrédients ;
- portions ;
- étapes ;
- recherche ;
- filtres ;
- ownership.

**DoD** : CRUD complet sécurisé.

---

## Phase 4 — Nutrition Service + OpenFoodFacts

Implémenter :

- client OpenFoodFacts ;
- normalisation ;
- cache ;
- calcul calories/macros ;
- allergènes ;
- labels possibles.

**DoD** : une recette possède des valeurs nutritionnelles calculées et testées.

---

## Phase 5 — Planning + liste de courses

Implémenter :

- planning semaine ;
- ajout de repas ;
- portions ;
- génération de liste ;
- normalisation unités ;
- agrégation ingrédients.

**DoD** : une semaine de repas produit une liste de courses consolidée.

---

## Phase 6 — Social

Implémenter :

- publication ;
- feed ;
- likes ;
- commentaires ;
- favoris ;
- pagination.

**DoD** : deux utilisateurs peuvent interagir autour d’une recette publiée.

---

## Phase 7 — CI/CD complet

Finaliser :

- runner auto-hébergé ;
- tests ;
- scans ;
- builds ;
- registry ;
- staging ;
- production ;
- rollback ;
- smoke tests.

**DoD** : un merge déclenche automatiquement test -> scan -> build -> image -> déploiement -> vérification.

---

## Phase 8 — Hardening

- revue OWASP ;
- vulnérabilités ;
- backups ;
- restauration ;
- logs ;
- monitoring ;
- réseaux ;
- Keycloak ;
- secrets ;
- RGPD.

---

## Phase 9 — Documentation finale

Produire :

- architecture ;
- procédures ;
- sécurité ;
- CI/CD ;
- restauration ;
- limites ;
- améliorations futures ;
- justification des choix.

---

# 29. Matrice de conformité

| Exigence | Réponse TOQ |
|---|---|
| Frontend | React |
| ≥ 2 services backend | User, Recipe, Nutrition, Planner, Social |
| Base de données | PostgreSQL |
| API externe | OpenFoodFacts + provider recette futur |
| Logique métier | nutrition, portions, planning, génération de liste de courses, réseau social |
| Conteneurisation | Docker |
| Déploiement | Docker Compose |
| CI/CD | GitLab CI |
| Runner | GitLab Runner auto-hébergé |
| Auth partagée | Keycloak |
| Token | OIDC / OAuth2 / JWT |
| TLS | Reverse Proxy |
| Chiffrement au repos | disque/volumes + backups |
| SAST | SonarQube ou équivalent validé |
| Audit dépendances | Maven + npm |
| Scan conteneur | Trivy |
| Scan secrets | Gitleaks |
| Disponibilité | healthchecks + restart + restore |
| Documentation sécurité | `docs/SECURITY.md` |

---

# 30. Critère de démonstration globale

Le scénario suivant doit fonctionner :

```text
1. L’utilisateur ouvre TOQ.
2. Il se connecte avec Keycloak.
3. Il renseigne ses préférences et objectifs.
4. Il crée ou importe une recette.
5. TOQ calcule les macros.
6. Il ajoute la recette au planning.
7. Il planifie plusieurs repas.
8. TOQ génère la liste de courses.
9. Il publie une recette.
10. Un second utilisateur peut liker et commenter la recette.
```

Et côté exploitation :

```text
11. Les tests CI passent.
12. Les scans de sécurité passent.
13. Les images Docker sont produites automatiquement.
14. Le staging est déployé automatiquement.
15. Le déploiement production est reproductible.
16. Une sauvegarde et une restauration sont démontrables.
```

---

# 31. Première tâche à exécuter par Astra

**Commencer uniquement par la Phase 0.**

Produire dans cet ordre :

1. synthèse de l’architecture ;
2. liste définitive des services et responsabilités ;
3. C4 Context en PlantUML ;
4. C4 Container en PlantUML ;
5. Deployment Diagram en PlantUML ;
6. arborescence du monorepo ;
7. ADR initiaux ;
8. contrats REST principaux ;
9. modèle de données initial par service ;
10. décisions nécessitant validation humaine.

**Ne pas commencer l’implémentation métier avant validation de cette phase.**

---

# 32. Format attendu des réponses d’Astra

```markdown
# Phase X — Nom

## Objectif

## Décisions

## Fichiers créés / modifiés

## Implémentation

## Commandes à exécuter

## Tests

## Résultat attendu

## Risques / points de vigilance

## Definition of Done

## Mise à jour STATUS.md
```

Lorsqu’il existe plusieurs solutions, comparer brièvement puis recommander celle qui respecte le mieux :

1. les consignes pédagogiques ;
2. la simplicité ;
3. la sécurité ;
4. les ressources disponibles ;
5. la maintenabilité.
