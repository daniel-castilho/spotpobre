FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
ARG JAR_FILE=target/spotpobre-*.jar
COPY --from=build /app/${JAR_FILE} app.jar
USER root
ENTRYPOINT ["java","-jar","/app.jar"]