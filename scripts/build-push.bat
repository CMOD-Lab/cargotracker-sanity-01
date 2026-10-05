@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM build-push.bat – Build and push the cargo-tracker Docker image (Windows)
REM Usage: scripts\build-push.bat
REM Run from the repository root directory.
REM =============================================================================

set "PROJECT_NAME=cargo-tracker"

REM ── Sanitise image name via PowerShell ───────────────────────────────────────
for /f "delims=" %%i in ('powershell -NoProfile -Command "$n='%PROJECT_NAME%'.ToLower() -replace '[^a-z0-9]','-'; $n=$n.Trim('-'); Write-Output $n"') do set "IMAGE_NAME=%%i"

echo =============================================
echo   Eclipse Cargo Tracker - Docker Build ^& Push
echo =============================================
echo.

REM ── Registry selection ────────────────────────────────────────────────────────
echo Select target registry:
echo   1) AWS ECR
echo   2) Docker Hub
echo.
set /p "REGISTRY_CHOICE=Enter choice [1 or 2]: "

REM ── Image tag ─────────────────────────────────────────────────────────────────
set /p "RAW_TAG=Enter image tag (press Enter for 'latest'): "
if "!RAW_TAG!"=="" (
    set "IMAGE_TAG=latest"
) else (
    for /f "delims=" %%t in ('powershell -NoProfile -Command "$t='!RAW_TAG!'.ToLower() -replace '[^a-z0-9._-]','-'; $t=$t.Trim('-'); if($t -eq ''){$t='latest'}; Write-Output $t"') do set "IMAGE_TAG=%%t"
)

echo.
echo Image tag: !IMAGE_TAG!
echo.

REM =============================================================================
REM AWS ECR
REM =============================================================================
if "!REGISTRY_CHOICE!"=="1" (
    set /p "AWS_REGION=Enter AWS Region (e.g. us-east-1): "
    set /p "AWS_ACCOUNT_ID=Enter AWS Account ID: "
    set /p "ECR_REPO_INPUT=Enter ECR repository name [!IMAGE_NAME!]: "
    if "!ECR_REPO_INPUT!"=="" (
        set "ECR_REPO=!IMAGE_NAME!"
    ) else (
        set "ECR_REPO=!ECR_REPO_INPUT!"
    )

    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

    echo.
    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR login failed.
        exit /b 1
    )

    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo ERROR: Failed to create ECR repository.
            exit /b 1
        )
    )

REM =============================================================================
REM Docker Hub
REM =============================================================================
) else if "!REGISTRY_CHOICE!"=="2" (
    set /p "DOCKER_USERNAME=Enter Docker Hub username: "
    set /p "DOCKER_PASSWORD=Enter Docker Hub password/token: "
    set /p "DOCKER_NAMESPACE_INPUT=Enter Docker Hub namespace/org [!DOCKER_USERNAME!]: "
    if "!DOCKER_NAMESPACE_INPUT!"=="" (
        set "DOCKER_NAMESPACE=!DOCKER_USERNAME!"
    ) else (
        set "DOCKER_NAMESPACE=!DOCKER_NAMESPACE_INPUT!"
    )

    set "FULL_IMAGE_NAME=!DOCKER_NAMESPACE!/!IMAGE_NAME!:!IMAGE_TAG!"

    echo.
    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )

) else (
    echo ERROR: Invalid choice. Exiting.
    exit /b 1
)

REM =============================================================================
REM Build
REM =============================================================================
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
echo Build context: . (repository root)
echo.
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Build successful.

REM =============================================================================
REM Push
REM =============================================================================
echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo =============================================
echo   Image pushed successfully!
echo   !FULL_IMAGE_NAME!
echo =============================================

endlocal
