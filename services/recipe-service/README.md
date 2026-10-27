# Recipe Service

Squelette de phase 1 : Spring Boot 4.0.8, Java 21, JDBC PostgreSQL et Flyway.
Le domaine métier et les contrats REST seront implémentés dans leur phase dédiée.

- API interne : port `8080`, accès refusé par défaut.
- Santé interne : port `8081`, `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`.
- La readiness inclut PostgreSQL ; la liveness décrit seulement le processus.
- Variables obligatoires : `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Base dédiée : `toq_recipe`. Aucun accès aux autres bases.
- Migrations futures : `src/main/resources/db/migration/V<n>__description.sql`.
  Aucun schéma métier n'est créé en phase 1 ; Flyway initialise son historique.

Depuis la racine du monorepo :

```sh
./mvnw -B -ntp -pl services/recipe-service -am verify
docker build -f services/recipe-service/Dockerfile .
```

Sous PowerShell, utiliser `.\mvnw.cmd`. Le JDK local doit être Java 21 ou supérieur ;
les images de build et d'exécution utilisent Java 21. Les tests démarrent de vrais
serveurs HTTP avec une base H2 éphémère ; Compose vérifie PostgreSQL.

Le Dockerfile utilise le contexte racine, un build multi-stage et un utilisateur
sans privilège. Ne pas publier directement les ports du service sur l'hôte :
le frontend/reverse proxy route les futures API.

