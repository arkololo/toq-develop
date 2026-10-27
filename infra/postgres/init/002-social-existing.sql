-- Run once against an existing local PostgreSQL volume after adding TOQ_SOCIAL_DB_PASSWORD.
\set ON_ERROR_STOP on
\set ECHO none
\set QUIET on
\getenv social_password TOQ_SOCIAL_DB_PASSWORD
SELECT format('CREATE ROLE toq_social_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'social_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='toq_social_app') \gexec
SELECT 'CREATE DATABASE toq_social OWNER toq_social_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='toq_social') \gexec
REVOKE ALL ON DATABASE toq_social FROM PUBLIC;
\connect toq_social
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO toq_social_app;
ALTER DEFAULT PRIVILEGES FOR ROLE toq_social_app IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
