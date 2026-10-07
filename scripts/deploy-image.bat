@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat – Deploy cargo-tracker to AWS EKS (Windows)
rem ============================================================

echo ============================================
echo   cargo-tracker - AWS EKS Deployment
echo ============================================
echo.

rem ---- Collect deployment parameters ----
set /p "AWS_REGION=Enter AWS region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS region is required.
    exit /b 1
)

set /p "CLUSTER_NAME=Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

set /p "IMAGE_URI=Enter full Docker image URI: "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo ---- Application environment variables ----
echo (Press Enter to use the default value shown)
echo.

set /p "REDIS_HOST_VAL=Enter REDIS_HOST (default: localhost): "
if "!REDIS_HOST_VAL!"=="" set "REDIS_HOST_VAL=localhost"

set /p "REDIS_PORT_VAL=Enter REDIS_PORT (default: 6379): "
if "!REDIS_PORT_VAL!"=="" set "REDIS_PORT_VAL=6379"

set /p "DB_JDBC_URL_VAL=Enter DB_JDBC_URL: "
if "!DB_JDBC_URL_VAL!"=="" set "DB_JDBC_URL_VAL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database"

set /p "DB_DRIVER_CLASS_VAL=Enter DB_DRIVER_CLASS (default: org.h2.jdbcx.JdbcDataSource): "
if "!DB_DRIVER_CLASS_VAL!"=="" set "DB_DRIVER_CLASS_VAL=org.h2.jdbcx.JdbcDataSource"

set /p "DB_USER_VAL=Enter DB_USER: "
set /p "DB_PASSWORD_VAL=Enter DB_PASSWORD: "

set /p "GRAPH_TRAVERSAL_URL_VAL=Enter GRAPH_TRAVERSAL_URL: "
if "!GRAPH_TRAVERSAL_URL_VAL!"=="" set "GRAPH_TRAVERSAL_URL_VAL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path"

rem ---- Configure kubectl ----
echo.
echo Configuring kubectl for EKS cluster '!CLUSTER_NAME!' in '!AWS_REGION!'...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

rem ---- Copy manifests to temp ----
echo.
echo Updating Kubernetes manifests with deployment values...

copy /Y kubernetes\deployment.yaml %TEMP%\deployment-deploy.yaml >nul
copy /Y kubernetes\service.yaml    %TEMP%\service-deploy.yaml    >nul
copy /Y kubernetes\ingress.yaml    %TEMP%\ingress-deploy.yaml    >nul
copy /Y kubernetes\namespace.yaml  %TEMP%\namespace-deploy.yaml  >nul

rem Use PowerShell for sed-like replacements
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{DB_DRIVER_CLASS}}', '!DB_DRIVER_CLASS_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{DB_USER}}', '!DB_USER_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"
powershell -Command "(Get-Content '%TEMP%\deployment-deploy.yaml') -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL_VAL!' | Set-Content '%TEMP%\deployment-deploy.yaml'"

rem ---- Apply manifests ----
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f %TEMP%\namespace-deploy.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f %TEMP%\deployment-deploy.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f %TEMP%\service-deploy.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f %TEMP%\ingress-deploy.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

rem ---- Wait for rollout ----
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Run: kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
    exit /b 1
)

rem ---- Verify ----
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo ============================================
echo   Deployment complete!
echo   Check ingress for the application URL.
echo.
echo   Rollback command (if needed):
echo     kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
echo ============================================

endlocal
