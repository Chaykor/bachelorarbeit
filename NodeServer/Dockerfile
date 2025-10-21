# Basis-Image
FROM node:20-slim

# Arbeitsverzeichnis im Container
WORKDIR /app

# Abhängigkeiten installieren
COPY package*.json ./
RUN npm ci

# Anwendungscode kopieren
COPY . .

# Anwendung starten
CMD ["node", "index.js"]