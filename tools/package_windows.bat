@echo off
setlocal EnableDelayedExpansion
echo ========================================================
echo   ComfyUI Companion - Standalone Windows Package Tool
echo ========================================================

cd /d "%~dp0\.."

echo [*] Building latest fat JAR via Maven...
call mvn -B clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed.
    exit /b %ERRORLEVEL%
)

if not exist "dist" mkdir "dist"

echo [*] Packaging standalone application with jpackage...
jpackage ^
  --type app-image ^
  --name "ComfyUICompanion" ^
  --input "target" ^
  --main-jar "comfyuicompanion.jar" ^
  --main-class "de.tki.comfyuicompanion.Main" ^
  --dest "dist" ^
  --app-version "2.0.2" ^
  --vendor "TKI" ^
  --description "Enterprise AI Companion for ComfyUI"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Standalone Windows app packaged in: dist\ComfyUICompanion\
    echo Run dist\ComfyUICompanion\ComfyUICompanion.exe without any installed Java!
) else (
    echo [ERROR] jpackage failed. Ensure JDK 21+ with jpackage is in PATH.
)
endlocal
