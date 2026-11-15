#!/bin/sh
set -e
cd "$(dirname "$0")"

trap 'docker compose -f db/docker-compose.yml down' EXIT

docker compose -f db/docker-compose.yml up -d --wait
javac -cp "lib/*" *.java
java -cp ".:lib/*" org.junit.runner.JUnitCore LogWaterTest
