# local-docker

Docker environments for local development. Each folder is a standalone `docker compose` stack.

## Kafka

Single-broker Kafka (KRaft, no ZooKeeper) with Kafka UI and a topic-setup container.

```bash
cd kafka
docker compose up -d     # start
docker compose down      # stop (data is wiped)
```

| What | Address |
|---|---|
| Bootstrap servers (apps on your machine) | `localhost:9092` |
| Bootstrap servers (other containers) | `kafka:29092` |
| Kafka UI | http://localhost:8085 |

Topics are created by the `kafka-setup` service, which runs `kafka/create-topics.sh` — edit the
`TOPICS` list in that script, then re-run it with `docker compose up kafka-setup`.
Auto-create is disabled, so producing to an unlisted topic fails.

## LocalStack (SNS, SQS, S3)

```bash
cd localstack
docker compose up -d
```

Endpoint: `http://localhost:4566`, region `us-east-1`, account `000000000000`.

Topics, queues, SNS→SQS subscriptions and buckets are created by `localstack/create-resources.sh`,
which LocalStack runs itself once it is ready (it's mounted into `/etc/localstack/init/ready.d/`).
Edit the `SUBSCRIPTIONS` and `BUCKETS` lists in that script, then recreate the container with
`docker compose down && docker compose up -d`.

Check that the script finished:

```bash
curl -s localhost:4566/_localstack/init/ready    # "state": "SUCCESSFUL"
```

`localstack/publishMessages.txt` has an example `sns publish` command.

## OpenSearch

Single-node OpenSearch with OpenSearch Dashboards.

```bash
cd opensearch
docker compose up -d
```

| What | Address |
|---|---|
| REST API | http://localhost:9200 |
| OpenSearch Dashboards | http://localhost:5601 (Dev Tools runs queries in the browser) |

The security plugin is **disabled** — plain HTTP and no login — so this is for local use only.
The JVM heap is 512 MB; raise `OPENSEARCH_JAVA_OPTS` in the compose file for bigger data sets.
There is no volume, so `docker compose down` wipes all indices.

```bash
curl -s localhost:9200/_cluster/health    # "status": "green"
```

## Redis (cache)

Redis configured as a cache, plus RedisInsight as a browser UI.

```bash
cd redis
docker compose up -d
```

| What | Address |
|---|---|
| Redis (apps on your machine) | `redis://:local@localhost:6379` (password `local`) |
| Redis (other containers in this stack) | `redis:6379` |
| RedisInsight | http://localhost:5540 |

- Memory is capped at 256 MB with `allkeys-lru`: when it's full, the least recently used keys are evicted.
- No persistence (no RDB snapshots, no AOF); `docker compose down` wipes everything.
- RedisInsight asks you to accept its EULA on first launch; the `local-redis` database is pre-configured.

```bash
docker exec bnaqica-redis redis-cli -a local --no-auth-warning ping    # PONG
```

Spring Boot: add `spring-boot-starter-data-redis` (and `spring-boot-starter-cache` for `@Cacheable`), then
set `spring.data.redis.host=localhost`, `spring.data.redis.port=6379`, `spring.data.redis.password=local`.

## Postgres

```bash
cd postgres
docker compose up -d
```

`localhost:5432`. Databases and users are created on first start by `docker_postgres_init.sql`
(password `local` for every user):

| Database | User | Used by |
|---|---|---|
| `kafka_demo_customer_db` | `kafka_demo_customer_local` | kafka-demo-customer |

There is no volume, so `docker compose down` wipes the data and the init script runs again on the next `up`.

## MongoDB

```bash
cd mongoDB
docker compose up -d
```

`mongodb://localhost:27017`, root user `admin` / `local`. `mongo-init.js` creates one database and
owner user per service on first start (password `local`):

| Database | User | Used by |
|---|---|---|
| `kafka_demo_email_db` | `kafka_demo_email_local` | kafka-demo-email |
| `kafka_demo_analytics_db` | `kafka_demo_analytics_local` | kafka-demo-analytics |

Connection string: `mongodb://<user>:local@localhost:27017/<db>?authSource=<db>`.

## Kafka Demo topics

`kafka-setup` creates `customer-events`, `email-events` and a `-dlq` topic for each, used by the
kafka-demo-customer, kafka-demo-email and kafka-demo-analytics services.
