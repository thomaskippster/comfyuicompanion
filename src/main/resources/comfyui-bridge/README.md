# ComfyUI Companion - Bridge Extension 🚀

This lightweight custom node extension connects your ComfyUI canvas directly to the **ComfyUI Companion** desktop application.

> [!NOTE]
> There is also a direct Pinokio install and a pre-compiled JAR available – you can find those links on the GitHub repo:
> [https://github.com/thomaskippster/comfyuicompanion](https://github.com/thomaskippster/comfyuicompanion)

---

## 🌟 What is ComfyUI Companion?

ComfyUI Companion is an all-in-one desktop studio that automates model management, cold storage offloading/restoration, local AI prompt optimization, node-free image generation, and timeline video editing for ComfyUI.

## 🚀 Features of this Bridge Extension

* **Direct Canvas Integration:** Adds a floating Rocket button (`🚀`) directly into the ComfyUI web canvas.
* **1-Click Workflow Sync:** Clicking the Rocket button serializes the active canvas and sends it directly to the Companion app for instant missing model detection.
* **Canvas Conversion Relay:** Translates GUI workflows to API execution prompts via `app.graphToPrompt()`.
* **Dynamic Model Refresh:** Triggers cache invalidation in ComfyUI when models are downloaded or restored without needing to restart the server.

---

## 📥 Getting ComfyUI Companion

To use this bridge, the **ComfyUI Companion** desktop application must be running on your system:

* **Pre-compiled JAR:** Download directly from [GitHub Releases](https://github.com/thomaskippster/comfyuicompanion/releases) or [SourceForge](https://sourceforge.net/projects/companion-for-comfyui/).
* **Pinokio 1-Click Install:** Install via [Pinokio Package Post](https://pinokio.co/posts/01m0dh3d006qpf47dx546qrtpd) or [Pinokio Computer](https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion).
* **Source Build:** Clone [https://github.com/thomaskippster/comfyuicompanion](https://github.com/thomaskippster/comfyuicompanion) and run `mvn clean package`.