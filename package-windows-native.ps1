# ==============================================================================
# Standalone Native App Bundler (Zero Java Runtime Required)
# Uses OpenJDK 21+ jpackage to produce an independent Windows desktop application
# Supports packaging native App-Image, EXE Installer, and Portable ZIP
# ==============================================================================

param (
    [switch]$DeploySourceForge,
    [string]$SourceForgeUser = "thomaskifree",
    [string]$SourceForgeProject = "companion-for-comfyui"
)

$ErrorActionPreference = "Stop"

# Auto-detect WiX in standard paths if not yet in PATH
$wixCandidates = @(
    "C:\Users\thoma\.wix311",
    "$env:USERPROFILE\.wix311",
    "C:\Program Files (x86)\WiX Toolset v3.11\bin"
)
foreach ($cand in $wixCandidates) {
    if ((Test-Path "$cand\candle.exe") -and ($env:Path -notlike "*$cand*")) {
        $env:Path = "$env:Path;$cand"
        break
    }
}

Write-Host "[1/5] Verifying ComfyUI Companion Fat JAR..." -ForegroundColor Cyan
$targetJar = "target/comfyuicompanion.jar"
if (-not (Test-Path $targetJar)) {
    Write-Host "Target JAR not found. Building with Maven..." -ForegroundColor Cyan
    mvn -f "pom.xml" clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed."
        exit 1
    }
}

# Determine project version dynamically from pom.xml
$appVersion = "2.0.2"
try {
    [xml]$pomXml = Get-Content "pom.xml"
    if ($pomXml.project.version) {
        $appVersion = $pomXml.project.version
    }
} catch {
    Write-Warning "Could not parse pom.xml version, falling back to $appVersion"
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

Write-Host "[2/5] Preparing Clean Packaging Staging Directory..." -ForegroundColor Cyan
$stagingDir = "target/jpackage-input"
if (Test-Path $stagingDir) {
    Remove-Item -Recurse -Force $stagingDir
}
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
Copy-Item -Path $targetJar -Destination $stagingDir

Write-Host "[3/5] Bundling Standalone Windows App Image via jpackage..." -ForegroundColor Cyan
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
  --app-version "$appVersion" `
  --vendor "TKI" `
  --description "Enterprise AI Companion for ComfyUI" `
  --icon "$icoPath" `
  --java-options "--enable-native-access=ALL-UNNAMED" `
  --java-options "-Dfile.encoding=UTF-8"

if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage app-image failed."
    exit 1
}

Write-Host "[4/5] Creating Portable ZIP Archive..." -ForegroundColor Cyan
Compress-Archive -Path "dist/ComfyUICompanion" -DestinationPath "dist/ComfyUICompanion-Windows-x64-Portable.zip" -Force

# Build Native Windows Installer EXE if WiX is available
$hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -ne $null
if ($hasWix) {
    Write-Host "[5/5] Building Native Windows EXE Installer..." -ForegroundColor Cyan
    jpackage `
      --type exe `
      --name "ComfyUICompanion" `
      --app-image "dist/ComfyUICompanion" `
      --dest "dist" `
      --app-version "$appVersion" `
      --vendor "TKI" `
      --description "Enterprise AI Companion for ComfyUI" `
      --icon "$icoPath" `
      --win-dir-chooser `
      --win-menu `
      --win-shortcut

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "jpackage exe installer build failed."
    }
} else {
    Write-Host "[5/5] WiX not found in PATH; skipping EXE installer build (App-Image and Portable ZIP created)." -ForegroundColor Yellow
}

# Generate SHA-256 Checksums for dist files
Get-ChildItem -Path "dist" -Filter "*.exe" | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)" | Set-Content "$($_.FullName).sha256"
}
Get-ChildItem -Path "dist" -Filter "*.zip" | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)" | Set-Content "$($_.FullName).sha256"
}

Write-Host "[SUCCESS] Windows standalone packages created successfully!" -ForegroundColor Green
Get-ChildItem -Path "dist" -Include "*.exe", "*.zip", "*.sha256" -Recurse | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize

# Optional deployment to SourceForge
if ($DeploySourceForge) {
    Write-Host "🚀 Deploying artifacts to SourceForge ($SourceForgeProject/v$appVersion)..." -ForegroundColor Cyan
    $keyFile = "$HOME\.ssh\sf_deploy_key"
    if (-not (Test-Path $keyFile)) {
        Write-Error "SourceForge SSH deploy key not found at: $keyFile"
        exit 1
    }

    $batch = @(
        "-mkdir /home/frs/project/$SourceForgeProject/v$appVersion"
    )
    Get-ChildItem -Path "dist" -Include "*.exe", "*.zip", "*.sha256" | ForEach-Object {
        $cleanPath = $_.FullName -replace '\\', '/'
        $batch += "put `"$cleanPath`" /home/frs/project/$SourceForgeProject/v$appVersion/"
    }
    $batch += "quit"
    
    $batchFile = "$env:TEMP\sftp_batch_upload.txt"
    $batch | Out-File -FilePath $batchFile -Encoding ascii
    sftp -i "$keyFile" -o StrictHostKeyChecking=no -b "$batchFile" "$SourceForgeUser@frs.sourceforge.net"
    Remove-Item -Force $batchFile
    Write-Host "[SUCCESS] Artifacts successfully uploaded to SourceForge!" -ForegroundColor Green
}
