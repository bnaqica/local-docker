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

Topics are created by the `kafka-setup` service — edit the `TOPICS` list in
`kafka/docker-compose.yml`, then re-run it with `docker compose up kafka-setup`.
Auto-create is disabled, so producing to an unlisted topic fails.
