# ---- Stage 1: Build the Spring Boot JAR with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build

# Set working directory inside the container
WORKDIR /app

# Copy the Maven descriptor first (this lets Docker cache dependencies better)
COPY pom.xml .

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN mvn -q -B dependency:go-offline

# Now copy the actual source code
COPY src ./src

# Build the Spring Boot application (skip tests for faster image builds)
RUN mvn -q -B clean package -DskipTests

# ---- Stage 2: Runtime image (JRE + fat JAR) ----
FROM eclipse-temurin:21-jre

# Set working directory where the app will live
WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port your Spring Boot app runs on
EXPOSE 8080

# Optional: extra JVM flags can be passed via JAVA_OPTS
ENV JAVA_OPTS=""

# Entry point: run the Spring Boot app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
