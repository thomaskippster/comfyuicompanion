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
    :: Den aktuellen Pfad an das Skript übergeben, damit dieser beibehalten wird
    powershell -Command "Start-Process '%~f0' -ArgumentList '\"%cd%\"' -Verb RunAs"
    exit /b

:runAntigravity
    :: Wechselt in das übergebene Verzeichnis (falls vorhanden) oder in den Skriptordner
    if not "%~1"=="" (
        cd /d "%~1"
    ) else (
        cd /d "%~dp0"
    )
    
    title Antigravity CLI (Admin)
    echo Starte Antigravity CLI (agy) im vollautomatischen YOLO-Modus...
    
    :: --- DER TRICK FÜR DEN YOLO MODUS ---
    :: WICHTIG: Kein "." und kein "-y". Die neuen Argumente für agy lauten wie folgt:
    call agy --dangerously-skip-permissions
    :: ------------------------------------

    :: Verhindert, dass sich das Fenster sofort schließt, falls Antigravity beendet wird
    pause