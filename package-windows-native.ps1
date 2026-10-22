# ==============================================================================
# Standalone Native App Bundler (Zero Java Runtime Required)
# Uses OpenJDK 21+ jpackage to produce an independent Windows desktop application
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "[1/4] Verifying ComfyUI Companion Fat JAR..." -ForegroundColor Cyan
$targetJar = "target/comfyuicompanion.jar"
if (-not (Test-Path $targetJar)) {
    Write-Host "Target JAR not found. Building with Maven..." -ForegroundColor Cyan
    mvn -f "pom.xml" clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed."
        exit 1
    }
}

$icoPath = "assets/icon.ico"
if (-not (Test-Path $icoPath) -and (Test-Path "assets/icon.png")) {
    Write-Host "[*] Generating Windows Icon from assets/icon.png..." -ForegroundColor Cyan
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Bitmap]::FromFile((Resolve-Path "assets/icon.png"))
    $resized = New-Object System.Drawing.Bitmap 256, 256
    $g = [System.Drawing.Graphics]::FromImage($resized)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($src, 0, 0, 256, 256)
    $g.Dispose()
    $src.Dispose()

    $ms = New-Object System.IO.MemoryStream
    $resized.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBytes = $ms.ToArray()
    $resized.Dispose()
    $ms.Dispose()

    $fs = [System.IO.File]::Create((Join-Path (Get-Location) $icoPath))
    $bw = New-Object System.IO.BinaryWriter $fs
    $bw.Write([uint16]0)
    $bw.Write([uint16]1)
    $bw.Write([uint16]1)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([uint16]1)
    $bw.Write([uint16]32)
    $bw.Write([uint32]$pngBytes.Length)
    $bw.Write([uint32]22)
    $bw.Write($pngBytes)
    $bw.Flush()
    $fs.Close()
}

Write-Host "[2/4] Preparing Clean Packaging Staging Directory..." -ForegroundColor Cyan
$stagingDir = "target/jpackage-input"
if (Test-Path $stagingDir) {
    Remove-Item -Recurse -Force $stagingDir
}
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
Copy-Item -Path $targetJar -Destination $stagingDir

Write-Host "[3/4] Bundling Standalone Windows App Image via jpackage..." -ForegroundColor Cyan
if (Test-Path "dist/ComfyUICompanion") {
    Remove-Item -Recurse -Force "dist/ComfyUICompanion"
}
New-Item -ItemType Directory -Force -Path "dist" | Out-Null

jpackage `
  --type app-image `
  --name "ComfyUICompanion" `
  --input "$stagingDir" `
  --main-jar "comfyuicompanion.jar" `
  --main-class "de.tki.comfyuicompanion.Main" `
  --dest "dist" `
  --app-version "2.0.2" `
  --vendor "TKI" `
  --description "Enterprise AI Companion for ComfyUI" `
  --icon "$icoPath" `
  --java-options "--enable-native-access=ALL-UNNAMED" `
  --java-options "-Dfile.encoding=UTF-8"

if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed."
    exit 1
}

Write-Host "[4/4] Creating Portable ZIP Archive..." -ForegroundColor Cyan
Compress-Archive -Path "dist/ComfyUICompanion" -DestinationPath "dist/ComfyUICompanion-Windows-x64-Portable.zip" -Force

Write-Host "[SUCCESS] Standalone application successfully created!" -ForegroundColor Green
Write-Host "Executable location: dist\ComfyUICompanion\ComfyUICompanion.exe" -ForegroundColor Green
Write-Host "Portable ZIP archive: dist\ComfyUICompanion-Windows-x64-Portable.zip" -ForegroundColor Green
