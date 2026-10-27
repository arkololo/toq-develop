# Phase 6 — Social

Le Social Service expose un fil de publications paginé (`GET /api/v1/social/publications?page=&size=`). `POST /publications` vérifie auprès du Recipe Service, avec le jeton de l'auteur, que la recette lui appartient avant d'enregistrer son titre et son identifiant. Les autres membres connectés peuvent liker, commenter et marquer la publication en favori ; l'auteur peut la supprimer. Le frontend « Partager » offre ces actions et reprend l'illustration TOQi des publications de la charte.

Les données sont dans `toq_social`, isolées des autres services. Le test `SocialInteractionTest` couvre les réactions et commentaires entre deux utilisateurs. Les images de recettes personnelles et la modération de contenu ne sont pas encore disponibles ; les visuels TOQi du fil sont décoratifs.
