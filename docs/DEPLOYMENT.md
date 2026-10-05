# Eclipse Cargo Tracker – Deployment Guide (AWS EKS)

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Building and Pushing the Docker Image](#building-and-pushing-the-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration & Environment Variables](#configuration--environment-variables)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

**Eclipse Cargo Tracker** is a Jakarta EE 10 reference application demonstrating Domain-Driven Design (DDD) patterns. It is packaged as a WAR file and runs on **Payara Micro** inside a Docker container.

| Property | Value |
|---|---|
| Java Version | 11 |
| Build Tool | Maven 3.9.x |
| Packaging | WAR |
| Runtime | Payara Micro 6.2025.3 |
| Base Image (runtime) | `eclipse-temurin:11-jdk-alpine` |
| Application Port | 8080 (HTTP) |
| Management Port | 8081 (HTTPS) |
| Context Root | `/cargo-tracker` |

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
| AWS CLI | v2, configured with appropriate IAM permissions |
| kubectl | Compatible with your EKS cluster version |
| eksctl | Optional – for cluster creation |
| Docker | For building and pushing images |

### Required IAM Permissions
- `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:CreateRepository`
- `eks:DescribeCluster`, `eks:UpdateKubeconfig`
- `elasticache:DescribeCacheClusters` (if using Amazon ElastiCache)

---

## Project Structure

```
Comp check/
├── Dockerfile                  # Multi-stage build (Maven builder + Payara Micro runtime)
├── .dockerignore               # Excludes build artefacts and wrapper scripts
├── docker-compose.yml          # Local development compose file (app only)
├── pom.xml                     # Maven build descriptor
├── post-boot-commands.asadmin  # Payara Micro post-boot configuration
├── src/
│   ├── main/
│   │   ├── java/               # Application source code
│   │   ├── liberty/config/     # OpenLiberty server configuration
│   │   ├── resources/          # Persistence, batch job descriptors
│   │   └── webapp/             # JSF pages, WEB-INF descriptors
│   └── test/                   # Arquillian integration tests
├── kubernetes/
│   ├── namespace.yaml          # Kubernetes namespace
│   ├── deployment.yaml         # Application deployment (2 replicas)
│   ├── service.yaml            # ClusterIP service
│   └── ingress.yaml            # AWS ALB Ingress
├── scripts/
│   ├── build-push.sh           # Linux/macOS: build & push image
│   ├── build-push.bat          # Windows: build & push image
│   ├── deploy-image.sh         # Linux/macOS: deploy to EKS
│   └── deploy-image.bat        # Windows: deploy to EKS
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Build the application locally (optional – Docker will build it too)
```bash
mvn clean package -DskipTests
```

### 2. Start the application
```bash
docker-compose up --build
```

### 3. Access the application
- **Application**: http://localhost:8080/cargo-tracker
- **REST API**: http://localhost:8080/cargo-tracker/rest/

### 4. Environment variable overrides
Create a `.env` file in the project root:
```env
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-auth-token
DB_JDBC_URL=jdbc:postgresql://your-rds-host:5432/cargotracker
DB_USER=cargotracker
DB_PASSWORD=secret
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

### 5. Stop the application
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
1. Select a registry (AWS ECR or Docker Hub)
2. Enter registry credentials / details
3. Enter an image tag (defaults to `latest`)

The script automatically:
- Sanitises the image name to lowercase with hyphens
- Creates the ECR repository if it does not exist (ECR only)
- Builds the image from the repository root
- Pushes the image to the selected registry

---

## AWS EKS Deployment

### Step 1 – Configure AWS CLI
```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### Step 2 – Create or connect to an EKS cluster
```bash
# Create a new cluster (optional)
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4

# Or update kubeconfig for an existing cluster
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
```

### Step 3 – Install the AWS Load Balancer Controller (required for ALB Ingress)
```bash
# Add the EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=cargo-tracker-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4 – Create Kubernetes Secrets (optional but recommended)
```bash
kubectl create namespace cargo-tracker

kubectl create secret generic cargo-tracker-secrets \
  --namespace cargo-tracker \
  --from-literal=redis-password=YOUR_REDIS_AUTH_TOKEN \
  --from-literal=db-user=YOUR_DB_USER \
  --from-literal=db-password=YOUR_DB_PASSWORD
```

### Step 5 – Run the deployment script
```bash
# Linux / macOS
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh

# Windows
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region
- EKS Cluster Name
- Full Docker image URI (e.g. `123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest`)
- Optional: REDIS_HOST, REDIS_PORT, DB_JDBC_URL, GRAPH_TRAVERSAL_URL

### Step 6 – Verify the deployment
```bash
kubectl get pods -n cargo-tracker
kubectl get svc -n cargo-tracker
kubectl get ingress -n cargo-tracker

# View logs
kubectl logs -l app=cargo-tracker -n cargo-tracker --tail=100 -f
```

### Step 7 – Access the application
```bash
# Get the ALB hostname
kubectl get ingress cargo-tracker-ingress -n cargo-tracker \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```
Navigate to: `http://<ALB_HOSTNAME>/cargo-tracker`

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (for high availability)
- **Image**: Populated at deploy time via `{{IMAGE_URI}}` placeholder
- **Probes**: TCP socket probes on port 8080 (JVM startup can take 60–90 s)
- **Resources**: requests `250m CPU / 512Mi RAM`, limits `500m CPU / 1Gi RAM`
- **Volumes**: `emptyDir` for Payara data directory

### service.yaml
- **Type**: ClusterIP (internal only; traffic enters via Ingress)
- **Port mapping**: 80 → 8080 (HTTP), 443 → 8081 (HTTPS)

### ingress.yaml
- **Controller**: AWS ALB (via `kubernetes.io/ingress.class: alb`)
- **Scheme**: `internet-facing`
- **Sticky sessions**: Enabled (important for JSF view state)
- **Host**: `cargo-tracker.example.com` – update to your actual domain

---

## Configuration & Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JAVA_OPTS` | `-Xms256m -Xmx512m ...` | JVM startup options |
| `TZ` | `UTC` | Container timezone |
| `REDIS_HOST` | `localhost` | Amazon ElastiCache primary endpoint |
| `REDIS_PORT` | `6379` | ElastiCache port |
| `REDIS_PASSWORD` | _(empty)_ | ElastiCache auth token (optional) |
| `DB_JDBC_URL` | H2 file URL | JDBC connection URL |
| `DB_USER` | _(empty)_ | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `GRAPH_TRAVERSAL_URL` | localhost URL | Internal graph traversal REST endpoint |

---

## Scaling and Management

### Manual scaling
```bash
kubectl scale deployment cargo-tracker --replicas=4 -n cargo-tracker
```

### Horizontal Pod Autoscaler
```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=8 \
  -n cargo-tracker
```

### Rolling update
```bash
# Update the image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<NEW_IMAGE_URI> \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback
```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Rollback to a specific revision
kubectl rollout undo deployment/cargo-tracker --to-revision=2 -n cargo-tracker
```

---

## Troubleshooting

### Pods not starting
```bash
# Describe the pod for events
kubectl describe pod -l app=cargo-tracker -n cargo-tracker

# Check logs
kubectl logs -l app=cargo-tracker -n cargo-tracker --previous
```

**Common causes:**
- Image pull errors → verify ECR permissions and image URI
- OOMKilled → increase memory limits in `deployment.yaml`
- CrashLoopBackOff → check application logs for startup errors

### Readiness probe failing
Payara Micro can take 60–90 seconds to start. The readiness probe has `initialDelaySeconds: 60`. If pods are still failing:
```bash
# Temporarily increase the delay
kubectl patch deployment cargo-tracker -n cargo-tracker \
  --type='json' \
  -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/initialDelaySeconds","value":120}]'
```

### Ingress not getting an address
```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Verify the ingress class annotation
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
```

### Redis connection errors
- Verify `REDIS_HOST` points to the ElastiCache primary endpoint
- Ensure the EKS node security group allows outbound traffic to the ElastiCache security group on port 6379
- Check `REDIS_PASSWORD` is set correctly if auth is enabled

### Database connection errors
- Verify `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD` are correct
- Ensure the EKS node security group allows outbound traffic to the RDS/database security group

---

## Security Considerations

1. **Non-root container**: The application runs as the `payara` user (UID non-root).
2. **Secrets management**: Use Kubernetes Secrets (or AWS Secrets Manager with External Secrets Operator) for `REDIS_PASSWORD`, `DB_USER`, `DB_PASSWORD`.
3. **Network policies**: Restrict pod-to-pod traffic with Kubernetes NetworkPolicy.
4. **Image scanning**: Enable ECR image scanning on push.
5. **HTTPS**: Configure an ACM certificate ARN in the ingress annotations:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
   ```
6. **RBAC**: Apply least-privilege IAM roles to EKS node groups.
7. **Resource limits**: Always set both `requests` and `limits` to prevent noisy-neighbour issues.

---

## Technology-Specific Notes

### Jakarta EE / Payara Micro
- The application uses **Jakarta EE 10** APIs (CDI 4.0, JPA 3.1, JAX-RS 3.1, JSF 4.0, JMS 3.1, Batch 2.1).
- **Payara Micro** is downloaded at image build time from Maven Central. Pin the version via the `PAYARA_VERSION` build arg if needed.
- The `post-boot-commands.asadmin` file is executed by Payara Micro after boot for additional server configuration.

### JVM Tuning
- `-XX:+UseContainerSupport` enables JVM container awareness (reads cgroup limits).
- `-XX:MaxRAMPercentage=75.0` caps heap at 75% of container memory limit.
- Adjust `-Xms` / `-Xmx` in `JAVA_OPTS` if you change the memory limits in `deployment.yaml`.

### JSF Session Affinity
- JSF stores view state server-side by default. The ALB Ingress is configured with sticky sessions (`stickiness.lb_cookie.duration_seconds=86400`) to route a user's requests to the same pod.
- For a fully stateless deployment, configure JSF client-side state saving in `web.xml`:
  ```xml
  <context-param>
    <param-name>jakarta.faces.STATE_SAVING_METHOD</param-name>
    <param-value>client</param-value>
  </context-param>
  ```

### Amazon ElastiCache (Redis)
- The `RedisConfig` class reads `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` from environment variables.
- Ensure the ElastiCache cluster is in the same VPC as the EKS cluster.
- Use a VPC security group rule to allow inbound TCP 6379 from the EKS node security group.

### Database
- Default profile uses **H2** (in-memory/file). For production, use the `cloud` Maven profile with PostgreSQL:
  ```bash
  mvn clean package -Pcloud \
    -DpostgreSqlJdbcUrl="jdbc:postgresql://host:5432/cargotracker" \
    -DpostgreSqlUsername="user" \
    -DpostgreSqlPassword="pass"
  ```
- Set `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD` environment variables in the Kubernetes deployment.
