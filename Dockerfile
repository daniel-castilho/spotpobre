# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot jar ---
# Base images pinned by digest (supply chain, S3). Bump deliberately: verify the digest,
# rebuild, re-scan, then update this file.
FROM maven:3.9.8-eclipse-temurin-21@sha256:6847cbbf21e159f97819f18336cf6bbc24a89f9eb6439317520d93f904e9a916 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# --- Runtime stage: minimal JRE, non-root, exec-form entrypoint ---
FROM eclipse-temurin:21-jre-jammy@sha256:9af01c30e85b5a9b15fe249a84fb6dcb19e06ee84fbf23ea2d116b0e195c2ce9
LABEL org.opencontainers.image.title="spotpobre-api" \
      org.opencontainers.image.description="Spotpobre music streaming backend" \
      org.opencontainers.image.source="https://github.com/anomalyco/spotpobre-api"

# Stable, non-root runtime user (UID/GID 10001). The app must never run as UID 0.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app \
    && mkdir -p /tmp && chown 10001:10001 /tmp

ARG JAR_FILE=target/spotpobre-*.jar
COPY --from=build /app/${JAR_FILE} /app/app.jar
RUN chown 10001:10001 /app/app.jar

USER 10001:10001
WORKDIR /app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]