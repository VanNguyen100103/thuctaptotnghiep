# Multi-stage Dockerfile for Spring Boot E-commerce Application
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy only pom.xml first for better dependency caching
COPY pom.xml ./

# Download dependencies (cached layer) - this will be cached unless pom.xml changes
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds, run tests in CI/CD)
RUN mvn clean package -DskipTests -B

# Stage 2: Extract layers for better caching
FROM eclipse-temurin:17-jre-alpine AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# Extract layers to optimize image size and startup time
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Create the optimized runtime image
FROM eclipse-temurin:17-jre-alpine

# Install curl and cleanup in single layer to reduce image size
RUN apk add --no-cache curl && \
    addgroup -S spring && \
    adduser -S spring -G spring

WORKDIR /app

# Copy extracted layers (dependencies change less frequently, better caching)
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

# Create log directory
RUN mkdir -p /var/log/ecommerce && chown -R spring:spring /var/log/ecommerce

# Switch to non-root user
USER spring:spring

# Expose port 8081
EXPOSE 8081

# Optimized JVM options for low-memory environment (Render free tier 512MB)
ENV JAVA_OPTS="-Xmx384m \
  -Xms192m \
  -XX:MaxMetaspaceSize=128m \
  -XX:+UseSerialGC \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=1 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.backgroundpreinitializer.ignore=true"

# Run the application with layered JAR
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
