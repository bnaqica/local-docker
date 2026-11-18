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

## Keycloak (identity / OAuth2)

Keycloak 26 in dev mode, with a `kafka-demo` realm imported from `keycloak/realms/kafka-demo-realm.json`
on startup. To change the realm, edit that file and recreate the container (`docker compose down && docker compose up -d`).

```bash
cd keycloak
docker compose up -d
```

| What | Address |
|---|---|
| Admin console | http://localhost:8180/admin (user `admin`, password `local`) |
| Issuer (realm) | `http://localhost:8180/realms/kafka-demo` |
| OIDC discovery | http://localhost:8180/realms/kafka-demo/.well-known/openid-configuration |
| From other containers | `http://keycloak:8080` (tokens still carry the `localhost:8180` issuer) |

Port 8180 because 8080 is used by the kafka-demo-ui container. The management port (9000, health) is
not published, since SonarQube uses 9000.

What the realm contains:

| Name | Type | Notes |
|---|---|---|
| `kafka-demo-ui` | Public client | Authorization code + PKCE (S256). Redirects to `http://localhost:5173/*` and `http://localhost:8080/*` |
| `kafka-demo-api` | Confidential client, secret `local` | The audience the services should require. Client credentials for service-to-service; password grant for curl testing |
| `customer-admin`, `customer-viewer` | Realm roles | In the token under `realm_access.roles` |
| `kafkaDemoAdmin` / `local` | User | Role `customer-admin` |
| `kafkaDemoViewer` / `local` | User | Role `customer-viewer` |

Access tokens from both clients have `aud` containing `kafka-demo-api`.

Dev mode uses an embedded database with no volume, so `docker compose down` wipes everything not in the realm file.
The realm file is only imported into a fresh database, so after changing it run `docker compose up -d --force-recreate` in `keycloak/`.

### CRM demo realms

`crm-admin-realm.json`, `crm-internal-realm.json` and `portal-realm.json` are generated for the CRM demo
(see the architecture in `crm-demo-user-management-service`). Each UI signs in to one realm, and each service trusts one realm.

| Realm | Client | Type | Notes |
|---|---|---|---|
| `crm-admin` | `admin-app` | Public, PKCE | Admin UI on `http://localhost:5175` |
| `crm-internal` | `crm-app` | Public, PKCE | CRM UI on `http://localhost:5173`; tokens carry audience `crm-api` |
| `crm-internal` | `crm-api` | Resource server | Holds CRM permissions as client roles |
| `crm-internal` | `ums-internal` | Service account, secret `ums-internal-local-secret` | `manage-users`, `view-users`, `query-groups` only |
| `crm-internal` | `crm-promoter` | Service account, secret `crm-promoter-local-secret` | Scope `ums:promote` |
| `crm-internal` | `core-service` | Service account, secret `core-service-local-secret` | Scopes `ums:team-scope`, `ums:catalog` |
| `portal` | `portal-app` | Public, PKCE | Portal on `http://localhost:5174`; self-registration on, email verification on |
| `portal` | `ums-portal` | Service account, secret `ums-portal-local-secret` | `manage-users`, `view-users`, `query-groups` only |

`crm-admin` has the user `admin.local` / `Local-admin-123` with realm role `admin`, who must set up TOTP on first login.
In `crm-internal` and `portal` the username is the email. All three realms: passwords of at least 12 characters, brute-force lockout (permanent after 3 temporary lockouts),
5-minute access tokens, login and admin events kept for 30 days. `crm-admin` sessions idle out after 15 minutes.
Not yet configured: breached-password checks, portal CAPTCHA, mandatory MFA for portal users, and fine-grained admin
permissions narrowing the service accounts.

Keycloak sends email through Mailpit, which catches everything: open http://localhost:8025 to read invites and
password-reset emails.

The sign-in page uses the `kafka-demo` login theme in `keycloak/themes/kafka-demo`. It extends Keycloak's own login theme
and recolours it to match kafka-demo-ui (`resources/css/kafka-demo.css`). `start-dev` doesn't cache themes, so CSS edits
show on reload; changing `theme.properties` needs a restart.
Not for production: HTTP only, fixed secrets, password grant enabled.

```bash
# Get a token for a test user
curl -s localhost:8180/realms/kafka-demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=kafka-demo-api -d client_secret=local \
  -d username=kafkaDemoAdmin -d password=local
```

Spring Boot resource server: add `spring-boot-starter-security-oauth2-resource-server`, then set
`spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/kafka-demo` and
`spring.security.oauth2.resourceserver.jwt.audiences=kafka-demo-api`.

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
| `crm_demo_core_service_db` | `crm_demo_core_service_local` | crm-demo-core-service |
| `crm_demo_customer_portal_service_db` | `crm_demo_customer_portal_service_local` | crm-demo-customer-portal-service |
| `crm_demo_user_management_service_db` | `crm_demo_user_management_service_local` | crm-demo-user-management-service |

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

`kafka-setup` creates `customer-change-event` and its `-dlq` topic, used by the
kafka-demo-customer, kafka-demo-email and kafka-demo-analytics services.
