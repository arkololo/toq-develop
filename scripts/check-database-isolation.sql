\set ON_ERROR_STOP on
-- Read-only checks of the effective connection and schema privileges.
DO $$
DECLARE
    item record;
    other_db text;
BEGIN
    FOR item IN SELECT * FROM (VALUES
        ('toq_user_app', 'toq_user'),
        ('toq_recipe_app', 'toq_recipe'),
        ('toq_nutrition_app', 'toq_nutrition'),
        ('toq_planner_app', 'toq_planner'),
        ('toq_social_app', 'toq_social'),
        ('keycloak_app', 'keycloak')
    ) AS services(role_name, db_name)
    LOOP
        IF NOT has_database_privilege(item.role_name, item.db_name, 'CONNECT') THEN
            RAISE EXCEPTION 'Missing own database permission for %', item.role_name;
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = item.role_name
                   AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication)) THEN
            RAISE EXCEPTION 'Unexpected elevated role: %', item.role_name;
        END IF;
        FOREACH other_db IN ARRAY ARRAY['toq_user','toq_recipe','toq_nutrition','toq_planner','toq_social','keycloak','postgres','template1']
        LOOP
            IF other_db <> item.db_name AND has_database_privilege(item.role_name, other_db, 'CONNECT') THEN
                RAISE EXCEPTION 'Cross-database permission: % -> %', item.role_name, other_db;
            END IF;
        END LOOP;
    END LOOP;
END $$;
SELECT 'Six application roles are isolated' AS result;
