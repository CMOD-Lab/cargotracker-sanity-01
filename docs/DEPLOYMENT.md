# Cargo Tracker - Deployment Guide

## Overview

This guide covers building, containerizing, and deploying the **Eclipse Cargo Tracker** application to **AWS EKS (Elastic Kubernetes Service)**.

- **Application**: Eclipse Cargo Tracker v3.1-SNAPSHOT
- **Framework**: Jakarta EE 10 (Payara Server)
- **Java Version**: 11
- **Package Type**: WAR
- **Application Port**: 8080 (HTTP), 8081 (HTTPS)
- **Health Endpoint**: `GET /cargo-tracker/rest/health`

---

## Prerequisites

### Local Development
- **Java 11** (JDK)
- **Maven 3.9+**
- **Docker Desktop** (or Docker Engine)
- **Docker Compose** v2+

### AWS EKS Deployment
- **AWS CLI v2** configured with appropriate IAM permissions
- **kubectl** v1.28+
- **eksctl** (optional, for cluster creation)
- **Helm** v3+ (optional, for AWS Load Balancer Controller)

### Required IAM Permissions
- `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`
- `eks:DescribeCluster`, `eks:ListClusters`
- `ec2:DescribeSubnets`, `ec2:DescribeSecurityGroups`
- `elasticloadbalancing:*` (for ALB Ingress Controller)

---

## Project Structure

```
cargotrackersanity01/
├── Dockerfile                  # Multi-stage Docker build
├── docker-compose.yml          # Local development (app only)
├── .dockerignore               # Docker build exclusions
├── pom.xml                     # Maven build descriptor
├── src/                        # Application source code
├── kubernetes/
│   ├── namespace.yaml          # Kubernetes namespace
│   ├── deployment.yaml         # Application deployment
│   ├── service.yaml            # ClusterIP service
│   └── ingress.yaml            # AWS ALB ingress
├── scripts/
│   ├── build-push.sh           # Linux/macOS build & push
│   ├── build-push.bat          # Windows build & push
│   ├── deploy-image.sh         # Linux/macOS EKS deploy
│   └── deploy-image.bat        # Windows EKS deploy
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development Setup

### 1. Build the Application

```bash
# Build WAR using Maven (default Payara profile)
mvn clean package -DskipTests

# Build with cloud/PostgreSQL profile
mvn clean package -Pcloud \
  -DpostgreSqlJdbcUrl="jdbc:postgresql://localhost:5432/postgres" \
  -DpostgreSqlUsername="cargotracker" \
  -DpostgreSqlPassword="cargotracker"
```

### 2. Build Docker Image Locally

```bash
docker build -t cargo-tracker:latest .
```

### 3. Run with Docker Compose

```bash
# Copy and configure environment variables
cp .env.example .env   # edit as needed

# Start the application container
docker-compose up -d

# View logs
docker-compose logs -f cargo-tracker

# Stop
docker-compose down
```

The application will be available at: `http://localhost:8080/cargo-tracker`

### 4. Environment Variables (docker-compose)

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `postgres` | Database name |
| `DB_USER` | `cargotracker` | Database username |
| `DB_PASSWORD` | `cargotracker` | Database password |
| `REDIS_HOST` | `localhost` | Redis/ElastiCache hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `GRAPH_TRAVERSAL_URL` | `http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path` | Internal routing service URL |

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Choose registry type (AWS ECR or Docker Hub)
2. Enter registry credentials and details
3. Specify an image tag (defaults to `latest`)

### Windows

```cmd
scripts\build-push.bat
```

### Manual ECR Push

```bash
# Authenticate
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Create repository (first time)
aws ecr create-repository --repository-name cargo-tracker --region us-east-1

# Build and push
docker build -t <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest .
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

---

## AWS EKS Deployment

### 1. Prerequisites

#### Install AWS CLI
```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install
aws configure
```

#### Install kubectl
```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/
```

#### Install AWS Load Balancer Controller (required for ALB Ingress)
```bash
# Add Helm repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install controller (replace <CLUSTER_NAME> and <AWS_REGION>)
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<CLUSTER_NAME> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### 2. Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name <YOUR_CLUSTER_NAME>
kubectl cluster-info
```

### 3. Deploy Using Script

#### Linux / macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows
```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region and EKS Cluster Name
- Full Docker Image URI
- Optional environment variable values (DB_HOST, REDIS_HOST, etc.)

### 4. Manual Deployment

```bash
# 1. Apply namespace
kubectl apply -f kubernetes/namespace.yaml

# 2. Update image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|<YOUR_IMAGE_URI>|g' kubernetes/deployment.yaml
sed -i 's|{{DB_HOST}}|<YOUR_DB_HOST>|g' kubernetes/deployment.yaml
sed -i 's|{{DB_PORT}}|5432|g' kubernetes/deployment.yaml
sed -i 's|{{DB_NAME}}|postgres|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_HOST}}|<YOUR_REDIS_HOST>|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_PORT}}|6379|g' kubernetes/deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path|g' kubernetes/deployment.yaml

# 3. Apply manifests
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# 5. Verify
kubectl get pods,svc,ingress -n cargo-tracker
```

### 5. Create Database Secret (Recommended)

```bash
kubectl create secret generic cargo-tracker-db-secret \
  --namespace cargo-tracker \
  --from-literal=username=<DB_USER> \
  --from-literal=password=<DB_PASSWORD>
```

---

## Kubernetes Manifest Descriptions

| File | Description |
|---|---|
| `namespace.yaml` | Creates the `cargo-tracker` namespace |
| `deployment.yaml` | Deploys 2 replicas with liveness/readiness probes on `/cargo-tracker/rest/health` |
| `service.yaml` | ClusterIP service exposing port 80 → 8080 |
| `ingress.yaml` | AWS ALB Ingress with internet-facing scheme and health check on `/cargo-tracker/rest/health` |

---

## Health Checks

The application exposes a custom health endpoint:

```
GET /cargo-tracker/rest/health
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "application": "cargo-tracker",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Kubernetes probes are configured with:
- **Liveness probe**: `initialDelaySeconds: 90` (JVM + Payara startup time)
- **Readiness probe**: `initialDelaySeconds: 60`

---

## Scaling and Management

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment cargo-tracker \
  --namespace cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<NEW_IMAGE_URI> \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback

```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Rollback to specific revision
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker --to-revision=2
```

### View Rollout History

```bash
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <POD_NAME> -n cargo-tracker

# View logs
kubectl logs <POD_NAME> -n cargo-tracker
kubectl logs <POD_NAME> -n cargo-tracker --previous  # crashed pod
```

### Common Issues

| Issue | Cause | Solution |
|---|---|---|
| `ImagePullBackOff` | Wrong image URI or missing ECR permissions | Verify image URI and IAM role |
| `CrashLoopBackOff` | Application startup failure | Check logs; verify DB/Redis connectivity |
| `Pending` pods | Insufficient cluster resources | Scale node group or reduce resource requests |
| Ingress not provisioning | ALB Controller not installed | Install AWS Load Balancer Controller |
| Health check failing | Payara startup takes >90s | Increase `initialDelaySeconds` in deployment.yaml |

### Service Connectivity

```bash
# Port-forward for local testing
kubectl port-forward svc/cargo-tracker-service 8080:80 -n cargo-tracker

# Test health endpoint
curl http://localhost:8080/cargo-tracker/rest/health
```

### Ingress / ALB Issues

```bash
# Check ingress status
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker

# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

---

## Configuration Management

### Environment Variables

All external service connections are configured via environment variables in `deployment.yaml`. Update the values before applying:

| Variable | Description |
|---|---|
| `DB_HOST` | PostgreSQL/RDS hostname |
| `DB_PORT` | Database port (default: 5432) |
| `DB_NAME` | Database name |
| `REDIS_HOST` | Amazon ElastiCache Redis endpoint |
| `REDIS_PORT` | Redis port (default: 6379) |
| `GRAPH_TRAVERSAL_URL` | Internal graph traversal REST service URL |
| `JAVA_OPTS` | JVM options (heap size, GC settings) |

### JVM Tuning

Adjust `JAVA_OPTS` in `deployment.yaml` based on container memory limits:

```yaml
- name: JAVA_OPTS
  value: "-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

For 1Gi memory limit, `-Xmx512m` is appropriate (leaving headroom for Payara overhead).

---

## Security Considerations

1. **Non-root container**: The Dockerfile creates a `payara` user; the container runs as non-root (UID 1000).
2. **Secrets management**: Use Kubernetes Secrets (or AWS Secrets Manager with External Secrets Operator) for database credentials.
3. **Network policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **IRSA (IAM Roles for Service Accounts)**: Use IRSA for fine-grained AWS permissions instead of node-level IAM roles.
6. **TLS**: Configure ACM certificate ARN in the ingress annotations for HTTPS termination at the ALB.

### Add TLS to Ingress

```yaml
annotations:
  alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<REGION>:<ACCOUNT>:certificate/<CERT_ID>
  alb.ingress.kubernetes.io/ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06
```

---

## Jakarta EE / Payara-Specific Notes

- **Payara Server** is the default Jakarta EE runtime for this application.
- The application is deployed as a WAR at context root `/cargo-tracker`.
- **JMS queues** (CargoHandledQueue, MisdirectedCargoQueue, etc.) are configured in `server.xml` for OpenLiberty or via Payara admin for Payara.
- **H2 database** is used by default (embedded); switch to PostgreSQL using the `cloud` Maven profile.
- **Redis (Jedis)** is used for Amazon ElastiCache integration; configure `REDIS_HOST` and `REDIS_PORT`.
- Payara startup can take 60–120 seconds; the liveness probe `initialDelaySeconds` is set to 90 seconds accordingly.
- For production, consider using **Payara Micro** for a lighter-weight deployment.

---

## Support

- **Project Repository**: https://github.com/eclipse-ee4j/cargotracker
- **Issue Tracker**: https://github.com/eclipse-ee4j/cargotracker/issues
- **Payara Documentation**: https://docs.payara.fish/
- **AWS EKS Documentation**: https://docs.aws.amazon.com/eks/
