# Eclipse Cargo Tracker — Deployment Guide

## Overview

This guide covers building, containerizing, and deploying the **Eclipse Cargo Tracker** application to **AWS EKS (Elastic Kubernetes Service)**.

- **Application**: Eclipse Cargo Tracker v3.1-SNAPSHOT
- **Framework**: Jakarta EE 10 on Payara Micro
- **Java Version**: 11
- **Build Tool**: Maven
- **Packaging**: WAR
- **Application Port**: 8080
- **Target Platform**: AWS EKS

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS EKS Prerequisites](#aws-eks-prerequisites)
6. [EKS Cluster Setup](#eks-cluster-setup)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [Configuration Management](#configuration-management)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Jakarta EE Specific Notes](#jakarta-ee-specific-notes)

---

## Prerequisites

### Local Development
- **Docker** 20.10+ and **Docker Compose** v2+
- **Java 11** (Eclipse Temurin recommended)
- **Maven 3.9+**
- **Git**

### AWS EKS Deployment
- **AWS CLI** v2+ (configured with appropriate IAM permissions)
- **kubectl** v1.28+
- **eksctl** v0.170+ (for cluster creation)
- **Helm** v3+ (optional, for AWS Load Balancer Controller)

### Required IAM Permissions
- `eks:*` — EKS cluster management
- `ecr:*` — ECR repository and image management
- `ec2:*` — VPC, subnets, security groups
- `iam:*` — Service accounts and roles
- `elasticloadbalancing:*` — Load balancer management

---

## Project Structure

```
cargo-tracker/
├── Dockerfile                    # Multi-stage build (Maven builder + eclipse-temurin:11-jre runtime)
├── docker-compose.yml            # Local development (application only)
├── .dockerignore                 # Excludes build artifacts and wrapper files
├── pom.xml                       # Maven build descriptor (Java 11, WAR packaging)
├── post-boot-commands.asadmin    # Payara Micro post-boot configuration
├── src/
│   ├── main/
│   │   ├── java/                 # Application source code
│   │   ├── resources/            # persistence.xml, batch jobs
│   │   └── webapp/               # JSF/Faces web resources, WEB-INF
│   └── test/                     # Arquillian integration tests
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes namespace
│   ├── deployment.yaml           # Application deployment (2 replicas)
│   ├── service.yaml              # ClusterIP service (port 80 → 8080)
│   └── ingress.yaml              # AWS ALB Ingress
├── scripts/
│   ├── build-push.sh             # Linux/macOS: build and push to ECR or Docker Hub
│   ├── build-push.bat            # Windows: build and push to ECR or Docker Hub
│   ├── deploy-image.sh           # Linux/macOS: deploy to AWS EKS
│   └── deploy-image.bat          # Windows: deploy to AWS EKS
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root:

```env
POSTGRESQL_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres
POSTGRESQL_USERNAME=postgres
POSTGRESQL_PASSWORD=yourpassword
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

> **Note**: The application requires a PostgreSQL database. Ensure PostgreSQL is running and accessible. The `docker-compose.yml` contains only the application service — database must be provided separately.

### 2. Build and Start

```bash
# Build the image and start the container
docker-compose up --build

# Run in background
docker-compose up -d --build

# View logs
docker-compose logs -f cargo-tracker

# Stop
docker-compose down
```

### 3. Access the Application

- **Application**: http://localhost:8080/cargo-tracker
- **Admin Interface**: http://localhost:8080/cargo-tracker/admin

---

## Build and Push Docker Image

### Linux/macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

### Script Prompts

The script will interactively ask for:
1. **Image tag** (default: `latest`)
2. **Registry type**: `1` for AWS ECR, `2` for Docker Hub
3. **Registry credentials** based on selection

### Manual Build

```bash
# Build
docker build -t cargo-tracker:latest .

# Tag for ECR
docker tag cargo-tracker:latest <account-id>.dkr.ecr.<region>.amazonaws.com/cargo-tracker:latest

# Push to ECR
aws ecr get-login-password --region <region> | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/cargo-tracker:latest
```

---

## AWS EKS Prerequisites

### 1. Install Required Tools

```bash
# AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/

# eksctl
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin
```

### 2. Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### 3. Create ECR Repository

```bash
aws ecr create-repository \
  --repository-name cargo-tracker \
  --region <your-region>
```

---

## EKS Cluster Setup

### Option A: Create New Cluster with eksctl

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

### Option B: Use Existing Cluster

```bash
aws eks update-kubeconfig --region <region> --name <cluster-name>
kubectl cluster-info
```

### Install AWS Load Balancer Controller (Required for ALB Ingress)

```bash
# Create IAM policy
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.1/docs/install/iam_policy.json
aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Create service account
eksctl create iamserviceaccount \
  --cluster=<cluster-name> \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name AmazonEKSLoadBalancerControllerRole \
  --attach-policy-arn=arn:aws:iam::<account-id>:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Install via Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<cluster-name> \
  --set serviceAccountName=aws-load-balancer-controller
```

---

## Kubernetes Deployment

### Automated Deployment

#### Linux/macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region
- EKS Cluster Name
- Docker image URI (full path with tag)
- PostgreSQL JDBC URL
- PostgreSQL username and password
- Graph Traversal URL

### Manual Deployment

```bash
# 1. Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster

# 2. Update image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|<account-id>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest|g' kubernetes/deployment.yaml
sed -i 's|{{POSTGRESQL_JDBC_URL}}|jdbc:postgresql://your-db-host:5432/postgres|g' kubernetes/deployment.yaml
sed -i 's|{{POSTGRESQL_USERNAME}}|postgres|g' kubernetes/deployment.yaml
sed -i 's|{{POSTGRESQL_PASSWORD}}|yourpassword|g' kubernetes/deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# 5. Verify
kubectl get pods,svc,ingress -n cargo-tracker
```

### Verify Deployment

```bash
# Check pods
kubectl get pods -n cargo-tracker

# Check pod logs
kubectl logs -f deployment/cargo-tracker -n cargo-tracker

# Check service
kubectl get svc -n cargo-tracker

# Get ingress hostname (ALB)
kubectl get ingress cargo-tracker-ingress -n cargo-tracker
```

---

## Configuration Management

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport` |
| `TZ` | Timezone | `UTC` |
| `POSTGRESQL_JDBC_URL` | PostgreSQL JDBC connection URL | Required |
| `POSTGRESQL_USERNAME` | Database username | Required |
| `POSTGRESQL_PASSWORD` | Database password | Required |
| `GRAPH_TRAVERSAL_URL` | Internal graph traversal REST endpoint | Required |

### Using Kubernetes Secrets for Sensitive Data

```bash
# Create secret for database credentials
kubectl create secret generic cargo-tracker-db-secret \
  --from-literal=postgresql-url="jdbc:postgresql://host:5432/postgres" \
  --from-literal=postgresql-username="postgres" \
  --from-literal=postgresql-password="yourpassword" \
  -n cargo-tracker
```

Then reference in `deployment.yaml`:
```yaml
env:
  - name: POSTGRESQL_PASSWORD
    valueFrom:
      secretKeyRef:
        name: cargo-tracker-db-secret
        key: postgresql-password
```

### Using AWS Secrets Manager

```bash
# Store secret
aws secretsmanager create-secret \
  --name cargo-tracker/db-credentials \
  --secret-string '{"username":"postgres","password":"yourpassword"}'
```

---

## Scaling and Management

### Manual Scaling

```bash
kubectl scale deployment cargo-tracker --replicas=3 -n cargo-tracker
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker

kubectl get hpa -n cargo-tracker
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<new-image-uri> \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Rollback to specific revision
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
kubectl rollout undo deployment/cargo-tracker --to-revision=2 -n cargo-tracker
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n cargo-tracker

# Check pod logs
kubectl logs <pod-name> -n cargo-tracker

# Check events
kubectl get events -n cargo-tracker --sort-by='.lastTimestamp'
```

### Common Issues

#### 1. ImagePullBackOff
- Verify ECR repository exists and image tag is correct
- Check IAM permissions for ECR pull
- Verify `imagePullPolicy` in deployment.yaml

```bash
# Check node IAM role has ECR permissions
aws iam list-attached-role-policies --role-name <node-role-name>
```

#### 2. CrashLoopBackOff
- Check application logs: `kubectl logs <pod-name> -n cargo-tracker`
- Verify PostgreSQL connection string is correct
- Ensure database is accessible from the EKS cluster
- Check JVM memory settings — increase if OOMKilled

#### 3. Payara Micro Startup Issues
- Payara Micro requires more startup time (60-90 seconds)
- `initialDelaySeconds` in probes is set to 90s for liveness and 60s for readiness
- If pods are being killed before startup, increase `initialDelaySeconds`

#### 4. Database Connection Failures
- Verify PostgreSQL is accessible from EKS nodes (security groups, VPC peering)
- Test connectivity: `kubectl run -it --rm debug --image=busybox --restart=Never -- nc -zv <db-host> 5432`
- Check JDBC URL format: `jdbc:postgresql://<host>:<port>/<database>`

#### 5. Ingress Not Getting External IP
- Verify AWS Load Balancer Controller is installed and running
- Check controller logs: `kubectl logs -n kube-system deployment/aws-load-balancer-controller`
- Ensure subnets are tagged: `kubernetes.io/role/elb=1`

#### 6. OOMKilled (Out of Memory)
```bash
# Check resource usage
kubectl top pods -n cargo-tracker

# Increase memory limits in deployment.yaml
resources:
  limits:
    memory: "2Gi"
```

### Useful Diagnostic Commands

```bash
# Get all resources in namespace
kubectl get all -n cargo-tracker

# Describe deployment
kubectl describe deployment cargo-tracker -n cargo-tracker

# Port-forward for local testing
kubectl port-forward deployment/cargo-tracker 8080:8080 -n cargo-tracker

# Execute shell in pod
kubectl exec -it <pod-name> -n cargo-tracker -- /bin/bash

# Check node resources
kubectl describe nodes
kubectl top nodes
```

---

## Security Considerations

1. **Non-root container**: The Dockerfile creates and uses a `payara` non-root user
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for credentials — never hardcode in manifests
3. **Network policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication
4. **Image scanning**: Enable ECR image scanning for vulnerability detection
5. **RBAC**: Apply least-privilege RBAC policies for service accounts
6. **TLS**: Configure HTTPS on the ALB Ingress using ACM certificates:
   ```yaml
   annotations:
     alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<region>:<account>:certificate/<id>
     alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
   ```
7. **Pod Security**: Consider adding `securityContext` to pods:
   ```yaml
   securityContext:
     runAsNonRoot: true
     readOnlyRootFilesystem: false
     allowPrivilegeEscalation: false
   ```

---

## Jakarta EE Specific Notes

### Payara Micro Configuration
- The application runs on **Payara Micro 6.2025.3** (Jakarta EE 10 compatible)
- Post-boot commands are configured via `post-boot-commands.asadmin`
- The WAR is deployed with context root `/cargo-tracker`
- JMS queues are configured internally within Payara Micro

### Database
- **Development**: H2 in-file mode (default Maven profile)
- **Production/Cloud**: PostgreSQL (activated with `-P cloud` Maven profile)
- The `cloud` Maven profile is used in the Docker build to include the PostgreSQL JDBC driver
- Schema is auto-generated on first startup (`jakarta.persistence.schema-generation.database.action=create`)

### JVM Tuning for Containers
The following JVM flags are set for container-aware operation:
```
-Xmx512m -Xms256m
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+UnlockExperimentalVMOptions
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
```

### Session Affinity
For JSF (Jakarta Faces) applications, session affinity is recommended. Configure sticky sessions on the ALB:
```yaml
annotations:
  alb.ingress.kubernetes.io/target-group-attributes: stickiness.enabled=true,stickiness.lb_cookie.duration_seconds=86400
```

### Health Probes
Since Payara Micro does not expose a Spring Actuator-style health endpoint by default, TCP socket probes are used:
- **Liveness probe**: `tcpSocket` on port 8080, `initialDelaySeconds: 90`
- **Readiness probe**: `tcpSocket` on port 8080, `initialDelaySeconds: 60`

Payara Micro has a longer startup time than Spring Boot — the probe delays account for JVM warm-up and application deployment.
