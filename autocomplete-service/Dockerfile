FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY common ./common
COPY autocomplete-service ./autocomplete-service

RUN mvn -B -f /workspace/common/pom.xml -DskipTests install \
	&& mvn -B -f /workspace/autocomplete-service/pom.xml -DskipTests package spring-boot:repackage

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 app

COPY --chmod=0555 --from=build /workspace/autocomplete-service/target/*.jar app.jar

EXPOSE 8081
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

