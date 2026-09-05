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

# Optimized JVM options for low-memory environment (Render free tier 512MB).
# Xmx/MaxMetaspaceSize alone used to add up to the full 512MB, leaving no
# room for thread stacks, JIT code cache, and direct buffers - those are
# capped explicitly below so total RSS actually stays under the container
# limit instead of relying on unbounded defaults (Xss defaults to 1m/thread,
# ReservedCodeCacheSize and MaxDirectMemorySize default to tens of MB each).
# ReservedCodeCacheSize is trimmed from the JVM's usual default - with
# TieredStopAtLevel=1 only the C1 compiler tier ever runs, which needs far
# less code cache than full tiered (C1+C2) compilation.
#
# MaxMetaspaceSize is the big one: production logs showed
# "Terminating due to java.lang.OutOfMemoryError: Metaspace" a few seconds
# after the FIRST login on a freshly booted instance, not at startup itself -
# that's when Spring Security/AOP first generates CGLIB proxy classes for
# @Transactional/@PreAuthorize-annotated beans (lazy, on first invocation),
# loading a burst of classes well past the previous 112m cap. Raised to 224m
# and Xmx trimmed to compensate, keeping the same total ceiling (~464m of
# 512m) rather than growing the container's overall footprint.
ENV JAVA_OPTS="-Xmx192m \
  -Xms128m \
  -Xss256k \
  -XX:MaxMetaspaceSize=224m \
  -XX:ReservedCodeCacheSize=16m \
  -XX:MaxDirectMemorySize=32m \
  -XX:+UseSerialGC \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=1 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.backgroundpreinitializer.ignore=true"

# Run the application with layered JAR
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
