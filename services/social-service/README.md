# Social Service

Service Spring Boot dédié aux publications de recettes. Une publication conserve le titre de la recette au moment de la publication, après vérification que son auteur possède cette recette. Les likes, favoris et commentaires sont liés à l'identifiant Keycloak (`sub`). Le fil est paginé.

Base PostgreSQL propre : `toq_social`. Migration : `V1__social.sql`. Le service n'expose aucun port sur l'hôte ; Nginx route `/api/v1/social/**`.
