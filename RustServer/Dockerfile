# Build-Stage
FROM rust:1.86 AS build

# Arbeitsverzeichnis im Container
WORKDIR /app

# Nur relevante Dateien kopieren
COPY Cargo.toml Cargo.lock ./
COPY src ./src

# Projekt kompilieren (Release-Modus)
RUN cargo build --release

# --------------------------------------

# Runtime-Stage: minimal
FROM debian:bookworm-slim

WORKDIR /app

# Benötigte Libs installieren (für glibc-linked Binaries)
RUN apt-get update && apt-get install -y \
    ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Binary + Testdaten kopieren
COPY --from=build /app/target/release/RustServer ./app

# Start der Anwendung
CMD ["./app"]