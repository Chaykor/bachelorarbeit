#!/bin/bash

# Pfad zu deiner Node.js-Datei (z. B. index.js oder app.js)
NODE_FILE="./index.js"

# Starte das Node.js-Programm im Hintergrund
node "$NODE_FILE" &

# PID des gestarteten Prozesses holen
PID=$!
# CSV-Datei vorbereiten
echo "Timestamp,CPU (%),Mem (MB)" > resource_log.csv

# Solange der Prozess läuft, überwachen
while ps -p $PID > /dev/null; do
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
    USAGE=$(ps -p $PID -o %cpu=,%mem= | awk '{print $1","$2}')
    echo "$TIMESTAMP,$USAGE" >> resource_log.csv
    sleep 1
done

echo "Monitoring beendet."