module.exports = {
  version: "2.0",
  title: "Companion for ComfyUI",
  description: "AI-powered model downloader, workflow bridge, timeline video editor, and manager for ComfyUI.",
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
      { icon: "fa-solid fa-trash-can", text: "Reset", href: "reset.js", confirm: "Are you sure you want to reset the installation? This will delete the local environment and build files." }
    ]
    if (installed) {
      results.push({ icon: "fa-solid fa-plug", text: "Reinstall", href: "install.json" })
    } else {
      results.unshift({ icon: "fa-solid fa-plug", text: "Install", href: "install.json" })
    }
    return results
  }
}
