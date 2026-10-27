# État du projet TOQ — 30 septembre 2026

L'application fonctionne exclusivement en local, via Docker Compose. Aucun cloud, staging distant, certificat public ou sauvegarde hors hôte n'est configuré.

| Phase | État local |
|---|---|
| 0 — Architecture | Validée ; voir `architecture/ARCHITECTURE_PHASE_0.md` et ADR-013 |
| 1 — Socle | React, Nginx, Keycloak, PostgreSQL, Compose opérationnels |
| 2 — Utilisateurs | Pages locales `/connexion` et `/inscription`, formulaires Keycloak habillés avec la charte TOQ, pseudo obligatoire au premier accès, préférences à cases à cocher et objectifs |
| 3 — Recettes | Lignes d'ingrédients, macros par portion, photo, modèles TheMealDB traduits en français par Gemini et recherche française par mots-clés |
| 4 — Nutrition | Open Food Facts par code-barres, catalogue CIQUAL 2025 local pour estimation choisie, calcul manuel par portion ou calcul historique |
| 5 — Planning | Semaine, repas, portions, apports planifiés, liste de courses par catégorie, agrégation et conversion g/kg, ml/l |
| 6 — Social P1 | Publication avec reprise de la photo de recette, fil paginé, likes, commentaires, onglet Mes favoris et résolution du pseudo courant |
| 7 — CI/CD | Tests et build CI définis ; runner et déploiement automatisé non configurés |
| 8 — Sécurité | JWT issuer/audience, bases isolées, réseau privé, sondes, limites de requête ; revue et scans exhaustifs à poursuivre |
| 9 — Documentation | Procédures locales, sécurité et limites documentées |
| Assistant IA | Gemini côté User Service, chat TOQi, traduction, lots de recettes, modification de repas et proposition d'une semaine complète (21 à 28 repas) avec confirmation avant remplacement ; clé API locale requise pour l'activer |

Le parcours principal est réalisable dans l'interface : créer un compte, choisir un pseudo, saisir les objectifs, créer une recette avec photo ou partir d'un modèle TheMealDB, renseigner ses macros par portion, planifier et générer les courses, puis publier avec photo et interagir. Les modèles externes sont des sources d'inspiration : leurs unités et allergènes doivent être vérifiés. Les valeurs CIQUAL sont explicitement des estimations pour un aliment équivalent choisi par l'utilisateur, jamais des valeurs mesurées pour un produit de marque. L'optimisation du planning, l'analyse automatique d'allergènes dans une recette et les métriques de production restent à faire.
