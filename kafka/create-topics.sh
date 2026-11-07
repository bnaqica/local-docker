#!/bin/sh
# Run by the one-shot `kafka-setup` service once the broker is healthy.
# Auto-create is disabled on the broker, so every topic an app uses must be listed here.
#
# Add or rename topics below, then re-run:  docker compose up kafka-setup
# Existing topics are left alone (--if-not-exists), so re-running is safe.

set -e

BOOTSTRAP_SERVER="kafka:29092"
PARTITIONS=3
REPLICATION_FACTOR=1   # single broker

TOPICS="
  example-topic
  example-topic-dlq

  customer-events
  customer-events-dlq

  email-events
  email-events-dlq
"

for topic in $TOPICS; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists --topic "$topic" \
    --partitions "$PARTITIONS" --replication-factor "$REPLICATION_FACTOR"
done

echo "Topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
