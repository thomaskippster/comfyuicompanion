module.exports = {
  run: [
    {
      method: "shell.run",
      params: {
        conda: "env",
        message: [
          process.platform === 'win32' ? 'update.bat' : 'bash update.sh'
        ],
        title: "Updating Companion for ComfyUI"
      }
    },
    {
      method: "notify",
      params: {
        html: "Update complete! You can now start the application."
      }
    }
  ]
}
