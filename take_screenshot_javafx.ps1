[CmdletBinding()]
param (
    [string]$WindowTitlePattern = "*",
    [string]$ProcessName = "java*",
    [string]$OutputPath = ".agy_visual\current_state.png"
)

Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue

$visualDir = Split-Path -Path $OutputPath -Parent
if ($visualDir -and (-not (Test-Path $visualDir))) {
    New-Item -ItemType Directory -Path $visualDir -Force | Out-Null
}

$csharpCode = @"
using System;
using System.Runtime.InteropServices;
public class FXWindowCapture {
    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }
}
"@
if (-not ([System.Management.Automation.PSTypeName]'FXWindowCapture').Type) {
    Add-Type -TypeDefinition $csharpCode -ErrorAction SilentlyContinue
}

# 1. Search for Java / JavaFX process with a window
$javaProc = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue | Where-Object { 
    $_.MainWindowHandle -ne [IntPtr]::Zero -and ($_.MainWindowTitle -like $WindowTitlePattern -or $_.MainWindowTitle.Length -gt 0)
} | Select-Object -First 1

$rect = New-Object FXWindowCapture+RECT
$captured = $false

if ($javaProc) {
    Write-Host "JavaFX Prozess gefunden: $($javaProc.ProcessName) (PID: $($javaProc.Id), Title: '$($javaProc.MainWindowTitle)')"
    try {
        [FXWindowCapture]::SetForegroundWindow($javaProc.MainWindowHandle) | Out-Null
        Start-Sleep -Milliseconds 300
        if ([FXWindowCapture]::GetWindowRect($javaProc.MainWindowHandle, [ref]$rect)) {
            $width = $rect.Right - $rect.Left
            $height = $rect.Bottom - $rect.Top
            if ($width -gt 50 -and $height -gt 50) {
                $bmp = New-Object System.Drawing.Bitmap($width, $height)
                $graphics = [System.Drawing.Graphics]::FromImage($bmp)
                $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($width, $height)))
                $graphics.Dispose()
                $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
                $bmp.Dispose()
                Write-Host "JavaFX-Fenster erfolgreich gespeichert: $OutputPath ($width x $height)"
                $captured = $true
            }
        }
    } catch {
        Write-Warning "Fenster-Capture fehlgeschlagen: $_"
    }
}

if (-not $captured) {
    try {
        Write-Host "Erstelle Desktop-Snapshot..."
        $screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
        $bmp = New-Object System.Drawing.Bitmap($screen.Width, $screen.Height)
        $graphics = [System.Drawing.Graphics]::FromImage($bmp)
        $graphics.CopyFromScreen($screen.Left, $screen.Top, 0, 0, $screen.Size)
        $graphics.Dispose()
        $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Write-Host "Screen-Snapshot gespeichert: $OutputPath ($($screen.Width) x $($screen.Height))"
    } catch {
        Write-Host "Desktop Screen-Capture im aktuellen Modus nicht verfügbar (z. B. Headless). Verwende TestFX 'VisualCaptureTest' für off-screen Renderings."
    }
}
