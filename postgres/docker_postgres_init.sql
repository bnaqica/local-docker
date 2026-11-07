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
