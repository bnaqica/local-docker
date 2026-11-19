# Keycloak Kafka event listener

A Keycloak provider (event listener `kafka`) that publishes every user event (logins, failed logins, lockouts,
registrations, password and TOTP changes) and admin event of a realm to the Kafka topic `keycloak-events`, after the
Keycloak transaction commits. The CRM user management service reads it: a first `LOGIN` turns an INVITED user ACTIVE,
lockouts feed the mass-lockout alert, `REGISTER` records portal starters, and the central audit stores everything.

Message shapes (the contract is in the user management service's `docs/events.md` and `docs/audit.md`):

```json
{"id": "…", "time": 1762855200000, "realm": "crm-internal", "type": "LOGIN", "userId": "…", "error": null,
 "details": {"username": "…", "ipAddress": "…", "clientId": "crm-app"}}
{"id": "…", "time": 1762855200000, "realm": "crm-internal", "kind": "ADMIN", "operationType": "UPDATE",
 "resourceType": "USER", "resourcePath": "users/…", "authDetails": {"userId": "…", "ipAddress": "…"},
 "representation": "{…}", "error": null}
```

User events are keyed by user id, admin events by realm name. Sending is asynchronous and never fails a login: if
Kafka is down, the event is logged as an error and lost (Keycloak's own event store still keeps it for 30 days).

## Build and test

```bash
./gradlew build
```

Builds `build/libs/keycloak-kafka-event-listener-<version>.jar` (with kafka-clients bundled) and runs the tests,
including one that loads the jar into a real Keycloak and checks the messages on a real Kafka (Docker needed).

## Settings

| Keycloak option | Environment variable | Default |
|---|---|---|
| `spi-events-listener-kafka-bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | none (required) |
| `spi-events-listener-kafka-topic` | `KEYCLOAK_EVENTS_TOPIC` | `keycloak-events` |

A realm only publishes if `kafka` is in its event listeners (the generated CRM realms have it).
