# ==============================================================================
# ComfyUI Companion - Windows Package Manager (WinGet) Submission Tool
# Submits the validated manifest to the official microsoft/winget-pkgs repository.
# ==============================================================================

param (
    [string]$Version = "2.1.0",
    [string]$GitHubToken = ""
)

$ErrorActionPreference = "Stop"
if ($PSScriptRoot) { Set-Location $PSScriptRoot }

# Determine project version dynamically from pom.xml if not explicitly passed
if (-not $PSBoundParameters.ContainsKey('Version') -and (Test-Path "pom.xml")) {
    try {
        [xml]$pomXml = Get-Content "pom.xml"
        if ($pomXml.project.version) {
            $Version = $pomXml.project.version
        }
    } catch {
        Write-Warning "Could not parse pom.xml version, using $Version"
    }
}

$manifestDir = "winget/manifests/t/Tki/ComfyUICompanion/$Version"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " ComfyUI Companion WinGet Submission Tool (v$Version)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

if (-not (Test-Path $manifestDir)) {
    Write-Host "Manifest directory not found: $manifestDir" -ForegroundColor Yellow
    Write-Host "Generating packages and manifests first..." -ForegroundColor Cyan
    & .\package-windows-native.ps1
}

if (-not (Test-Path $manifestDir)) {
    Write-Error "Failed to locate or generate manifests in $manifestDir"
    exit 1
}

# Step 1: Validate manifests with winget CLI
Write-Host "[1/3] Validating Manifests via WinGet..." -ForegroundColor Cyan
if (Get-Command winget -ErrorAction SilentlyContinue) {
    winget validate "$manifestDir"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Manifest validation failed. Please check YAML formatting."
        exit 1
    }
    Write-Host "Manifest validation passed successfully!" -ForegroundColor Green
} else {
    Write-Warning "WinGet CLI not found; skipping local schema validation."
}

# Step 2: Ensure wingetcreate is installed
Write-Host "[2/3] Checking WingetCreate Tool..." -ForegroundColor Cyan
if (-not (Get-Command wingetcreate -ErrorAction SilentlyContinue)) {
    Write-Host "Installing Microsoft.WingetCreate..." -ForegroundColor Cyan
    winget install Microsoft.WingetCreate --accept-source-agreements --accept-package-agreements
}

# Step 3: Submission instructions and execution
Write-Host "[3/3] Submitting Manifest to microsoft/winget-pkgs..." -ForegroundColor Cyan
if ($GitHubToken) {
    Write-Host "Submitting using provided GitHub token..." -ForegroundColor Cyan
    wingetcreate submit "$manifestDir" --token "$GitHubToken"
} else {
    Write-Host "To submit your package pull request to microsoft/winget-pkgs, run:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  wingetcreate submit `"$manifestDir`"" -ForegroundColor White
    Write-Host ""
    Write-Host "Or provide your GitHub Personal Access Token (PAT with 'public_repo' scope):" -ForegroundColor Yellow
    Write-Host "  .\submit-to-winget.ps1 -GitHubToken `"<YOUR_TOKEN>`"" -ForegroundColor White
    Write-Host ""
    
    $choice = Read-Host "Would you like to run 'wingetcreate submit' now? (Y/N)"
    if ($choice -match '^[Yy]') {
        wingetcreate submit "$manifestDir"
    }
}
