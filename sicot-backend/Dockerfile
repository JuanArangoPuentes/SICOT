# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so code edits don't re-download.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B clean package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S sicot && adduser -S sicot -G sicot
COPY --from=build /build/target/sicot-backend-*.jar app.jar
RUN chown sicot:sicot app.jar
USER sicot

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
