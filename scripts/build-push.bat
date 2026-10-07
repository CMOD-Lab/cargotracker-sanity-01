@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem build-push.bat – Build and push the cargo-tracker Docker image
rem ============================================================

set "PROJECT_NAME=cargo-tracker"
set "IMAGE_NAME=cargo-tracker"

echo ============================================
echo   cargo-tracker - Docker Build ^& Push
echo ============================================
echo.

rem ---- Registry selection ----
echo Select container registry:
echo   1) AWS ECR
echo   2) Docker Hub
set /p "REGISTRY_CHOICE=Enter choice [1-2]: "

rem ---- Image tag ----
set /p "IMAGE_TAG_INPUT=Enter image tag (default: latest): "
if "!IMAGE_TAG_INPUT!"=="" (
    set "IMAGE_TAG=latest"
) else (
    set "IMAGE_TAG=!IMAGE_TAG_INPUT!"
)

echo.
echo Image name : !IMAGE_NAME!
echo Image tag  : !IMAGE_TAG!
echo.

rem ============================================================
rem AWS ECR
rem ============================================================
if "!REGISTRY_CHOICE!"=="1" (
    set /p "AWS_REGION=Enter AWS region (e.g. us-east-1): "
    set /p "AWS_ACCOUNT_ID=Enter AWS account ID: "

    if "!AWS_REGION!"=="" (
        echo ERROR: AWS region is required.
        exit /b 1
    )
    if "!AWS_ACCOUNT_ID!"=="" (
        echo ERROR: AWS account ID is required.
        exit /b 1
    )

    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
    set "ECR_REPO=!IMAGE_NAME!"
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

    echo Logging in to ECR...
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

rem ============================================================
rem Docker Hub
rem ============================================================
) else if "!REGISTRY_CHOICE!"=="2" (
    set /p "DOCKER_USERNAME=Enter Docker Hub username: "
    set /p "DOCKER_PASSWORD=Enter Docker Hub password/token: "

    if "!DOCKER_USERNAME!"=="" (
        echo ERROR: Docker Hub username is required.
        exit /b 1
    )
    if "!DOCKER_PASSWORD!"=="" (
        echo ERROR: Docker Hub password is required.
        exit /b 1
    )

    set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"

    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )

) else (
    echo ERROR: Invalid registry choice '!REGISTRY_CHOICE!'.
    exit /b 1
)

rem ============================================================
rem Build
rem ============================================================
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo ============================================
echo   Build ^& push complete!
echo   Image: !FULL_IMAGE_NAME!
echo ============================================

endlocal
