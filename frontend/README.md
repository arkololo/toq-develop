# Frontend TOQ

La phase 1 installe React, TypeScript et Vite avec une page publique responsive issue de la charte fournie. Les liens de présentation fonctionnent ; la connexion Keycloak et les écrans métier arrivent dans leurs phases respectives. Aucun profil, recette, apport nutritionnel ou planning fictif n’est affiché.

Depuis ce dossier, avec Node 22.15.1 ou supérieur compatible (image de build fixée à 22.23.2) :

```sh
npm ci
npm run dev
```

Vite écoute sur `http://127.0.0.1:5173`. Cette commande sert uniquement le frontend ; pour l’environnement complet, suivre le README racine et Docker Compose.

```sh
npm run lint
npm test
npm run build
```

Les tests vérifient les destinations des liens, le lien d’évitement et les règles sémantiques WCAG A/AA via axe-core. Le contraste et le rendu responsive demandent une vérification dans un navigateur. Le build est servi par Nginx sans processus Node à l’exécution ; la configuration du proxy appartient à `infra/reverse-proxy/default.conf`.

Les dépendances sont fixées dans `package.json` et `package-lock.json`. TypeScript 5.9 reste compatible avec l’analyseur typescript-eslint ; jsdom 26 reste compatible avec le Node 22.15.1 disponible localement. Les exigences Node de Vite sont documentées dans son [guide officiel](https://vite.dev/guide/). Les images sont fixées par version et digest après vérification sur Docker Hub le 22 septembre 2026. Aucun fichier de police, script tiers ou asset n’est chargé depuis un CDN.

La palette validée est centralisée dans `src/styles/tokens.css`. Voir `src/assets/README.md` pour la provenance des images. Les répertoires métier, clients API et routage seront ajoutés quand leurs premiers usages seront implémentés.
