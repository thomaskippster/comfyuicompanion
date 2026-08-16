# ComfyUI Companion 🚀

<p align="center">
  <img src="assets/icon.png" width="128" height="128" alt="ComfyUI Companion Icon" />
</p>

<p align="center">
  <a href="https://github.com/thomaskippster/comfyuicompanion/releases"><img src="https://img.shields.io/github/v/release/thomaskippster/comfyuicompanion?style=for-the-badge&color=blue" alt="Release" /></a>
  <a href="https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion"><img src="https://img.shields.io/badge/Pinokio-1--Click%20Install-FF6B6B?style=for-the-badge&logo=pinokio&logoColor=white" alt="Pinokio 1-Click Install" /></a>
  <a href="https://www.oracle.com/java/technologies/downloads/#java21"><img src="https://img.shields.io/badge/Java-21+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21+" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License" /></a>
</p>

> **Automate your model management and keep your creative flow uninterrupted.**

<p align="center">
  <video src="https://github.com/thomaskippster/comfyuicompanion/raw/main/assets/comfyuicompanion.mp4" controls width="100%" poster="https://github.com/thomaskippster/comfyuicompanion/raw/main/assets/icon.png"></video>
</p>

ComfyUI Companion is a local companion app designed to eliminate the manual frustration of managing model files for ComfyUI. Instead of hunting down models, downloading them to temporary folders, and manually moving them to `custom_nodes`, `checkpoints`, `loras`, or `vae` folders, ComfyUI Companion automates the entire process. 

From **AI-powered workflow analysis** and **one-click canvas integration** to **reclaiming disk space with cold archives**, this tool ensures your ComfyUI environment is optimized, secure, and always ready to run.


---

## 🎨 Core Features Deep-Dive

### 1. 🚀 1-Click Workflow Import (The Bridge)
No more saving JSON files and dragging them manually. ComfyUI Companion includes a lightweight custom node extension called the **Bridge**.
* **Direct Canvas Integration:** Adds a modern floating Rocket button (`🚀`) directly into the ComfyUI interface.
* **Instant Canvas Syncing:** Clicking the button serializes the current active canvas workflow and securely sends it to the Companion app via a local API.
* **On-the-Fly Detection:** The app instantly scans the incoming workflow, detects which models are required, and checks if they are installed, archived, or missing.

### 2. 🧠 AI-Powered Workflow Analysis
Sometimes workflow files lack metadata, or custom node filenames don't match typical model names. ComfyUI Companion solves this with smart AI analysis:
* **Gemini & Local AI Support:** Connects securely to Google Gemini AI or a local LLM (like Qwen or Llama via Ollama/Local AI) to inspect node configurations, widget values, and metadata.
* **Smart Name Reconstitution:** The AI reconstructs missing model names, identifies target folders (e.g., distinguishing between a ControlNet and a LoRA), and suggests potential search queries.
* **Direct Online Lookup:** Performs automated searches on **Civitai** and **Hugging Face** to find matching download links instantly.

### 3. 📦 Cold Archive & 1-Click Restoration
High-end models take up massive SSD space (SDXL checkpoints, Flux models, etc. can be 10GB–30GB each). The **Archive Manager** helps you keep your SSD clean:
* **Cold Storage Offloading:** Move rarely used models to a slower external HDD or cold-storage directory with a single click.
* **Automatic Detection:** When you load or import a workflow that requires a model currently in the archive, ComfyUI Companion recognizes it.
* **1-Click Restoration:** Click "Restore" and the app automatically copies or symlinks the model back to its correct ComfyUI folder, making it instantly available for inference.

### 4. 👯 Duplicate Detection & Fast Hashing (AutoV1)
Are duplicate models eating up your storage space under different filenames?
* **SHA-256 Fingerprinting:** Scans and hashes your models to identify duplicate files, helping you clean up redundant copies.
* **AutoV1 Fast Hashing:** Full hashing of 10GB+ files can take minutes and cause high CPU/disk utilization. With **Fast Hashing** enabled, the app calculates the SHA-256 hash using only the first **100MB** of the file, matching Civitai's AutoV1 format. This allows thousands of models to be scanned and verified in seconds.
* **Corrupt File Detection:** Automatically checks if model downloads were truncated or corrupted, verifying file headers (like `.safetensors`, `.ckpt`, or `.bin`) before ComfyUI tries to load them.

### 5. 🔌 ComfyUI Process & Lifecycle Control
Take full control of your ComfyUI server directly from the Companion app:
* **Dynamic Discovery:** Automatically scans your system to locate ComfyUI. It identifies virtual environments (`.venv`), standard directories (WSL, standalone installations, Pinokio, or manual git clones), and automatically maps execution commands.
* **Real-time Log Streaming:** View ComfyUI server console output directly inside the Companion app.
* **Browser Sync & Auto-Reload:** Whenever the ComfyUI server restarts, the Companion automatically reloads your open browser tab as soon as the WebSocket reconnects, eliminating manual page-refreshing. It also blocks duplicate browser tab launches.

### 6. 🛡️ Local Encrypted Vault
Your API keys and download tokens are sensitive. ComfyUI Companion features a secure **Credentials Vault**:
* **AES-256 Encryption:** All keys (Civitai API keys, Hugging Face tokens, Gemini API credentials) are encrypted locally using industrial-grade AES encryption.
* **Master Password Lock:** The vault is unlocked via a master password upon app startup, ensuring your credentials are never stored in plain text.

### 7. 🎨 Prompt Lab & Architecture-Tailored AI Optimization
An interactive environment to design, experiment with, and optimize prompts:
* **Style Filters & Suggestions:** Quickly apply presets like Photorealistic, Oil Painting, Unreal Engine 5 Render, Anime, Dark Fantasy/Cinematic, and Watercolor Sketch.
* **Optional Environmental Context:** Pick from curated environments or select `None (Optional)` to skip environmental context formatting.
* **Model-Specific AI Rewrites:** Built-in "Optimize with AI" utilizes Gemini or local LLMs to rephrase and restructure prompts tailored to the specific target architecture of your selected ComfyUI model (e.g. SD 1.5, SDXL, Flux).

### 8. 📊 Visual Workflow Graph Preview
Inspect your workflow structure directly inside the Companion application:
* **Node & Connection Mapping:** Visualizes node configurations, links, and flow directions on an interactive canvas.
* **Immediate Updates:** Renders the graph layout instantly when dragging and dropping a workflow file or syncing via the Bridge rocket button.

### 9. 🔍 Model Update & Upgrade Checker
Keep your local model library up-to-date with new versions released on Civitai:
* **Auto-Matching:** Scans your local models using hashes and queries the Civitai API.
* **One-Click Upgrades:** Displays newer versions, changelogs, and allows queueing downloads for newer model iterations directly.

### 10. 🖼️ Asynchronous Output Gallery
A rich visual panel that tracks and manages your generation history:
* **Live Folder Monitoring:** Automatically scans your ComfyUI output directory and lists generated images and videos.
* **Interactive Media Viewer:** View images and play video outputs.
* **Checkboxes & Bulk Operations:** Shift-click or use checkmarks to select multiple outputs for bulk deletion or file operations.
* **Asynchronous Loading:** Uses background threads for scaling and loading thumbnails, keeping the main interface responsive.

### 11. 📈 Real-Time System Monitoring
* **Resource Bars:** Displays real-time GPU VRAM, system RAM, and CPU usage on the main companion interface.
* **Active Hardware Tracking:** Monitors inference system state dynamically.

### 12. 🔌 Auto-Updater for App & Custom Nodes
Keep the companion app and ComfyUI updated without opening a terminal:
* **Automated Git Maintenance:** Checks for newer custom node repositories, executes `git pull`, and triggers dependency setups inside your virtual environment.
* **Self-Updates:** Checks for newer ComfyUI Companion releases and updates the application binaries.

### 13. 🛡️ Port Collision Defense
* **Active Port Cleaning:** Automatically checks if the target ComfyUI server port is already bound.
* **Stale Task Termination:** Terminates zombie python processes or conflicting services on the specified port to ensure a smooth, error-free startup.

### 14. 🗂️ Blueprint Gallery (Model Manager Redesign)
A high-performance visual workflow manager that organizes and displays your reusable templates and node workflows.
* **Smart Cards & Categories:** Parses `*.json` templates from the `companion_blueprints` folder, extracting node metadata, category info, and descriptions to render a modern visual grid.
* **Dynamic Grid Layout:** Features a responsive card layout that automatically wraps to avoid clipping, sorting by model availability (installed first, followed by partially installed, then missing).
* **Local Model Availability Badge:** Scans your model directories and overlays status badges directly on blueprint cards, indicating if all required models are locally ready.
* **Instant Missing Model Downloads:** Detects required models for any given blueprint and provides a one-click download option to fetch them directly.
* **Preview Media Resolution:** Automatically resolves local images or videos (using Video4j/OpenCV) as card backgrounds, with direct fallback querying to the ComfyUI server templates endpoint.

### 15. 🎬 Video Architect: Story-to-Video Production
An advanced timeline-based director and editing workspace designed to build cohesive stories and stitch them into video tracks.
* **Gemma-2-2B Local Script Writer:** Integrates a local **Gemma 2 2B Instruct GGUF** model using the `llama.cpp` Java wrapper. It downloads and runs locally on CPU (preventing conflicts with ComfyUI's VRAM usage) to parse abstract script ideas into a structured JSON timeline of visual scenes and narration cues. The model automatically unloads from RAM after 5 minutes of inactivity to conserve memory.
* **Text-to-Speech Narration (TTS):** Generates voiceovers for timeline scenes automatically using local **Piper TTS** (with automatic Windows binaries/models bootstrap) or online/ComfyUI-integrated **KokoroTTS** / **ElevenLabs** nodes.
* **OpenCV-Powered Visual Enhancement:** Allows frame-by-frame contrast and brightness adjustment on timeline scenes with real-time feedback using Video4j native OpenCV bindings.
* **Automated FFmpeg Stitching (Master Render):** Cuts, trims, and scales clips (handling static images and moving videos) to match narrations, burns subtitle overlays via the FFmpeg `drawtext` filter, and merges segments into a final `master_export.mp4`.

---

## ⚙️ Launch Configurations Reference

The app comes with **10 pre-configured launch profiles**, ordered logically by utility. You can customize them or add your own:

| Profile Name | Launch Arguments | Purpose / Use Case |
| :--- | :--- | :--- |
| **Standard Mode (Default)** | `--normalvram` | Balanced performance and memory usage for general generation. |
| **Beast Mode (High VRAM)** | `--highvram` | Maximizes performance and caching. Ideal for GPUs with 24GB+ VRAM (RTX 3090/4090). |
| **Background / Gaming Mode** | `--lowvram` | Minimizes VRAM usage and aggressively unloads models. Perfect for multitasking or gaming while generating. |
| **Flux & SD3 Optimization** | `--fp8_e4m3fn-text-enc --fp8_e4m3fn-unet` | Forces FP8 precision on UNet and Text Encoders. Saves massive VRAM for heavy modern models (Flux, SD3). |
| **CPU Mode (No GPU)** | `--cpu` | Runs all execution on the CPU. Fallback mode for systems without a dedicated Nvidia/AMD GPU. |
| **Extreme Savings (No VRAM)** | `--novram` | Offloads all models to RAM immediately after inference. |
| **Network Hub (LAN Mode)** | `--listen 0.0.0.0` | Allows other devices on your local network to access your ComfyUI server instance. |
| **AMD / Intel GPU Mode** | `--directml` | Enables DirectML acceleration. Required for AMD Radeon and Intel Arc GPUs on Windows. |
| **WSL / Linux Subsystem** | *WSL command tunnel* | Executes the ComfyUI server within a Windows Subsystem for Linux environment. |
| **Safe Mode (Troubleshooting)**| `--disable-all-custom-nodes` | Starts ComfyUI without loading any custom nodes. Perfect for debugging startup crashes. |

---

## 🚀 Getting Started

### Prerequisites
* **Java 21** or higher
* **Maven** (only if building from source)

---

### Option 1: 📥 Direct Download (SourceForge) — *Recommended*
1. Download the latest pre-compiled build from [SourceForge](https://sourceforge.net/projects/comfyuicompanion/).
2. Run the application:
   ```bash
   java -jar comfyuicompanion.jar
   ```

---

### Option 2: ⚡ Quick Install (Pinokio)
If you use [Pinokio](https://pinokio.computer):
* **1-Click Install:** Click [Install on Pinokio](pinokio://download?uri=https://github.com/thomaskippster/comfyuicompanion) or open the web link [pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion](https://pinokio.computer/item?uri=https://github.com/thomaskippster/comfyuicompanion).
* **Or via Pinokio App:** Open Pinokio, paste `https://github.com/thomaskippster/comfyuicompanion` into the address bar, and click **Download** -> **Install**.
* The script automatically configures OpenJDK 21, Maven, builds the JAR, and manages 1-click start/stop and updates.

---

### Option 3: 🛠️ Manual Build (For Developers)
1. Clone the repository:
   ```bash
   git clone https://github.com/thomaskippster/comfyuicompanion.git
   ```
2. Build the application:
   ```bash
   mvn clean package
   ```
3. Run the executable jar:
   ```bash
   java --enable-native-access=ALL-UNNAMED -jar target/comfyuicompanion.jar
   ```

---

## 🔌 Installing the ComfyUI Bridge Node

To enable the Rocket button (`🚀`) in your ComfyUI canvas:

### Automatic Setup (Easiest)
1. Launch the ComfyUI Companion app.
2. Navigate to **Settings** -> **ComfyUI Bridge...**.
3. Select your main ComfyUI folder and click **Start Installation**.
4. Restart your ComfyUI server.

### Manual Setup
1. Copy the folder `src/main/resources/comfyui-bridge` into your `ComfyUI/custom_nodes/` directory.
2. Rename the copied folder to `comfyuicompanion`.
3. Restart ComfyUI.

---

## 📖 How to Use

1. **Configure Folders:** Open the app, go to **Settings -> Directories** and set your ComfyUI models folder and your preferred Archive folder.
2. **Unlock the Vault:** Create a Master Password and add your API Keys (Civitai, Hugging Face, Gemini) under **Settings -> AI & API Keys**.
3. **Analyze a Workflow:**
   * Drag and drop any `.json` or `.png` workflow into the Companion app window.
   * *OR:* Click the rocket button (`🚀`) in the ComfyUI web canvas.
4. **Download and Restore:**
   * Review the list of models.
   * Click **Start Queue**. Archived models are restored instantly, and missing models are queued and downloaded in parallel.

---

## 🤝 Support & Feedback

If you find this tool helpful, please leave a star ⭐ on the [GitHub Repository](https://github.com/thomaskippster/comfyuicompanion)! 
Feedback, issue reports, and feature suggestions are highly welcome. Feel free to open a ticket or a pull request.
