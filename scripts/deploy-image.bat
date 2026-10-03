@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM deploy-image.bat - Deploy cargo-tracker to AWS EKS
REM ============================================================

set "APP_NAME=cargo-tracker"
set "NAMESPACE=cargo-tracker"
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
set "K8S_DIR=%PROJECT_ROOT%kubernetes"

echo ==============================================
echo   Cargo Tracker - AWS EKS Deployment Script
echo ==============================================
echo.

REM ---- Collect required inputs ----
set /p "AWS_REGION=Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

set /p "CLUSTER_NAME=Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

set /p "IMAGE_URI=Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo ---- Application Configuration ----
set /p "POSTGRESQL_JDBC_URL=Enter PostgreSQL JDBC URL (e.g. jdbc:postgresql://host:5432/postgres): "
if "!POSTGRESQL_JDBC_URL!"=="" set "POSTGRESQL_JDBC_URL=jdbc:postgresql://postgres:5432/postgres"

set /p "POSTGRESQL_USERNAME=Enter PostgreSQL username [postgres]: "
if "!POSTGRESQL_USERNAME!"=="" set "POSTGRESQL_USERNAME=postgres"

set /p "POSTGRESQL_PASSWORD=Enter PostgreSQL password: "
if "!POSTGRESQL_PASSWORD!"=="" set "POSTGRESQL_PASSWORD=postgres"

set /p "GRAPH_TRAVERSAL_URL=Enter Graph Traversal URL [http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path]: "
if "!GRAPH_TRAVERSAL_URL!"=="" set "GRAPH_TRAVERSAL_URL=http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path"

echo.
echo ---- Configuring kubectl for EKS ----
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

echo.
echo ---- Updating Kubernetes manifests ----

REM Create temp directory for modified manifests
set "TMP_DIR=%TEMP%\cargo-tracker-deploy-%RANDOM%"
mkdir "!TMP_DIR!"

copy "!K8S_DIR!\namespace.yaml"  "!TMP_DIR!\namespace.yaml"  >nul
copy "!K8S_DIR!\deployment.yaml" "!TMP_DIR!\deployment.yaml" >nul
copy "!K8S_DIR!\service.yaml"    "!TMP_DIR!\service.yaml"    >nul
copy "!K8S_DIR!\ingress.yaml"    "!TMP_DIR!\ingress.yaml"    >nul

REM Replace placeholders using PowerShell
powershell -Command "(Get-Content '!TMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{POSTGRESQL_JDBC_URL}}','!POSTGRESQL_JDBC_URL!' -replace '{{POSTGRESQL_USERNAME}}','!POSTGRESQL_USERNAME!' -replace '{{POSTGRESQL_PASSWORD}}','!POSTGRESQL_PASSWORD!' -replace '{{GRAPH_TRAVERSAL_URL}}','!GRAPH_TRAVERSAL_URL!' | Set-Content '!TMP_DIR!\deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

echo.
echo ---- Applying Kubernetes manifests ----

echo Applying namespace...
kubectl apply -f "!TMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 (echo ERROR: Failed to apply namespace. & exit /b 1)

echo Applying deployment...
kubectl apply -f "!TMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 (echo ERROR: Failed to apply deployment. & exit /b 1)

echo Applying service...
kubectl apply -f "!TMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 (echo ERROR: Failed to apply service. & exit /b 1)

echo Applying ingress...
kubectl apply -f "!TMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 (echo ERROR: Failed to apply ingress. & exit /b 1)

REM Clean up temp files
rmdir /s /q "!TMP_DIR!"

echo.
echo ---- Waiting for deployment rollout ----
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo.
    echo WARNING: Deployment rollout did not complete within timeout.
    echo To rollback, run:
    echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

echo.
echo ---- Verifying deployed resources ----
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ==============================================
echo   SUCCESS: Deployment complete!
echo   Namespace: !NAMESPACE!
echo   Image:     !IMAGE_URI!
echo ==============================================
echo.
echo Useful commands:
echo   kubectl get pods -n !NAMESPACE!
echo   kubectl logs -f deployment/!APP_NAME! -n !NAMESPACE!
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
exit /b 0
