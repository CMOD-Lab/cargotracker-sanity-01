# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (offline-friendly layer)
RUN mvn dependency:go-offline -B -P cloud

# Copy full project source
COPY src ./src

# Build the WAR (cloud profile includes PostgreSQL JDBC driver)
RUN mvn clean package -DskipTests -P cloud \
    -DpostgreSqlJdbcUrl="jdbc:postgresql://localhost:5432/postgres" \
    -DpostgreSqlUsername="postgres" \
    -DpostgreSqlPassword="postgres"

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:11-jre

LABEL maintainer="Eclipse Cargo Tracker" \
      description="Eclipse Cargo Tracker - Jakarta EE application on Payara Micro" \
      version="3.1-SNAPSHOT"

# Environment
ENV PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/deployments \
    TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions -Dfile.encoding=UTF-8 -Duser.timezone=UTC"

# Create non-root user
RUN groupadd -r payara && useradd -r -g payara -d ${PAYARA_HOME} -s /bin/bash payara

# Create required directories
RUN mkdir -p ${PAYARA_HOME} ${DEPLOY_DIR} /opt/payara/cargo-tracker-data && \
    chown -R payara:payara ${PAYARA_HOME}

# Download Payara Micro
RUN apt-get update && apt-get install -y --no-install-recommends wget && \
    wget -q "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar" \
         -O ${PAYARA_HOME}/payara-micro.jar && \
    apt-get remove -y wget && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Copy built artifacts from builder stage
COPY --from=builder /workspace/target/cargo-tracker.war ${DEPLOY_DIR}/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar ${PAYARA_HOME}/postgresql.jar

# Copy Payara post-boot commands
COPY post-boot-commands.asadmin ${PAYARA_HOME}/config/post-boot-commands.asadmin

# Set ownership
RUN chown -R payara:payara ${PAYARA_HOME}

# Switch to non-root user
USER payara

WORKDIR ${PAYARA_HOME}

# Expose application port
EXPOSE 8080

# Start Payara Micro
ENTRYPOINT ["sh", "-c", \
  "java ${JAVA_OPTS} \
   -jar ${PAYARA_HOME}/payara-micro.jar \
   --addLibs ${PAYARA_HOME}/postgresql.jar \
   --postbootcommandfile ${PAYARA_HOME}/config/post-boot-commands.asadmin \
   --deploy ${DEPLOY_DIR}/cargo-tracker.war \
   --port 8080 \
   --nocluster"]
