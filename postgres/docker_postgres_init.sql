-- Kafka Demo Customer Postgres DB
CREATE USER kafka_demo_customer_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE kafka_demo_customer_db
    WITH
    OWNER = kafka_demo_customer_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE kafka_demo_customer_db TO kafka_demo_customer_local;

-- CRM Demo core service Postgres DB
CREATE USER crm_demo_core_service_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE crm_demo_core_service_db
    WITH
    OWNER = crm_demo_core_service_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE crm_demo_core_service_db TO crm_demo_core_service_local;

-- CRM Demo customer portal service Postgres DB
CREATE USER crm_demo_customer_portal_service_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE crm_demo_customer_portal_service_db
    WITH
    OWNER = crm_demo_customer_portal_service_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE crm_demo_customer_portal_service_db TO crm_demo_customer_portal_service_local;

-- CRM Demo user management service Postgres DB
CREATE USER crm_demo_user_management_service_local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE crm_demo_user_management_service_db
    WITH
    OWNER = crm_demo_user_management_service_local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE crm_demo_user_management_service_db TO crm_demo_user_management_service_local;

-- CRM Demo central audit DB (written and read by the user management service). Append-only:
-- audit_owner owns the schema and runs its Flyway migrations; audit_writer may only INSERT, audit_reader only SELECT.
-- The migrations grant those rights; nothing can UPDATE or DELETE audit records.
CREATE USER audit_owner WITH PASSWORD 'local';
CREATE USER audit_writer WITH PASSWORD 'local';
CREATE USER audit_reader WITH PASSWORD 'local';
CREATE DATABASE crm_audit_db
    WITH
    OWNER = audit_owner
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
REVOKE ALL ON DATABASE crm_audit_db FROM PUBLIC;
GRANT CONNECT ON DATABASE crm_audit_db TO audit_owner, audit_writer, audit_reader;
