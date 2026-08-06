# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first to cache them
RUN mvn dependency:go-offline

COPY src ./src
# Compile the application skipping tests to speed up the build
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/comfyuicompanion.jar app.jar

# Set volume for external models mapping (so we don't copy huge models into the image)
VOLUME /models

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
