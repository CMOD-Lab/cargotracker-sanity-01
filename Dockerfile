# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptors first for layer caching
COPY pom.xml .

# Download all dependencies (offline-friendly layer)
RUN mvn dependency:go-offline -B -q

# Copy the full project source
COPY src ./src

# Build the WAR (skip tests – tests run in CI)
RUN mvn clean package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:11-jdk-alpine

LABEL maintainer="Eclipse Cargo Tracker" \
      description="Eclipse Cargo Tracker – Jakarta EE 10 application on Payara Micro" \
      version="3.1-SNAPSHOT"

# ── Environment ──────────────────────────────────────────────
ENV TZ=UTC \
    LANG=en_US.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions" \
    PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/deployments \
    # Redis / ElastiCache
    REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    REDIS_PASSWORD="" \
    # Database
    DB_JDBC_URL="jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database" \
    DB_USER="" \
    DB_PASSWORD="" \
    # Graph traversal service
    GRAPH_TRAVERSAL_URL="http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path"

# ── Create non-root user ──────────────────────────────────────
RUN addgroup -S payara && adduser -S -G payara payara

# ── Download Payara Micro ─────────────────────────────────────
RUN mkdir -p ${PAYARA_HOME} ${DEPLOY_DIR} && \
    wget -q "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar" \
         -O ${PAYARA_HOME}/payara-micro.jar && \
    chown -R payara:payara ${PAYARA_HOME}

# ── Copy application artefacts ────────────────────────────────
COPY --from=builder /workspace/target/cargo-tracker.war ${DEPLOY_DIR}/cargo-tracker.war
COPY post-boot-commands.asadmin ${PAYARA_HOME}/post-boot-commands.asadmin

RUN chown -R payara:payara ${PAYARA_HOME}

# ── Switch to non-root ────────────────────────────────────────
USER payara

WORKDIR ${PAYARA_HOME}

# ── Expose ports ──────────────────────────────────────────────
EXPOSE 8080 8081

# ── Entrypoint ────────────────────────────────────────────────
ENTRYPOINT ["sh", "-c", \
  "exec java $JAVA_OPTS \
    -jar ${PAYARA_HOME}/payara-micro.jar \
    --deploy ${DEPLOY_DIR}/cargo-tracker.war \
    --port 8080 \
    --sslPort 8081 \
    --postbootcommandfile ${PAYARA_HOME}/post-boot-commands.asadmin \
    --noCluster"]
