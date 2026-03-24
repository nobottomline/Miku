# Stage 1: Build
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app
COPY . .
RUN gradle :apps:server:buildFatJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S miku && adduser -S miku -G miku

WORKDIR /app

COPY --from=builder /app/apps/server/build/libs/miku-server.jar ./miku-server.jar

# Create directories
RUN mkdir -p extensions data/prefs data/files logs && \
    chown -R miku:miku /app

USER miku

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseStringDeduplication", \
    "-Xmx512m", \
    "-Xms256m", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "miku-server.jar"]
