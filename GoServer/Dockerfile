# Wähle ein offizielles Go-Image als Basis
FROM golang:1.22

# Setze das Arbeitsverzeichnis im Container
WORKDIR /app

# Kopiere Go-Module-Dateien zuerst (damit Docker Cache genutzt wird)
COPY go.mod go.sum ./

# Falls sie noch nicht existieren, erzeugt der nächste Schritt sie
RUN go mod tidy
# Kopiere den Code in das Arbeitsverzeichnis
COPY src/main.go .
RUN go get github.com/gorilla/mux
# Baue die Go-Anwendung
RUN go build -o app main.go

# Setze den Befehl zum Ausführen der Anwendung
CMD ["./app"]