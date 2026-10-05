@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat – Deploy cargo-tracker to AWS EKS (Windows)
REM Usage: scripts\deploy-image.bat
REM Run from the repository root directory.
REM =============================================================================

echo =============================================
echo   Eclipse Cargo Tracker - Deploy to AWS EKS
echo =============================================
echo.

REM ── Collect deployment parameters ────────────────────────────────────────────
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
echo --- Optional application configuration (press Enter to skip) ---
echo.

set /p "REDIS_HOST_VAL=Enter REDIS_HOST (ElastiCache endpoint) [localhost]: "
if "!REDIS_HOST_VAL!"=="" set "REDIS_HOST_VAL=localhost"

set /p "REDIS_PORT_VAL=Enter REDIS_PORT [6379]: "
if "!REDIS_PORT_VAL!"=="" set "REDIS_PORT_VAL=6379"

set /p "DB_JDBC_URL_VAL=Enter DB_JDBC_URL [jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database]: "
if "!DB_JDBC_URL_VAL!"=="" set "DB_JDBC_URL_VAL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database"

set /p "GRAPH_TRAVERSAL_URL_VAL=Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: "
if "!GRAPH_TRAVERSAL_URL_VAL!"=="" set "GRAPH_TRAVERSAL_URL_VAL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path"

echo.

REM ── Configure kubectl for EKS ─────────────────────────────────────────────────
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

echo.

REM ── Patch Kubernetes manifests using PowerShell ───────────────────────────────
echo Updating Kubernetes manifests with deployment values...

powershell -NoProfile -Command ^
  "(Get-Content 'kubernetes\deployment.yaml') ^
   -replace '\{\{IMAGE_URI\}\}','!IMAGE_URI!' ^
   -replace '\{\{REDIS_HOST\}\}','!REDIS_HOST_VAL!' ^
   -replace '\{\{REDIS_PORT\}\}','!REDIS_PORT_VAL!' ^
   -replace '\{\{DB_JDBC_URL\}\}','!DB_JDBC_URL_VAL!' ^
   -replace '\{\{GRAPH_TRAVERSAL_URL\}\}','!GRAPH_TRAVERSAL_URL_VAL!' ^
   | Set-Content '%TEMP%\deployment-deploy.yaml'"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to patch deployment.yaml.
    exit /b 1
)

copy /Y "kubernetes\service.yaml"   "%TEMP%\service-deploy.yaml"   >nul
copy /Y "kubernetes\ingress.yaml"   "%TEMP%\ingress-deploy.yaml"   >nul
copy /Y "kubernetes\namespace.yaml" "%TEMP%\namespace-deploy.yaml" >nul

echo Manifests updated.
echo.

REM ── Apply manifests ───────────────────────────────────────────────────────────
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f "%TEMP%\namespace-deploy.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo   [2/4] Applying deployment...
kubectl apply -f "%TEMP%\deployment-deploy.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo   [3/4] Applying service...
kubectl apply -f "%TEMP%\service-deploy.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo   [4/4] Applying ingress...
kubectl apply -f "%TEMP%\ingress-deploy.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

echo.

REM ── Wait for rollout ──────────────────────────────────────────────────────────
echo Waiting for deployment rollout to complete...
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo.
    echo WARNING: Rollout did not complete within 5 minutes.
    echo To rollback, run: kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
    exit /b 1
)

echo.

REM ── Verify resources ──────────────────────────────────────────────────────────
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n cargo-tracker

echo.

REM ── Display access URL ────────────────────────────────────────────────────────
for /f "delims=" %%h in ('kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set "INGRESS_HOST=%%h"

echo =============================================
echo   Deployment complete!
if "!INGRESS_HOST!"=="" (
    echo   Ingress hostname not yet assigned.
    echo   Run: kubectl get ingress -n cargo-tracker
) else (
    echo   Application URL: http://!INGRESS_HOST!/cargo-tracker
)
echo =============================================

REM ── Cleanup temp files ────────────────────────────────────────────────────────
del /f /q "%TEMP%\deployment-deploy.yaml" "%TEMP%\service-deploy.yaml" ^
         "%TEMP%\ingress-deploy.yaml"     "%TEMP%\namespace-deploy.yaml" >nul 2>&1

endlocal
