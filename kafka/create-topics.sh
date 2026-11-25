#!/bin/sh
# Run by the one-shot `kafka-setup` service once the broker is healthy.
# Auto-create is disabled on the broker, so every topic an app uses must be listed here.
#
# Add or rename topics below, then re-run:  docker compose up kafka-setup
# Existing topics are left alone (--if-not-exists), so re-running is safe.

set -e

BOOTSTRAP_SERVER="kafka-1:29092,kafka-2:29092,kafka-3:29092,kafka-4:29092"
PARTITIONS=5
REPLICATION_FACTOR=4   # one replica on each of the 4 brokers

TOPICS="
  customer-change-event
  customer-change-event-dlq
  ums-user-events
  audit-events
  keycloak-events
  keycloak-events-dlq
"

for topic in $TOPICS; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists --topic "$topic" \
    --partitions "$PARTITIONS" --replication-factor "$REPLICATION_FACTOR"
done

echo "Topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
