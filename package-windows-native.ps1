# ==============================================================================
# Standalone Native App Bundler (Zero Java Runtime Required)
# Uses OpenJDK 21 jpackage to produce an independent Windows desktop application
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "🚀 [1/3] Building ComfyUI Companion Fat JAR with Maven..." -ForegroundColor Cyan
mvn -f "pom.xml" clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed."
    exit 1
}

$targetJar = "target/comfyuicompanion.jar"
if (-not (Test-Path $targetJar)) {
    Write-Error "Target JAR not found at $targetJar"
    exit 1
}

Write-Host "📦 [2/3] Bundling Standalone Windows App Image via jpackage..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "dist" | Out-Null

jpackage `
  --type app-image `
  --name "ComfyUICompanion" `
  --input "target" `
  --main-jar "comfyuicompanion.jar" `
  --main-class "de.tki.comfyuicompanion.Main" `
  --dest "dist" `
  --app-version "2.0.1" `
  --vendor "TKI" `
  --description "Enterprise AI Companion for ComfyUI"

Write-Host "🎉 [3/3] Standalone application successfully created!" -ForegroundColor Green
Write-Host "Executable location: dist\ComfyUICompanion\ComfyUICompanion.exe" -ForegroundColor Green
