@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "DEV_JAR=C:\Dev\workspace\comfyuicompanion\target\comfyuicompanion.jar"
set "DEV_SRC=C:\Dev\workspace\comfyuicompanion\src"

if exist "%DEV_JAR%" (
    echo =======================================================
    echo  Local development workspace detected:
    echo  C:\Dev\workspace\comfyuicompanion
    echo =======================================================
    echo [Update] Syncing pre-built JAR and sources from workspace...
    if not exist "target" mkdir target
    copy /y "%DEV_JAR%" "target\comfyuicompanion.jar" >nul
    if exist "%DEV_SRC%" (
        robocopy "%DEV_SRC%" "src" /E /PURGE /NFL /NDL /NJH /NJS >nul 2>&1
    )
    echo [Update] Sync complete! Running with local developer build.
    exit /b 0
)

echo =======================================================
echo  Updating Companion for ComfyUI from Git
echo =======================================================
echo [Update] Fetching latest changes from origin/main...
git fetch origin
git checkout -B main origin/main
git reset --hard origin/main

echo [Update] Building application with Maven...
call mvn package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed with exit code %ERRORLEVEL%!
    exit /b %ERRORLEVEL%
)

echo [Update] Update complete!
endlocal
