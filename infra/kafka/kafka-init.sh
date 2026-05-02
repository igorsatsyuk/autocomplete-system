#!/bin/bash

set -e

KAFKA_HOST=${KAFKA_HOST:-kafka}
KAFKA_PORT=${KAFKA_PORT:-9092}

echo "Waiting for Kafka at $KAFKA_HOST:$KAFKA_PORT..."

# Wait until Kafka is reachable
while ! nc -z $KAFKA_HOST $KAFKA_PORT; do
  sleep 1
done

echo "Kafka is up. Creating topics..."

create_topic() {
  local topic=$1
  local partitions=$2
  local replication=$3

  echo "Creating topic: $topic"

  kafka-topics --create \
    --if-not-exists \
    --topic "$topic" \
    --bootstrap-server "$KAFKA_HOST:$KAFKA_PORT" \
    --partitions "$partitions" \
    --replication-factor "$replication" || true
}

create_topic "search-events" 3 1
create_topic "search-stats" 3 1
create_topic "db-changes.public.search_stats" 1 1

echo "Kafka topics created."