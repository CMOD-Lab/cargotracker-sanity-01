#!/bin/bash
# =============================================================================
# deploy-image.sh – Deploy cargo-tracker to AWS EKS
# Usage: ./scripts/deploy-image.sh
# Run from the repository root directory.
# =============================================================================
set -e
set -o pipefail

echo "============================================="
echo "  Eclipse Cargo Tracker – Deploy to AWS EKS"
echo "============================================="
echo ""

# ── Collect deployment parameters ────────────────────────────────────────────
read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required."
  exit 1
fi

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required."
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "--- Optional application configuration (press Enter to skip) ---"
echo ""

read -rp "Enter REDIS_HOST (ElastiCache endpoint) [localhost]: " REDIS_HOST_VAL
REDIS_HOST_VAL="${REDIS_HOST_VAL:-localhost}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT_VAL
REDIS_PORT_VAL="${REDIS_PORT_VAL:-6379}"

read -rp "Enter DB_JDBC_URL [jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database]: " DB_JDBC_URL_VAL
DB_JDBC_URL_VAL="${DB_JDBC_URL_VAL:-jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database}"

read -rp "Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: " GRAPH_TRAVERSAL_URL_VAL
GRAPH_TRAVERSAL_URL_VAL="${GRAPH_TRAVERSAL_URL_VAL:-http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path}"

echo ""

# ── Configure kubectl for EKS ─────────────────────────────────────────────────
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in $AWS_REGION ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster."; exit 1; }

echo ""

# ── Patch Kubernetes manifests ────────────────────────────────────────────────
echo "Updating Kubernetes manifests with deployment values..."

# Work on copies to avoid modifying originals permanently
cp kubernetes/deployment.yaml /tmp/deployment-deploy.yaml
cp kubernetes/service.yaml    /tmp/service-deploy.yaml
cp kubernetes/ingress.yaml    /tmp/ingress-deploy.yaml
cp kubernetes/namespace.yaml  /tmp/namespace-deploy.yaml

sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'                         /tmp/deployment-deploy.yaml
sed -i 's|{{REDIS_HOST}}|'"$REDIS_HOST_VAL"'|g'                   /tmp/deployment-deploy.yaml
sed -i 's|{{REDIS_PORT}}|'"$REDIS_PORT_VAL"'|g'                   /tmp/deployment-deploy.yaml
sed -i 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL_VAL"'|g'                 /tmp/deployment-deploy.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL_VAL"'|g' /tmp/deployment-deploy.yaml

echo "Manifests updated."
echo ""

# ── Apply manifests ───────────────────────────────────────────────────────────
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f /tmp/namespace-deploy.yaml

echo "  [2/4] Applying deployment..."
kubectl apply -f /tmp/deployment-deploy.yaml

echo "  [3/4] Applying service..."
kubectl apply -f /tmp/service-deploy.yaml

echo "  [4/4] Applying ingress..."
kubectl apply -f /tmp/ingress-deploy.yaml

echo ""

# ── Wait for rollout ──────────────────────────────────────────────────────────
echo "Waiting for deployment rollout to complete..."
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s || {
  echo ""
  echo "WARNING: Rollout did not complete within 5 minutes."
  echo "To rollback, run: kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
  exit 1
}

echo ""

# ── Verify resources ──────────────────────────────────────────────────────────
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n cargo-tracker

echo ""

# ── Display access URL ────────────────────────────────────────────────────────
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n cargo-tracker \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")

echo "============================================="
echo "  Deployment complete!"
if [ -n "$INGRESS_HOST" ]; then
  echo "  Application URL: http://$INGRESS_HOST/cargo-tracker"
else
  echo "  Ingress hostname not yet assigned."
  echo "  Run: kubectl get ingress -n cargo-tracker"
fi
echo "============================================="

# ── Cleanup temp files ────────────────────────────────────────────────────────
rm -f /tmp/deployment-deploy.yaml /tmp/service-deploy.yaml \
      /tmp/ingress-deploy.yaml /tmp/namespace-deploy.yaml
