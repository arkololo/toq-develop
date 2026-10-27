# ADR-013 — Bootstrap exclusivement local

Statut : **accepté**, instruction utilisateur après validation de la phase 0, le 22 septembre 2026.

## Décision

Toute l’infrastructure actuellement mise en place s’exécute sur la machine de développement avec Docker et Docker Compose. Quatre services métier sont actifs : User, Recipe, Nutrition et Planner. Social reste réservé à P1. React, Spring Boot, Keycloak et PostgreSQL sont conservés.

Les interfaces web locales sont publiées uniquement sur l’interface de boucle locale. PostgreSQL et les ports des services ne sont pas publiés. HTTP est une exception explicite de développement local, sans certificat public et sans revendication de chiffrement du trafic ou des volumes locaux.

La phase 1 crée les squelettes exécutables, le socle de stockage, les healthchecks, une landing React, les Dockerfiles et une définition de CI minimale. La configuration du realm TOQ, de PKCE et du profil appartient à la phase 2. Aucune fonction métier n’est simulée pour masquer ce découpage.

## Conséquences

Oracle Cloud, staging distant, certificats publics, sauvegardes hors hôte, publication d’images et infrastructures externalisées sont différés. Aucun runner n’est enregistré auprès de GitLab durant ce bootstrap ; le fichier de pipeline est préparé pour une activation ultérieure. Les mêmes commandes de validation sont exécutables localement.

La première construction télécharge des dépendances et des images publiques. Ce téléchargement ne déploie pas de service externe. Les builds suivants réutilisent les caches et images présents sur la machine.

La partie déploiement distant du dossier de phase 0 décrit une cible ultérieure, pas l’environnement actuellement implémenté. Cette décision prévaut sur ADR-006, ADR-011 et le périmètre production d’ADR-004 pour le travail actuel.
