module.exports = {
  run: [
    {
      method: "shell.run",
      params: {
        message: [
          "git fetch origin",
          "git checkout -B main origin/main",
          "git reset --hard origin/main"
        ],
        title: "Resetting Git Repository"
      }
    },
    {
      method: "fs.rm",
      params: {
        path: "env"
      }
    },
    {
      method: "fs.rm",
      params: {
        path: "target"
      }
    }
  ]
}
