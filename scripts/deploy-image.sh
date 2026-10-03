#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh - Deploy cargo-tracker to AWS EKS
# ============================================================

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo "  Cargo Tracker - Deploy to AWS EKS"
echo "============================================"
echo ""

# ---- Prompt for AWS / EKS details ----
read -rp "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required." >&2
  exit 1
fi

read -rp "Enter full Docker Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker Image URI is required." >&2
  exit 1
fi

echo ""
echo "--- Optional Application Configuration ---"
echo "(Press Enter to skip any value and keep the placeholder)"
echo ""

read -rp "Enter DB_HOST (database hostname) []: " DB_HOST_VAL
DB_HOST_VAL="${DB_HOST_VAL:-localhost}"

read -rp "Enter DB_PORT (database port) [5432]: " DB_PORT_VAL
DB_PORT_VAL="${DB_PORT_VAL:-5432}"

read -rp "Enter DB_NAME (database name) [postgres]: " DB_NAME_VAL
DB_NAME_VAL="${DB_NAME_VAL:-postgres}"

read -rp "Enter REDIS_HOST (Redis/ElastiCache hostname) []: " REDIS_HOST_VAL
REDIS_HOST_VAL="${REDIS_HOST_VAL:-localhost}"

read -rp "Enter REDIS_PORT (Redis port) [6379]: " REDIS_PORT_VAL
REDIS_PORT_VAL="${REDIS_PORT_VAL:-6379}"

read -rp "Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: " GRAPH_TRAVERSAL_URL_VAL
GRAPH_TRAVERSAL_URL_VAL="${GRAPH_TRAVERSAL_URL_VAL:-http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path}"

echo ""
echo "--- Configuring kubectl for EKS ---"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

echo ""
echo "--- Updating Kubernetes manifests ---"

# Work on copies to avoid modifying originals
DEPLOY_DIR=$(mktemp -d)
cp -r "$PROJECT_ROOT/kubernetes/"* "$DEPLOY_DIR/"

# Replace placeholders using pipe delimiter
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                                   "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{DB_HOST}}|${DB_HOST_VAL}|g"                                   "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{DB_PORT}}|${DB_PORT_VAL}|g"                                   "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{DB_NAME}}|${DB_NAME_VAL}|g"                                   "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST_VAL}|g"                             "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT_VAL}|g"                             "$DEPLOY_DIR/deployment.yaml"
sed -i "s|{{GRAPH_TRAVERSAL_URL}}|${GRAPH_TRAVERSAL_URL_VAL}|g"           "$DEPLOY_DIR/deployment.yaml"

echo ""
echo "--- Applying Kubernetes manifests ---"

echo "Applying namespace..."
kubectl apply -f "$DEPLOY_DIR/namespace.yaml"

echo "Applying deployment..."
kubectl apply -f "$DEPLOY_DIR/deployment.yaml"

echo "Applying service..."
kubectl apply -f "$DEPLOY_DIR/service.yaml"

echo "Applying ingress..."
kubectl apply -f "$DEPLOY_DIR/ingress.yaml"

echo ""
echo "--- Waiting for deployment rollout ---"
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

echo ""
echo "--- Verifying deployed resources ---"
kubectl get pods,svc,ingress -n "$NAMESPACE"

echo ""
echo "--- Application Access ---"
INGRESS_HOST=$(kubectl get ingress "$APP_NAME-ingress" -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://${INGRESS_HOST}/cargo-tracker"
else
  echo "Ingress hostname is still provisioning. Run the following to check:"
  echo "  kubectl get ingress -n $NAMESPACE"
fi

echo ""
echo "--- Rollback Instructions ---"
echo "If the deployment fails, run:"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"

# Cleanup temp dir
rm -rf "$DEPLOY_DIR"

echo ""
echo "============================================"
echo "  Deployment Complete!"
echo "============================================"
