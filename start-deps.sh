#!/usr/bin/env bash
set -euo pipefail

docker compose up -d mysql redis rmqnamesrv rmqbroker

echo "Waiting for MySQL, Redis and RocketMQ..."
for attempt in $(seq 1 60); do
    mysql_status=$(docker inspect --format '{{.State.Health.Status}}' agents-mysql 2>/dev/null || true)
    redis_status=$(docker inspect --format '{{.State.Health.Status}}' agents-redis 2>/dev/null || true)
    namesrv_status=$(docker inspect --format '{{.State.Health.Status}}' agents-rmqnamesrv 2>/dev/null || true)
    broker_status=$(docker inspect --format '{{.State.Health.Status}}' agents-rmqbroker 2>/dev/null || true)

    if [[ "$mysql_status" == "healthy" && "$redis_status" == "healthy" \
        && "$namesrv_status" == "healthy" && "$broker_status" == "healthy" ]]; then
        echo "Dependencies are healthy."
        docker compose ps
        exit 0
    fi
    sleep 2
done

docker compose ps
echo "Dependencies did not become healthy within 120 seconds." >&2
exit 1
