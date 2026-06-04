FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY backend ./backend

RUN mvn -B -f /workspace/backend/pom.xml -pl autocomplete-service -am -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 app

COPY --chmod=0555 --from=build /workspace/backend/autocomplete-service/target/*.jar app.jar

EXPOSE 8081
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
