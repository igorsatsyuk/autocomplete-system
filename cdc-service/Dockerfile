FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY common ./common
COPY cdc-service ./cdc-service

RUN mvn -B -f /workspace/common/pom.xml -DskipTests install \
	&& mvn -B -f /workspace/cdc-service/pom.xml -DskipTests package spring-boot:repackage

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/cdc-service/target/*.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

