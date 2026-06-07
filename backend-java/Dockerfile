# Build stage
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY backend-java/pom.xml .
COPY backend-java/src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /database && chmod 777 /database
COPY frontend /frontend
COPY --from=build /app/target/persona-backend-1.0.0.jar app.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
