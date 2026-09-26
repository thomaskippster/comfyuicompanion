# 🎨 Civitai Article Draft: "Stop Wasting SSD Space: The Free ComfyUI Model Manager You Need"

> **Wo posten?** Auf [Civitai.com](https://civitai.com) -> Oben rechts auf **Create** -> **Article** oder **Publish Tool**.  
> **Tags:** `ComfyUI`, `Workflow`, `Tools`, `Guide`, `Software`, `Optimization`

---

## Titel:
**Never Run Out of SSD Space in ComfyUI: Automated Model Downloader & Cold Archive (ComfyUI Companion)**

## Cover-Bild:
Lade `assets/icon.png` oder einen Screenshot deiner Companion-App hoch.

---

## Artikel-Inhalt:

### The Problem Every ComfyUI Creator Faces
If you generate with SDXL, SD 1.5, and now modern models like **Flux.1**, your fast NVMe SSD is probably constantly filled to the brim. Checkpoints range from 6GB to 24GB each, and downloading hundreds of LoRAs quickly eats up 500GB+ of storage.

Furthermore, every time you discover an awesome workflow on Civitai and drag it into ComfyUI, you are greeted by red missing nodes or have to manually search for 5 different LoRAs and checkpoints across the web.

To fix this, I developed **ComfyUI Companion** — a 100% free, open-source desktop application that bridges directly with ComfyUI.

---

### What Can ComfyUI Companion Do?

#### 1. 🚀 1-Click Canvas Bridge
You don't need to manually export JSON files. ComfyUI Companion adds a small, clean floating **Rocket button (`🚀`)** right into your ComfyUI web canvas.
Clicking it sends your active workflow to the companion app, which instantly scans:
- Which models are installed?
- Which models are archived on your slow drive?
- Which models need to be downloaded from Civitai or Hugging Face?

#### 2. 📦 Cold Archive (Reclaim Your Fast SSD)
Do you really need all 50 Flux LoRAs and SDXL checkpoints on your primary SSD simultaneously?
- Offload rarely used models to a secondary HDD or external drive.
- When an imported workflow requires them, ComfyUI Companion automatically detects them and restores or symlinks them back to your models directory in **one click**.

#### 3. 🧠 Offline AI Model Detection (Local Gemma 2B)
Sometimes workflow nodes have cryptic filenames. ComfyUI Companion embeds a local **Gemma 2B model** (running completely offline on CPU, zero cloud API keys required) to analyze node metadata, reconstruct missing model names, determine whether it's a LoRA or ControlNet, and query Civitai automatically.

#### 4. 👯 AutoV1 Fast Hash Duplicate Scanner
Don't let duplicate downloads with different names clutter your drive. Using Civitai's **AutoV1 100MB chunk hashing**, ComfyUI Companion scans thousands of gigabytes in seconds, detecting redundant copies and truncated/corrupted downloads.

#### 5. 🎬 Story-to-Video Architect
Includes a timeline production suite with local script writing, local Piper TTS voiceovers, and automated FFmpeg video stitching.

---

### How to Install (Easy 1-Click Setup)

#### Option 1: Pinokio (Easiest)
If you use [Pinokio](https://pinokio.computer):
1. Open Pinokio and paste: `https://github.com/thomaskippster/comfyuicompanion`
2. Click **Install** -> **Start**. Pinokio takes care of all dependencies automatically!

#### Option 2: Pre-compiled JAR
1. Ensure you have **Java 21** installed.
2. Download `comfyuicompanion.jar` from [GitHub Releases](https://github.com/thomaskippster/comfyuicompanion/releases).
3. Double-click or run: `java -jar comfyuicompanion.jar`.

---

### Links & Support
- ⭐ **GitHub Repository:** [https://github.com/thomaskippster/comfyuicompanion](https://github.com/thomaskippster/comfyuicompanion)
- 📥 **Releases:** [GitHub Releases](https://github.com/thomaskippster/comfyuicompanion/releases)
- ⚡ **Pinokio Page:** [Pinokio Package](https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion)

If this tool saves your SSD space and workflow time, please drop a Star on GitHub! Happy creating! 🚀
