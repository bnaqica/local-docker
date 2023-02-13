-- Applicant Credit Score Postgres DB
CREATE USER applicant_credit_score_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE applicant_credit_score_db
    WITH
    OWNER = applicant_credit_score_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE applicant_credit_score_db TO applicant_credit_score_local;

-- Applicant Portal Postgres DB
CREATE USER applicant_portal_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE applicant_portal_db
    WITH
    OWNER = applicant_portal_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE applicant_portal_db TO applicant_portal_local;

-- Application Ingestion Postgres DB
CREATE USER application_ingestion_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE application_ingestion_db
    WITH
    OWNER = application_ingestion_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE application_ingestion_db TO application_ingestion_local;