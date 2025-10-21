FROM gradle:8.5-jdk21 AS build

# Arbeitsverzeichnis im Container
WORKDIR /app

# Nur relevante Dateien kopieren
COPY build.gradle settings.gradle* gradlew ./
COPY gradle ./gradle
COPY src ./src



# Baue das Projekt (inkl. Abhängigkeiten)
RUN ./gradlew build --no-daemon

# --------------------------------------

# Neues schlankes Image für Ausführung
FROM openjdk:21

WORKDIR /app

# Kopiere nur das gebaute JAR aus dem vorherigen Stage
COPY --from=build /app/build/libs/*.jar app.jar
#COPY build/libs/*-all.jar app.jar
# Starte die Java-Anwendung
CMD ["java","-Xmx1512m", "-jar", "app.jar"]