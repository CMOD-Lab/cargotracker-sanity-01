# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy Maven build descriptor first for dependency caching
COPY pom.xml .

# Download all dependencies (cache layer)
RUN mvn dependency:go-offline -B -q

# Copy the full project source
COPY src ./src
COPY post-boot-commands.asadmin .

# Build the WAR (default payara profile, skip tests)
RUN mvn clean package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM mcr.microsoft.com/openjdk/jdk:11-ubuntu

LABEL maintainer="cargo-tracker-team" \
      application="cargo-tracker" \
      version="3.1-SNAPSHOT"

# Environment variables
ENV PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/glassfish/domains/domain1/autodeploy \
    TZ=UTC \
    LANG=en_US.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"

# Install required packages
RUN apt-get update && \
    apt-get install -y --no-install-recommends unzip && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r payara && useradd -r -g payara -d ${PAYARA_HOME} -s /bin/bash payara

# Download and install Payara Server
RUN mkdir -p ${PAYARA_HOME} && \
    apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    wget -q "https://nexus.payara.fish/repository/payara-community/fish/payara/distributions/payara/${PAYARA_VERSION}/payara-${PAYARA_VERSION}.zip" \
         -O /tmp/payara.zip && \
    unzip -q /tmp/payara.zip -d /opt && \
    mv /opt/payara6 ${PAYARA_HOME} || true && \
    rm -f /tmp/payara.zip && \
    apt-get remove -y wget && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Copy the built WAR from builder stage
COPY --from=builder /workspace/target/cargo-tracker.war ${DEPLOY_DIR}/cargo-tracker.war

# Set ownership
RUN chown -R payara:payara ${PAYARA_HOME}

# Switch to non-root user
USER payara

WORKDIR ${PAYARA_HOME}

# Expose application and admin ports
EXPOSE 8080 8081 4848

# Start Payara Server
CMD ["bin/startserv"]
