# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (offline-friendly layer)
RUN mvn dependency:go-offline -B -q

# Copy the full project source
COPY src ./src

# Build the WAR, skip tests
RUN mvn clean package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:11-jdk

LABEL maintainer="cargo-tracker-team" \
      application="cargo-tracker" \
      version="3.1-SNAPSHOT"

# Timezone
ENV TZ=UTC

WORKDIR /opt/payara

# Download Payara Micro
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Add a non-root user
RUN groupadd -r cargotracker && useradd -r -g cargotracker -d /opt/payara -s /sbin/nologin cargotracker

# Copy Payara Micro JAR from Maven local repo populated during build
COPY --from=builder /root/.m2/repository/fish/payara/extras/payara-micro /opt/payara/payara-micro-repo/

# Copy the built WAR
COPY --from=builder /workspace/target/cargo-tracker.war /opt/payara/cargo-tracker.war

# Resolve the actual payara-micro jar (version-agnostic)
RUN PAYARA_JAR=$(find /opt/payara/payara-micro-repo -name "payara-micro-*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1) && \
    cp "$PAYARA_JAR" /opt/payara/payara-micro.jar && \
    chown -R cargotracker:cargotracker /opt/payara

# Application port
EXPOSE 8080

# JVM options for container-aware memory management
ENV JAVA_OPTS="-Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.net.preferIPv4Stack=true \
  -Dfile.encoding=UTF-8"

# Environment variables for external services (override at runtime)
ENV REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database \
    DB_DRIVER_CLASS=org.h2.jdbcx.JdbcDataSource \
    DB_USER="" \
    DB_PASSWORD="" \
    GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

USER cargotracker

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /opt/payara/payara-micro.jar --deploy /opt/payara/cargo-tracker.war --port 8080"]
