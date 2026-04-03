# Build stage
FROM maven:3.9.9-eclipse-temurin-23-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:23-jre-alpine

WORKDIR /app

RUN apk add --no-cache ffmpeg

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]