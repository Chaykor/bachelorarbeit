# Build-Stage
FROM rust:1.86 AS build

# Arbeitsverzeichnis im Container
WORKDIR /app

# Nur relevante Dateien kopieren
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY test_data ./test_data



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

RUN mkdir "/app/output"
# Binary + Testdaten kopieren
COPY --from=build /app/target/release/RustClient ./app
COPY test_data ./test_data


# Start der Anwendung
CMD ["./app"]