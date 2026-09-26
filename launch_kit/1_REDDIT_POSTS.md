# 🔴 Reddit Launch Posts

> **Wichtig:** Lade beim Erstellen des Posts immer das Video `assets/comfyuicompanion.mp4` oder `assets/demo_social.gif` als Video/Bild-Post hoch, und setze den Text in die Beschreibung oder den ersten Kommentar. Visuelle Posts bekommen auf Reddit 5- bis 10-mal mehr Upvotes!

---

## Post 1: Für r/comfyui

**Subreddit:** `r/comfyui`  
**Flair:** `Workflow / Tool` oder `News / Update`  
**Titel:**  
`I built an open-source ComfyUI Companion app to fix "Missing Model Hell" & SSD storage overload (Cold Archive, 1-Click Canvas Bridge, Local AI & Pinokio)`

**Post-Body:**
```markdown
Hey everyone! 👋

Like many of you, my fast NVMe SSD was constantly screaming under 20GB+ Flux checkpoints, SD3, and dozens of massive LoRA folders. On top of that, every time I downloaded an awesome workflow from the community, I spent 20 minutes manually hunting down missing models across Civitai and Hugging Face.

To solve this once and for all, I built **ComfyUI Companion** — a free, open-source desktop app that automates model management, canvas syncing, and storage optimization.

### 🌟 Core Features:

* 🚀 **1-Click Canvas Bridge:** Adds a small Rocket button directly inside your ComfyUI web canvas. One click syncs your active workflow to the companion app, instantly showing which models are missing, installed, or archived.
* 📦 **Cold Archive & 1-Click Restore:** Offload heavy models (Flux, SDXL, SD3) to a slower HDD or external drive. When you load a workflow that needs them, restore or symlink them back to your models folder with a single click.
* 🧠 **Local Gemma 2B AI (Zero Cloud):** Reconstructs missing model names from node metadata and identifies target directories (distinguishing LoRA vs ControlNet) 100% offline via an embedded local Gemma 2B model.
* 👯 **AutoV1 Fast Duplicate Detection:** Hashes models in seconds (Civitai AutoV1 100MB chunk hashing) to find duplicate files eating storage under different filenames, plus corrupt file validation.
* 🔌 **Server Process Control:** Auto-detects virtual environments, streams ComfyUI server logs, auto-reloads your browser tab after restarts, and clears zombie port collisions.
* 🎬 **Video Architect & Timeline:** Timeline workspace with local Gemma 2B script parsing, local Piper TTS voiceovers, and automated FFmpeg master stitching.
* ⚡ **1-Click Pinokio Install:** Available as a pre-compiled JAR and 1-click Pinokio app!

### 📥 Links & Getting Started:
* **GitHub (MIT License):** https://github.com/thomaskippster/comfyuicompanion
* **Pinokio 1-Click Install:** https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion
* **Pre-compiled JAR:** https://github.com/thomaskippster/comfyuicompanion/releases

It's completely free and runs offline. I'd love to hear your feedback, workflow edge cases, or feature requests! ⭐
```

---

## Post 2: Für r/StableDiffusion

**Subreddit:** `r/StableDiffusion`  
**Flair:** `Resource` oder `Software`  
**Titel:**  
`Free Tool: ComfyUI Companion – Automated Model Downloader, Cold Storage Archiver (SSD Saver) and Canvas Bridge`

**Post-Body:**
```markdown
Hi all!

Managing hundreds of gigabytes of models across SD 1.5, SDXL, and Flux has become a nightmare. 

I created **ComfyUI Companion**, an open-source companion app designed to eliminate the manual file juggling and missing model errors when running ComfyUI.

### 💡 How it helps your workflow:
1. **Never run out of SSD space:** Move rarely used 10GB–30GB checkpoints to an external HDD cold archive. When a workflow needs them, the tool detects it and restores them in 1 click.
2. **Instant Canvas Sync:** A floating rocket button right on the ComfyUI canvas sends your active workflow to the companion app to auto-detect missing models and queue downloads from Civitai / Hugging Face.
3. **AutoV1 Fast Hash:** Scans thousands of models in seconds using 100MB chunk hashing to detect duplicates and corrupted downloads.
4. **100% Local & Private:** Embedded local Gemma 2B AI engine for smart model name reconstitution, plus local Piper TTS for video generation workflows.
5. **No Python setup required:** Runs via 1-click Pinokio or pre-compiled executable JAR.

Check it out on GitHub: https://github.com/thomaskippster/comfyuicompanion  
Pinokio 1-Click: https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion

Hope this saves you as much SSD space and headache as it did for me!
```

---

## Post 3: Für r/LocalLLaMA

**Subreddit:** `r/LocalLLaMA`  
**Flair:** `Discussion` oder `Tool`  
**Titel:**  
`I built a desktop app that embeds Gemma-2-2B locally via llama.cpp Java wrapper to parse node graphs & automate ComfyUI model management`

**Post-Body:**
```markdown
Hey r/LocalLLaMA!

Wanted to share a practical real-world local LLM integration:

I developed **ComfyUI Companion** (open source), and one of the biggest challenges was parsing messy ComfyUI workflow JSONs where custom node model names are truncated or ambiguous (e.g. distinguishing whether an obscure filename belongs in `loras`, `checkpoints`, or `controlnet`).

Instead of making cloud API calls, I embedded **Gemma-2-2B-Instruct** running directly on CPU via llama.cpp Java bindings:
- Runs 100% locally with zero cloud dependence.
- Automatically unloads from RAM after 5 minutes of inactivity to keep system resources free for GPU inference.
- Reconstructs missing model names and query parameters for automated Civitai/Hugging Face lookup.
- Also powers a local script-to-video timeline in the built-in Video Architect tab with Piper TTS.

Code and architecture details:
👉 GitHub: https://github.com/thomaskippster/comfyuicompanion
```
