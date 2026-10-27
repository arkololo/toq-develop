# Phase 4 — Nutrition

Le service Nutrition expose un calcul déterministe des calories et macronutriments à partir de valeurs pour 100 g, avec totaux de recette et de portion : `POST /api/v1/nutrition/calculate`.

La lecture d’un produit par code-barres est disponible par `GET /api/v1/nutrition/products/{barcode}`. Nutrition est le seul service qui appelle OpenFoodFacts. Le résultat est placé dans `food_cache` PostgreSQL : les lectures suivantes sont locales. Le client impose un délai de six secondes et un `User-Agent` configurable.

Les routes sont protégées par le jeton Keycloak et son audience `toq-api`. Les tests couvrent le calcul et le démarrage avec la migration Flyway.

Les fichiers `toq-logo.png` et `toqi-charte.png` du frontend proviennent directement du dossier `base/Charte graphique` ; la navigation connectée et l’accueil utilisent ces assets.
