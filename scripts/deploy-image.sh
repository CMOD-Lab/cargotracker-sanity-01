#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh – Deploy cargo-tracker to AWS EKS
# ============================================================

echo "============================================"
echo "  cargo-tracker – AWS EKS Deployment"
echo "============================================"
echo ""

# ---- Collect deployment parameters ----
read -rp "Enter AWS region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS region is required." >&2
  exit 1
fi

read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

echo ""
echo "---- Application environment variables ----"
echo "(Press Enter to skip any variable and keep the placeholder)"
echo ""

read -rp "Enter REDIS_HOST (ElastiCache endpoint, default: localhost): " REDIS_HOST_VAL
REDIS_HOST_VAL="${REDIS_HOST_VAL:-localhost}"

read -rp "Enter REDIS_PORT (default: 6379): " REDIS_PORT_VAL
REDIS_PORT_VAL="${REDIS_PORT_VAL:-6379}"

read -rp "Enter DB_JDBC_URL (JDBC connection string): " DB_JDBC_URL_VAL
DB_JDBC_URL_VAL="${DB_JDBC_URL_VAL:-jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database}"

read -rp "Enter DB_DRIVER_CLASS (default: org.h2.jdbcx.JdbcDataSource): " DB_DRIVER_CLASS_VAL
DB_DRIVER_CLASS_VAL="${DB_DRIVER_CLASS_VAL:-org.h2.jdbcx.JdbcDataSource}"

read -rp "Enter DB_USER: " DB_USER_VAL
DB_USER_VAL="${DB_USER_VAL:-}"

read -rsp "Enter DB_PASSWORD: " DB_PASSWORD_VAL
echo ""
DB_PASSWORD_VAL="${DB_PASSWORD_VAL:-}"

read -rp "Enter GRAPH_TRAVERSAL_URL (default: http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path): " GRAPH_TRAVERSAL_URL_VAL
GRAPH_TRAVERSAL_URL_VAL="${GRAPH_TRAVERSAL_URL_VAL:-http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path}"

# ---- Configure kubectl ----
echo ""
echo "Configuring kubectl for EKS cluster '$CLUSTER_NAME' in '$AWS_REGION'..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

# ---- Patch manifests ----
echo ""
echo "Updating Kubernetes manifests with deployment values..."

# Work on copies so originals stay as templates
cp kubernetes/deployment.yaml /tmp/deployment-deploy.yaml
cp kubernetes/service.yaml    /tmp/service-deploy.yaml
cp kubernetes/ingress.yaml    /tmp/ingress-deploy.yaml
cp kubernetes/namespace.yaml  /tmp/namespace-deploy.yaml

sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'                                   /tmp/deployment-deploy.yaml
sed -i 's|{{REDIS_HOST}}|'"$REDIS_HOST_VAL"'|g'                             /tmp/deployment-deploy.yaml
sed -i 's|{{REDIS_PORT}}|'"$REDIS_PORT_VAL"'|g'                             /tmp/deployment-deploy.yaml
sed -i 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL_VAL"'|g'                           /tmp/deployment-deploy.yaml
sed -i 's|{{DB_DRIVER_CLASS}}|'"$DB_DRIVER_CLASS_VAL"'|g'                   /tmp/deployment-deploy.yaml
sed -i 's|{{DB_USER}}|'"$DB_USER_VAL"'|g'                                   /tmp/deployment-deploy.yaml
sed -i 's|{{DB_PASSWORD}}|'"$DB_PASSWORD_VAL"'|g'                           /tmp/deployment-deploy.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL_VAL"'|g'           /tmp/deployment-deploy.yaml

# ---- Apply manifests ----
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f /tmp/namespace-deploy.yaml

echo "  [2/4] Applying deployment..."
kubectl apply -f /tmp/deployment-deploy.yaml

echo "  [3/4] Applying service..."
kubectl apply -f /tmp/service-deploy.yaml

echo "  [4/4] Applying ingress..."
kubectl apply -f /tmp/ingress-deploy.yaml

# ---- Wait for rollout ----
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s

# ---- Verify ----
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n cargo-tracker

# ---- Display URL ----
echo ""
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n cargo-tracker \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "<pending>")
echo "============================================"
echo "  Deployment complete!"
echo "  Application URL: http://${INGRESS_HOST}/cargo-tracker"
echo ""
echo "  Rollback command (if needed):"
echo "    kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
echo "============================================"
