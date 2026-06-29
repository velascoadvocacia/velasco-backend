FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests -Dquarkus.package.type=uber-jar

FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /app
COPY --from=builder /app/target/*-runner.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
