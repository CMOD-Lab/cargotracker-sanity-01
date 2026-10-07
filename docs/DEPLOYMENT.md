# Cargo Tracker – AWS EKS Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Building and Pushing the Docker Image](#building-and-pushing-the-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration Management](#configuration-management)
9. [Scaling and Rolling Updates](#scaling-and-rolling-updates)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

**Eclipse Cargo Tracker** is a Jakarta EE 10 web application demonstrating Domain-Driven Design (DDD) patterns. It is packaged as a WAR file and runs on **Payara Micro** inside a Docker container.

| Property | Value |
|---|---|
| Java Version | 11 |
| Build Tool | Maven 3.9.x |
| Packaging | WAR |
| Runtime | Payara Micro 6.x |
| Application Port | 8080 |
| Health Endpoint | `/cargo-tracker/rest/health` |
| Base Image (runtime) | `eclipse-temurin:11-jdk` |

---

## Prerequisites

### Local Development
| Tool | Minimum Version |
|---|---|
| Docker | 24.x |
| Docker Compose | 2.x |
| Java JDK | 11 |
| Maven | 3.9.x |

### AWS EKS Deployment
| Tool | Notes |
|---|---|
| AWS CLI v2 | Configured with IAM credentials |
| kubectl | Matching your EKS cluster version |
| eksctl (optional) | For cluster creation |
| IAM permissions | `eks:*`, `ecr:*`, `ec2:*`, `iam:PassRole` |

---

## Project Structure

```
cargotracker-sanity/
├── Dockerfile                  # Multi-stage build (Maven builder + eclipse-temurin runtime)
├── docker-compose.yml          # Single-service local development stack
├── .dockerignore               # Excludes build artifacts and wrapper scripts
├── pom.xml                     # Maven build descriptor (Java 11, WAR packaging)
├── src/
│   └── main/
│       ├── java/               # Application source code
│       ├── liberty/config/     # OpenLiberty server.xml (reference config)
│       ├── resources/          # persistence.xml, batch jobs
│       └── webapp/             # JSF/Faces pages, WEB-INF
├── kubernetes/
│   ├── namespace.yaml          # Namespace: cargo-tracker
│   ├── deployment.yaml         # 2-replica Deployment with health probes
│   ├── service.yaml            # ClusterIP Service (port 80 → 8080)
│   └── ingress.yaml            # AWS ALB Ingress with sticky sessions
├── scripts/
│   ├── build-push.sh           # Linux/macOS: build & push to ECR or Docker Hub
│   ├── build-push.bat          # Windows: build & push to ECR or Docker Hub
│   ├── deploy-image.sh         # Linux/macOS: deploy to EKS
│   └── deploy-image.bat        # Windows: deploy to EKS
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Build the application locally (optional verification)
```bash
mvn clean package -DskipTests
```

### 2. Start the application container
```bash
docker-compose up --build
```

The application will be available at: **http://localhost:8080/cargo-tracker**

### 3. Environment variable overrides
Create a `.env` file in the project root to override defaults:
```env
REDIS_HOST=my-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
DB_JDBC_URL=jdbc:postgresql://my-rds-host:5432/cargotracker
DB_DRIVER_CLASS=org.postgresql.ds.PGPoolingDataSource
DB_USER=cargotracker
DB_PASSWORD=secret
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

### 4. Stop the stack
```bash
docker-compose down
```

---

## Building and Pushing the Docker Image

### Linux / macOS
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows
```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Choose a registry (AWS ECR or Docker Hub)
2. Enter registry credentials / AWS account details
3. Specify an image tag (defaults to `latest`)

The script automatically:
- Sanitizes the image name to lowercase with hyphens
- Creates the ECR repository if it does not exist (ECR only)
- Builds the Docker image from the project root
- Pushes the image to the selected registry

---

## AWS EKS Deployment

### Step 1 – Create or connect to an EKS cluster

**Create a new cluster (eksctl):**
```bash
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

**Connect to an existing cluster:**
```bash
aws eks update-kubeconfig --region us-east-1 --name <cluster-name>
kubectl cluster-info
```

### Step 2 – Install the AWS Load Balancer Controller

The ingress manifest uses the AWS ALB Ingress Controller. Install it if not already present:
```bash
# Add the EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<cluster-name> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 3 – Build and push the image
```bash
./scripts/build-push.sh
# Note the full image URI output by the script
```

### Step 4 – Deploy to EKS

**Linux / macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS region and EKS cluster name
- Full Docker image URI (from Step 3)
- Runtime environment variables (Redis, database, graph traversal URL)

### Step 5 – Verify the deployment
```bash
kubectl get pods -n cargo-tracker
kubectl get svc  -n cargo-tracker
kubectl get ingress -n cargo-tracker

# Check pod logs
kubectl logs -l app=cargo-tracker -n cargo-tracker --tail=100

# Get the ALB hostname
kubectl get ingress cargo-tracker-ingress -n cargo-tracker \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

The application will be accessible at:
```
http://<alb-hostname>/cargo-tracker
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all resources.

### deployment.yaml
| Field | Value |
|---|---|
| Replicas | 2 |
| Image | `{{IMAGE_URI}}` (replaced at deploy time) |
| Container port | 8080 |
| CPU request/limit | 250m / 500m |
| Memory request/limit | 512Mi / 1Gi |
| Liveness probe | `GET /cargo-tracker/rest/health` (delay 90s, period 30s) |
| Readiness probe | `GET /cargo-tracker/rest/health` (delay 60s, period 15s) |

**Environment variable placeholders** (replaced by `deploy-image.sh`):

| Placeholder | Description |
|---|---|
| `{{IMAGE_URI}}` | Full Docker image URI with tag |
| `{{REDIS_HOST}}` | Amazon ElastiCache primary endpoint |
| `{{REDIS_PORT}}` | ElastiCache port (default 6379) |
| `{{DB_JDBC_URL}}` | JDBC connection string |
| `{{DB_DRIVER_CLASS}}` | JDBC driver class name |
| `{{DB_USER}}` | Database username |
| `{{DB_PASSWORD}}` | Database password |
| `{{GRAPH_TRAVERSAL_URL}}` | Internal graph traversal REST URL |

### service.yaml
ClusterIP service exposing port 80 → container port 8080.

### ingress.yaml
AWS ALB Ingress with:
- Internet-facing scheme
- IP target type
- Health check on `/cargo-tracker/rest/health`
- Sticky sessions (2-day cookie duration, matching original nginx config)

---

## Configuration Management

### Using Kubernetes Secrets (recommended for production)
```bash
kubectl create secret generic cargo-tracker-secrets \
  --from-literal=DB_PASSWORD=<password> \
  --from-literal=REDIS_AUTH_TOKEN=<token> \
  -n cargo-tracker
```

Reference the secret in `deployment.yaml`:
```yaml
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: cargo-tracker-secrets
        key: DB_PASSWORD
```

### Using AWS Secrets Manager with External Secrets Operator
```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace
```

---

## Scaling and Rolling Updates

### Manual scaling
```bash
kubectl scale deployment cargo-tracker --replicas=4 -n cargo-tracker
```

### Horizontal Pod Autoscaler
```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker
```

### Rolling update (new image)
```bash
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<new-image-uri> \
  -n cargo-tracker

kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback
```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

---

## Troubleshooting

### Pod not starting
```bash
kubectl describe pod -l app=cargo-tracker -n cargo-tracker
kubectl logs -l app=cargo-tracker -n cargo-tracker --previous
```

**Common causes:**
- `ImagePullBackOff` – ECR credentials not configured; ensure the node IAM role has `ecr:GetAuthorizationToken` and `ecr:BatchGetImage`.
- `CrashLoopBackOff` – Application startup failure; check logs for database or Redis connection errors.
- `OOMKilled` – Increase memory limit in `deployment.yaml`.

### Health probe failures
The liveness probe has a 90-second initial delay to allow Payara Micro to start. If pods are being killed prematurely:
```bash
# Increase initialDelaySeconds in deployment.yaml
kubectl edit deployment cargo-tracker -n cargo-tracker
```

### Database connectivity
Ensure the EKS worker nodes can reach the RDS/PostgreSQL endpoint:
```bash
kubectl run -it --rm debug --image=busybox --restart=Never -n cargo-tracker -- \
  nc -zv <db-host> 5432
```

### Redis connectivity
```bash
kubectl run -it --rm debug --image=redis:alpine --restart=Never -n cargo-tracker -- \
  redis-cli -h <redis-host> -p 6379 ping
```

### Ingress / ALB not provisioning
```bash
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

---

## Security Considerations

1. **Non-root container**: The Dockerfile creates a `cargotracker` user; the application runs as non-root.
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager – never hardcode credentials.
3. **Network policies**: Restrict pod-to-pod traffic with Kubernetes NetworkPolicy.
4. **Image scanning**: Enable ECR image scanning on push to detect vulnerabilities.
5. **IRSA (IAM Roles for Service Accounts)**: Use IRSA instead of node-level IAM roles for fine-grained AWS permissions.
6. **TLS termination**: Configure HTTPS on the ALB by adding an ACM certificate ARN annotation:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<region>:<account>:certificate/<id>
   alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS": 443}]'
   ```

---

## Technology-Specific Notes

### Jakarta EE 10 / Payara Micro
- The application uses Jakarta EE 10 APIs (CDI 4, JPA 3.1, JAX-RS 3.1, Faces 4.0, JMS 3.1).
- Payara Micro is embedded as a fat-JAR launcher; no separate application server installation is needed.
- The WAR context root is `/cargo-tracker` (set in `glassfish-web.xml`).

### JVM Tuning
The following JVM flags are set via `JAVA_OPTS`:
```
-Xms256m -Xmx512m
-XX:+UseContainerSupport        # Respect cgroup memory limits
-XX:MaxRAMPercentage=75.0       # Use up to 75% of container memory for heap
-XX:+UseG1GC                    # G1 garbage collector
-Djava.net.preferIPv4Stack=true
-Dfile.encoding=UTF-8
```
Adjust `-Xmx` and the Kubernetes memory limit together (keep `-Xmx` ≤ 75% of the limit).

### Redis / Amazon ElastiCache
The application uses Redis (via Jedis) for distributed caching across replicas. Set `REDIS_HOST` to your ElastiCache primary endpoint. For ElastiCache with TLS/AUTH, update `RedisCache.java` to pass the auth token.

### Database
- **Development**: H2 file-based database (default, no external dependency).
- **Production**: PostgreSQL via the `cloud` Maven profile. Pass `DB_JDBC_URL`, `DB_DRIVER_CLASS`, `DB_USER`, and `DB_PASSWORD` as environment variables.

### Graph Traversal Service
The `GRAPH_TRAVERSAL_URL` environment variable points to the internal REST endpoint used for route calculation. In a single-pod deployment this is `http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path`. In a multi-pod deployment, point it to the Kubernetes service DNS name:
```
http://cargo-tracker-service.cargo-tracker.svc.cluster.local/cargo-tracker/rest/graph-traversal/shortest-path
```
