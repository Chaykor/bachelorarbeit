# Wähle ein offizielles Go-Image als Basis
FROM golang:1.21

# Setze das Arbeitsverzeichnis im Container
WORKDIR /app

# Kopiere den Code in das Arbeitsverzeichnis
COPY src/main.go .

COPY /test_data ./test_data

# Baue die Go-Anwendung
RUN go mod init app && go build -o app main.go

# Setze den Befehl zum Ausführen der Anwendung
CMD ["./app", "sh"]