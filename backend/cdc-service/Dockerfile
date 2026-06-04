FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY backend ./backend

RUN mvn -B -f /workspace/backend/pom.xml -pl cdc-service -am -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 app

COPY --chmod=0555 --from=build /workspace/backend/cdc-service/target/*.jar app.jar

EXPOSE 8084
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
