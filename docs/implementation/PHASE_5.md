# Phase 5 — Planning

Le Planner Service stocke un planning hebdomadaire privé : une semaine commence le lundi et chaque repas porte un instantané de recette, une date, un créneau et un nombre de portions. L’API disponible est `GET /api/v1/plans/{weekStart}` et `POST /api/v1/plans/{weekStart}/meals`.

La consolidation transactionnelle de la liste de courses est la prochaine sous-étape de cette phase.
