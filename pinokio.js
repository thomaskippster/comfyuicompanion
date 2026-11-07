module.exports = {
  version: "2.1.0",
  title: "Companion for ComfyUI",
  description: "AI-powered model downloader, workflow bridge, timeline video editor, and manager for ComfyUI. Features modular Video Diffusion Strategies (Wan 2.2, Wan 2.1 I2V, LTX, Hunyuan), TikTok/Reels 9:16 vertical presets, and automatic missing-node sanitization.",
  icon: "assets/icon.png",
  menu: async (kernel, info) => {
    let installed = await kernel.exists("target/comfyuicompanion.jar")
    let running = await kernel.running("start.json")
    if (running) {
      return [
        { icon: "fa-solid fa-spin fa-circle-notch", text: "Running", href: "start.json" }
      ]
    }
    let results = [
      { icon: "fa-solid fa-play", text: "Start", href: "start.json" },
      { icon: "fa-solid fa-rotate", text: "Update", href: "update.json" },
      { icon: "fa-solid fa-trash-can", text: "Reset", href: "reset.js", confirm: "Are you sure you want to reset the installation? This will reset the repository to origin/main and delete the local environment and build files." }
    ]
    if (installed) {
      results.push({ icon: "fa-solid fa-plug", text: "Reinstall", href: "install.json" })
    } else {
      results.unshift({ icon: "fa-solid fa-plug", text: "Install", href: "install.json" })
    }
    results.push({ icon: "fa-brands fa-github", text: "GitHub Releases & Repo", href: "https://github.com/thomaskippster/comfyuicompanion", target: "_blank" })
    return results
  }
}
