# Phase 3 — Recettes

La phase fournit le CRUD des recettes privées. Chaque recette est rattachée au `sub` du jeton Keycloak et un autre utilisateur ne peut ni la lire, ni la modifier, ni la supprimer.

L’API expose `GET/POST /api/v1/recipes`, `GET/PUT/DELETE /api/v1/recipes/{id}` et `GET /api/v1/ingredients?query=`. Les recettes comportent un titre, une description facultative, un nombre de portions, des ingrédients quantifiés et des étapes ordonnées. La suppression archive la recette.

Le frontend propose la création, la liste et la suppression depuis l’espace connecté. L’entrée des ingrédients accepte une ligne au format `nom | quantité | unité`.
