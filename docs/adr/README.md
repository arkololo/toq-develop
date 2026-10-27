# TOQ — Registre des décisions

Mise à jour : 23 septembre 2026. L’utilisateur a validé la Phase 0 et autorisé la Phase 1, avec une infrastructure exclusivement locale.

Les motifs et alternatives d’origine figurent dans la [section 9 de l’architecture](../architecture/ARCHITECTURE_PHASE_0.md#9-décisions-techniques-à-valider). [ADR-013](0013-developpement-local.md) définit la restriction locale qui prévaut pour l’implémentation actuelle.

| ADR | Sujet | État effectif |
|---|---|---|
| 001 | Quatre services P0, Social P1, courses dans Planner | Accepté ; squelettes P0 créés |
| 002 | PostgreSQL, bases/rôles isolés, Flyway | Accepté ; socle local en place |
| 003 | React SPA et Nginx commun au frontend et au routage | Accepté ; en place |
| 004 | Authentification, ownership et chiffrement | Cible acceptée ; API fermées, HTTP local, configuration JWT en Phase 2 |
| 005 | Instantanés de planning et cohérence des calculs | Accepté ; implémentation métier ultérieure |
| 006 | Hôte APP, Runner distinct et staging | Différé ; uniquement la machine locale actuellement |
| 007 | Nutrition prévue et périmètre des imports | Accepté ; implémentation ultérieure |
| 008 | Visibilité et suppression des données | Principes acceptés ; cycle de vie à préciser avant activation sociale |
| 009 | Identité graphique et assets | Accepté ; landing utilisant le logo et TOQi d’origine |
| 010 | Pipeline et stratégie de livraison | Définition CI minimale ; Runner et déploiement distant différés |
| 011 | Sauvegardes et restauration hors hôte | Différé explicitement |
| 012 | Versions et reproductibilité | Versions Phase 1 figées, lockfile npm et digests Docker |
| 013 | Bootstrap exclusivement local | Accepté, instruction explicite utilisateur |

Les versions effectivement retenues et les vérifications sont consignées dans [PHASE_1.md](../PHASE_1.md). Aucun service externe ni déploiement distant n’est créé par cette phase.
