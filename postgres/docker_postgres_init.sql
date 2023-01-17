-- Grades Service Postgres DB
CREATE USER grades_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE grades_db
    WITH
    OWNER = grades_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE grades_db TO grades_local;