# ---
# Stage 1: Build Stage
# ---
# Use Eclipse Temurin JDK 21 as the build environment
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set the working directory inside the container
WORKDIR /workspace

# Copy the Maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Make the Maven wrapper executable (important for Linux-based images)
RUN chmod +x mvnw

# Download all dependencies
# This leverages Docker's layer cache; dependencies are only re-downloaded if pom.xml changes
RUN ./mvnw dependency:go-offline

# Copy the project source code
COPY src ./src

# Clean and package the application (skip tests to speed up the build)
RUN ./mvnw clean package -DskipTests

# ---
# Stage 2: Run Stage
# ---
# Use Eclipse Temurin JRE 21 (a smaller image) as the final environment
FROM eclipse-temurin:21-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Define an argument for the JAR file path
# This default is for 'live-gift' but can be overridden at build time
ARG JAR_FILE=target/live-gift-0.0.1-SNAPSHOT.jar

# Copy the built .jar file from the "builder" stage using the ARG
COPY --from=builder /workspace/${JAR_FILE} app.jar

# Expose the Spring Boot default port 8080
EXPOSE 8080

# Set the command to execute when the container starts
ENTRYPOINT ["java","-jar","/app/app.jar"]

