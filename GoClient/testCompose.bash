#!/bin/bash

# Starte docker-compose im Hintergrund
docker compose up --build -d

# Warten, bis der Container da ist
sleep 2

# Container-Name (aus docker-compose.yml)
CONTAINER_NAME="GoClient"

# CSV-Datei vorbereiten
echo "Timestamp,CPU (%),Mem (MB)" > resource_log.csv

# Solange der Container läuft, überwachen
while docker ps --format '{{.Names}}' | grep -q "$CONTAINER_NAME"; do
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")

    # CPU % und Mem in MB auslesen
    USAGE=$(docker stats --no-stream --format "{{.CPUPerc}},{{.MemUsage}}" "$CONTAINER_NAME" | awk -F '[ /]+' '{print $1","$2}')

    echo "$TIMESTAMP,$USAGE" >> resource_log.csv
    sleep 1
done

echo "Monitoring beendet."

# Optional: Container herunterfahren
docker compose down