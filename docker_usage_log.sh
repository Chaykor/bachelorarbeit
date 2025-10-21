#!/bin/bash

# Name des Containers (ändern!)
CONTAINER_NAME="NodeClient"

# Intervall in Sekunden
INTERVAL=1

# Ausgabedatei
OUTPUT_FILE="docker_usage_${CONTAINER_NAME}.csv"

# Header schreiben (nur, wenn Datei neu ist)
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "timestamp,container,cpu_percent,mem_usage,mem_limit,net_input,net_output" > "$OUTPUT_FILE"
fi

echo "Starte Logging für Container '$CONTAINER_NAME' (alle $INTERVAL Sekunden)..."
echo "Schreibe in: $OUTPUT_FILE"
echo "Zum Beenden: STRG + C"

while true; do
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
    
    # Docker Stats einmalig abrufen
    STATS=$(docker stats --no-stream --format "{{.Container}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}}" $CONTAINER_NAME)
    echo $STATS
    # Nur loggen, wenn Container läuft
    if [ -n "$STATS" ]; then
        # Werte aufsplitten
        CONTAINER=$(echo "$STATS" | cut -d',' -f1)
        CPU=$(echo "$STATS" | cut -d',' -f2 | tr -d '%')
        MEM_USAGE=$(echo "$STATS" | cut -d',' -f3 | awk '{print $1}')
        NET_IO=$(echo "$STATS" | cut -d',' -f5)
        NET_IN=$(echo "$NET_IO" | awk '{print $1}')
        NET_OUT=$(echo "$NET_IO" | awk '{print $3}')
        
        # Zeile in CSV schreiben
        echo "$TIMESTAMP,$CONTAINER,$CPU,$MEM_USAGE,$NET_IO" >> "$OUTPUT_FILE"
    else
        echo "$TIMESTAMP,$CONTAINER_NAME,not_running,not_running,not_running,0,0" >> "$OUTPUT_FILE"
    fi
    
    sleep "$INTERVAL"
done
