# 🔌 ComfyUI-Manager Registration Guide

Durch die Aufnahme in den **ComfyUI-Manager** wird dein Tool für Hunderttausende ComfyUI-Nutzer direkt in der Benutzeroberfläche unter **"Install Custom Nodes"** durchsuchbar und mit 1 Klick installierbar.

---

## 📝 Dein JSON-Eintrag für `custom-node-list.json`

Kopiere diesen JSON-Block:

```json
{
  "author": "thomaskippster",
  "title": "ComfyUI Companion Bridge",
  "reference": "https://github.com/thomaskippster/comfyuicompanion",
  "files": [
    "https://github.com/thomaskippster/comfyuicompanion"
  ],
  "install_type": "git-clone",
  "description": "Bridge extension for ComfyUI Companion desktop app. Adds a 1-click canvas rocket button to sync workflows, auto-detect and download missing models, manage SSD cold storage archives, and local AI tools."
}
```

---

## 🚀 Schritt-für-Schritt zur Einreichung (Pull Request)

1. Gehe auf das GitHub-Repository von ComfyUI-Manager:  
   👉 **https://github.com/ltdrdata/ComfyUI-Manager**
2. Klicke oben rechts auf **"Fork"**, um eine Kopie in deinem GitHub-Account zu erstellen.
3. Öffne in deinem Fork die Datei:  
   `custom-node-list.json`
4. Klicke auf das Stift-Icon (✏️ Edit this file).
5. Scrolle ganz nach unten (vor die letzte schließende eckige Klammer `]`) oder füge deinen JSON-Eintrag alphabetisch ein. Vergiss das Komma `,` zwischen den Einträgen nicht!
6. Klicke auf **"Commit changes..."** (Branch: `add-comfyuicompanion-bridge`).
7. Klicke auf **"Contribute"** -> **"Open pull request"**.
8. **Titel des PRs:**  
   `Add ComfyUI Companion Bridge to custom node list`
9. **Beschreibung des PRs:**
   ```text
   Hi Dr. Lt.Data!

   This PR adds the ComfyUI Companion Bridge custom node to the registry.
   It provides direct canvas syncing and missing model detection for the open-source ComfyUI Companion desktop application.

   - Repository: https://github.com/thomaskippster/comfyuicompanion
   - License: MIT
   - Tested on ComfyUI latest.

   Thank you for maintaining this incredible tool!
   ```
10. Klicke auf **"Create pull request"**.

Sobald Dr. Lt.Data den PR mergt, ist dein Node für die weltweite ComfyUI-Community auffindbar!
