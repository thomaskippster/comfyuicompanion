@echo off
:: Prüfen, ob das Skript bereits mit Administratorrechten läuft
net session >nul 2>&1
if %errorLevel% == 0 (
    goto :runAntigravity
) else (
    goto :requestAdmin
)

:requestAdmin
    echo Fordere Administratorrechte an...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b

:runAntigravity
    :: Wechselt in das Verzeichnis, in dem das Skript liegt (optional)
    cd /d "%~dp0"
    title Antigravity CLI (Admin)
    echo Starte Antigravity CLI im vollautomatischen YOLO-Modus...
    
    :: --- DER TRICK FÜR DEN YOLO MODUS ---
    :: Möglichkeit 1: Direkt über den Startparameter (einfachste Variante)
    call agy . -y
    
    :: Möglichkeit 2: Falls der Parameter zickt, lösch das 'call Antigravity -y' oben 
    :: und nutze stattdessen diese zwei Zeilen hier ohne das '::':
    :: set Antigravity_YOLO_MODE=true
    :: call Antigravity
    :: ------------------------------------

    :: Verhindert, dass sich das Fenster sofort schließt, falls Antigravity beendet wird
    pause