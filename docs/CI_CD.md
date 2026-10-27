# CI/CD locale

`.gitlab-ci.yml` lance les tests Maven, lint/tests/build frontend, audit des dépendances npm de production puis la construction des images Docker. Le job de build est prévu pour un GitLab Runner **Shell dédié à cette machine**, portant le tag `toq-local`, avec Docker et Compose installés. Il génère un `.env` propre à son répertoire de travail ; il n'effectue aucun déploiement externe.

Le runner n'est pas enregistré dans ce dépôt et aucun pipeline GitLab n'a été exécuté depuis cet environnement. Installer/enregistrer un runner, configurer des scans SAST, secrets et conteneurs, puis établir un processus de déploiement et de retour arrière sera une étape distincte avant toute cible hors local. Les commandes locales équivalentes sont dans le README.
