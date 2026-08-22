@echo off
setlocal
cd /d "%~dp0"

echo === Building Companion for ComfyUI ===
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo === Starting Application ===
java --enable-native-access=ALL-UNNAMED -jar target\comfyuicompanion.jar %*

endlocal
