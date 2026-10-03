@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat - Deploy cargo-tracker to AWS EKS
rem ============================================================

set APP_NAME=cargo-tracker
set NAMESPACE=cargo-tracker

echo ============================================
echo   Cargo Tracker - Deploy to AWS EKS
echo ============================================
echo.

rem ---- Prompt for AWS / EKS details ----
set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required.
    exit /b 1
)

echo.
echo --- Optional Application Configuration ---
echo (Press Enter to skip any value and keep the default)
echo.

set /p DB_HOST_VAL="Enter DB_HOST (database hostname) [localhost]: "
if "!DB_HOST_VAL!"=="" set DB_HOST_VAL=localhost

set /p DB_PORT_VAL="Enter DB_PORT (database port) [5432]: "
if "!DB_PORT_VAL!"=="" set DB_PORT_VAL=5432

set /p DB_NAME_VAL="Enter DB_NAME (database name) [postgres]: "
if "!DB_NAME_VAL!"=="" set DB_NAME_VAL=postgres

set /p REDIS_HOST_VAL="Enter REDIS_HOST (Redis/ElastiCache hostname) [localhost]: "
if "!REDIS_HOST_VAL!"=="" set REDIS_HOST_VAL=localhost

set /p REDIS_PORT_VAL="Enter REDIS_PORT (Redis port) [6379]: "
if "!REDIS_PORT_VAL!"=="" set REDIS_PORT_VAL=6379

set /p GRAPH_TRAVERSAL_URL_VAL="Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: "
if "!GRAPH_TRAVERSAL_URL_VAL!"=="" set GRAPH_TRAVERSAL_URL_VAL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

echo.
echo --- Configuring kubectl for EKS ---
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
echo --- Creating temporary manifest directory ---
set DEPLOY_DIR=%TEMP%\cargo-tracker-deploy-%RANDOM%
mkdir "!DEPLOY_DIR!"
xcopy /E /I /Q kubernetes "!DEPLOY_DIR!" >nul

echo --- Updating Kubernetes manifests ---
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{DB_HOST\}\}', '!DB_HOST_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{DB_PORT\}\}', '!DB_PORT_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{DB_NAME\}\}', '!DB_NAME_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{REDIS_HOST\}\}', '!REDIS_HOST_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{REDIS_PORT\}\}', '!REDIS_PORT_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!DEPLOY_DIR!\deployment.yaml') -replace '\{\{GRAPH_TRAVERSAL_URL\}\}', '!GRAPH_TRAVERSAL_URL_VAL!' | Set-Content '!DEPLOY_DIR!\deployment.yaml'"

echo.
echo --- Applying Kubernetes manifests ---

echo Applying namespace...
kubectl apply -f "!DEPLOY_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo Applying deployment...
kubectl apply -f "!DEPLOY_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo Applying service...
kubectl apply -f "!DEPLOY_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo Applying ingress...
kubectl apply -f "!DEPLOY_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

echo.
echo --- Waiting for deployment rollout ---
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo WARNING: Rollout did not complete within timeout. Check pod status.
)

echo.
echo --- Verifying deployed resources ---
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo --- Rollback Instructions ---
echo If the deployment fails, run:
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

rem Cleanup temp dir
rmdir /S /Q "!DEPLOY_DIR!"

echo.
echo ============================================
echo   Deployment Complete!
echo ============================================

endlocal
exit /b 0
