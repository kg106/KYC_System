# ============================================================
# Stage 1: Build Stage
# ============================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (layer caching for dependencies)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x ./mvnw

# Download dependencies only (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the JAR (skip tests during image build; run them in CI)
RUN ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime Stage
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install Tesseract OCR + English language data
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        libtesseract-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security
RUN groupadd --system kycgroup && \
    useradd --system --gid kycgroup --no-create-home kycuser

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create directories for document uploads and logs
RUN mkdir -p /app/uploads /app/logs && \
    chown -R kycuser:kycgroup /app

# Switch to non-root user
USER kycuser

# Expose application port
EXPOSE 8080

# JVM tuning for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]