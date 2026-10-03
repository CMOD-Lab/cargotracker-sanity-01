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
K8S_DIR="$PROJECT_ROOT/kubernetes"

echo "=============================================="
echo "  Cargo Tracker - AWS EKS Deployment Script"
echo "=============================================="
echo ""

# ---- Collect required inputs ----
read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required." >&2
  exit 1
fi

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

echo ""
echo "---- Application Configuration ----"
read -rp "Enter PostgreSQL JDBC URL (e.g. jdbc:postgresql://host:5432/postgres): " POSTGRESQL_JDBC_URL
if [ -z "$POSTGRESQL_JDBC_URL" ]; then
  POSTGRESQL_JDBC_URL="jdbc:postgresql://postgres:5432/postgres"
  echo "  Using default: $POSTGRESQL_JDBC_URL"
fi

read -rp "Enter PostgreSQL username [postgres]: " POSTGRESQL_USERNAME
POSTGRESQL_USERNAME="${POSTGRESQL_USERNAME:-postgres}"

read -rsp "Enter PostgreSQL password: " POSTGRESQL_PASSWORD
echo ""
if [ -z "$POSTGRESQL_PASSWORD" ]; then
  POSTGRESQL_PASSWORD="postgres"
  echo "  Using default password."
fi

read -rp "Enter Graph Traversal URL [http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path]: " GRAPH_TRAVERSAL_URL
GRAPH_TRAVERSAL_URL="${GRAPH_TRAVERSAL_URL:-http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path}"

echo ""
echo "---- Configuring kubectl for EKS ----"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to configure kubectl for EKS cluster '$CLUSTER_NAME'." >&2
  exit 1
fi

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

echo ""
echo "---- Updating Kubernetes manifests ----"

# Work on copies to avoid modifying originals
TMP_DIR=$(mktemp -d)
cp "$K8S_DIR"/*.yaml "$TMP_DIR/"

# Replace all placeholders using pipe delimiter
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'                         "$TMP_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_JDBC_URL}}|'"$POSTGRESQL_JDBC_URL"'|g'     "$TMP_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_USERNAME}}|'"$POSTGRESQL_USERNAME"'|g'     "$TMP_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_PASSWORD}}|'"$POSTGRESQL_PASSWORD"'|g'     "$TMP_DIR/deployment.yaml"
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL"'|g'     "$TMP_DIR/deployment.yaml"

echo ""
echo "---- Applying Kubernetes manifests ----"

echo "Applying namespace..."
kubectl apply -f "$TMP_DIR/namespace.yaml"

echo "Applying deployment..."
kubectl apply -f "$TMP_DIR/deployment.yaml"

echo "Applying service..."
kubectl apply -f "$TMP_DIR/service.yaml"

echo "Applying ingress..."
kubectl apply -f "$TMP_DIR/ingress.yaml"

# Clean up temp files
rm -rf "$TMP_DIR"

echo ""
echo "---- Waiting for deployment rollout ----"
kubectl rollout status deployment/$APP_NAME -n $NAMESPACE --timeout=300s
if [ $? -ne 0 ]; then
  echo ""
  echo "WARNING: Deployment rollout did not complete within timeout."
  echo "To rollback, run:"
  echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
  exit 1
fi

echo ""
echo "---- Verifying deployed resources ----"
kubectl get pods,svc,ingress -n $NAMESPACE

echo ""
echo "---- Application Access ----"
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://$INGRESS_HOST/"
else
  echo "Ingress hostname is still provisioning. Run the following to check:"
  echo "  kubectl get ingress cargo-tracker-ingress -n $NAMESPACE"
fi

echo ""
echo "=============================================="
echo "  SUCCESS: Deployment complete!"
echo "  Namespace: $NAMESPACE"
echo "  Image:     $IMAGE_URI"
echo "=============================================="
echo ""
echo "Useful commands:"
echo "  kubectl get pods -n $NAMESPACE"
echo "  kubectl logs -f deployment/$APP_NAME -n $NAMESPACE"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE  # rollback"
