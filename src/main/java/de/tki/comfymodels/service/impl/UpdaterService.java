package de.tki.comfymodels.service.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class UpdaterService {

    public static class RepoStatus {
        public String name;
        public File path;
        public String currentBranch;
        public String currentCommit;
        public String remoteCommit;
        public int behindCount;
        public boolean updateAvailable;
        public String error;
    }

    @Autowired
    private ConfigService configService;

    public List<RepoStatus> scanRepositories() {
        List<RepoStatus> list = new ArrayList<>();
        String comfyPathStr = configService.getComfyUIPath();
        if (comfyPathStr == null || comfyPathStr.isEmpty()) return list;

        File comfyRoot = new File(comfyPathStr);
        if (!comfyRoot.exists() || !comfyRoot.isDirectory()) return list;

        // 1. Scan ComfyUI Core
        RepoStatus coreStatus = checkRepo(comfyRoot, "ComfyUI Core");
        if (coreStatus != null) {
            list.add(coreStatus);
        }

        // 2. Scan Custom Nodes
        File customNodesDir = new File(comfyRoot, "custom_nodes");
        if (customNodesDir.exists() && customNodesDir.isDirectory()) {
            File[] children = customNodesDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        File gitFolder = new File(child, ".git");
                        if (gitFolder.exists()) {
                            RepoStatus nodeStatus = checkRepo(child, child.getName());
                            if (nodeStatus != null) {
                                list.add(nodeStatus);
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    private RepoStatus checkRepo(File dir, String displayName) {
        RepoStatus status = new RepoStatus();
        status.name = displayName;
        status.path = dir;
        
        File gitFolder = new File(dir, ".git");
        if (!gitFolder.exists()) {
            status.error = "Not a git repository (missing .git)";
            status.currentBranch = "N/A";
            status.currentCommit = "N/A";
            status.remoteCommit = "N/A";
            status.updateAvailable = false;
            return status;
        }

        try (Git git = Git.open(dir)) {
            Repository repository = git.getRepository();
            // Fetch first to get latest updates
            try {
                git.fetch().setRemote("origin").call();
            } catch (Exception e) {
                status.error = "Fetch failed: " + e.getMessage();
            }
            
            String branch = repository.getBranch();
            status.currentBranch = branch != null ? branch : "N/A";
            
            ObjectId head = repository.resolve("HEAD");
            status.currentCommit = head != null ? (head.name().length() > 7 ? head.name().substring(0, 7) : head.name()) : "unknown";
            
            BranchTrackingStatus tracking = null;
            if (branch != null) {
                try {
                    tracking = BranchTrackingStatus.of(repository, branch);
                } catch (Exception e) {
                    // ignore tracking error
                }
            }
            if (tracking != null) {
                status.behindCount = tracking.getBehindCount();
                status.updateAvailable = tracking.getBehindCount() > 0;
                ObjectId remoteHead = repository.resolve("refs/remotes/origin/" + branch);
                status.remoteCommit = remoteHead != null ? (remoteHead.name().length() > 7 ? remoteHead.name().substring(0, 7) : remoteHead.name()) : "unknown";
            } else {
                status.updateAvailable = false;
                status.remoteCommit = status.currentCommit;
            }
        } catch (Exception e) {
            status.error = "Git error: " + e.getMessage();
        }
        return status;
    }

    public void updateRepository(RepoStatus repo, Consumer<String> logCallback) {
        logCallback.accept("Updating " + repo.name + "...\n");
        File gitFolder = new File(repo.path, ".git");
        if (!gitFolder.exists()) {
            logCallback.accept("❌ Not a git repository: " + repo.path.getAbsolutePath() + "\n");
            return;
        }

        try (Git git = Git.open(repo.path)) {
            Repository repository = git.getRepository();
            if (repository.resolve("HEAD") == null) {
                logCallback.accept("⚠️ Unborn branch detected. Fetching remote references...\n");
                git.fetch().setRemote("origin").call();
                
                String targetBranch = "master";
                ObjectId remoteHead = repository.resolve("refs/remotes/origin/main");
                if (remoteHead != null) {
                    targetBranch = "main";
                } else {
                    remoteHead = repository.resolve("refs/remotes/origin/master");
                }
                
                if (remoteHead == null) {
                    try {
                        java.util.List<org.eclipse.jgit.lib.Ref> refs = git.branchList()
                                .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                                .call();
                        for (org.eclipse.jgit.lib.Ref ref : refs) {
                            String name = ref.getName();
                            if (name.startsWith("refs/remotes/origin/")) {
                                remoteHead = ref.getObjectId();
                                targetBranch = name.substring("refs/remotes/origin/".length());
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                
                if (remoteHead != null) {
                    logCallback.accept("Creating and checking out branch '" + targetBranch + "' pointing to remote commit...\n");
                    
                    String localBranchRef = "refs/heads/" + targetBranch;
                    org.eclipse.jgit.lib.RefUpdate refUpdate = repository.updateRef(localBranchRef);
                    refUpdate.setNewObjectId(remoteHead);
                    org.eclipse.jgit.lib.RefUpdate.Result res = refUpdate.update();
                    
                    org.eclipse.jgit.lib.RefUpdate headUpdate = repository.updateRef("HEAD");
                    headUpdate.link(localBranchRef);
                    
                    git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
                    logCallback.accept("✅ Checked out branch " + targetBranch + "\n");
                } else {
                    logCallback.accept("❌ No remote branches found. Repository has no commits and remote is empty.\n");
                }
            } else {
                git.pull().call();
                logCallback.accept("✅ Git pull completed for " + repo.name + "\n");
            }
        } catch (Exception e) {
            logCallback.accept("❌ Git pull failed for " + repo.name + ": " + e.getMessage() + "\n");
            return;
        }

        // Install requirements if python is configured and requirements.txt exists
        File reqs = new File(repo.path, "requirements.txt");
        if (reqs.exists()) {
            String pythonPath = configService.getPythonPath();
            if (pythonPath != null && !pythonPath.isEmpty()) {
                logCallback.accept("⚙️ Found requirements.txt. Installing dependencies via pip...\n");
                try {
                    ProcessBuilder pb = new ProcessBuilder(pythonPath, "-m", "pip", "install", "-r", "requirements.txt");
                    pb.directory(repo.path);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logCallback.accept("  [pip] " + line + "\n");
                        }
                    }
                    int code = p.waitFor();
                    if (code == 0) {
                        logCallback.accept("✅ Pip install succeeded!\n");
                    } else {
                        logCallback.accept("⚠️ Pip install completed with code " + code + "\n");
                    }
                } catch (Exception e) {
                    logCallback.accept("❌ Failed to run pip install: " + e.getMessage() + "\n");
                }
            } else {
                logCallback.accept("⚠️ Python path not configured, skipped pip install.\n");
            }
        }
    }

    public void repairEnvironment(Consumer<String> logCallback) {
        String pythonPath = configService.getPythonPath();
        if (pythonPath == null || pythonPath.isEmpty()) {
            logCallback.accept("❌ Python path not configured in settings. Cannot run repair.\n");
            return;
        }

        logCallback.accept("⚙️ Starting Environment Repair...\n");
        logCallback.accept("🐍 Python executable: " + pythonPath + "\n\n");

        String comfyPathStr = configService.getComfyUIPath();
        if (comfyPathStr == null || comfyPathStr.isEmpty()) {
            logCallback.accept("❌ ComfyUI path not configured. Cannot repair.\n");
            return;
        }

        File comfyRoot = new File(comfyPathStr);
        if (!comfyRoot.exists() || !comfyRoot.isDirectory()) {
            logCallback.accept("❌ ComfyUI directory does not exist: " + comfyPathStr + "\n");
            return;
        }

        // 1. Repair Core ComfyUI requirements
        File coreReqs = new File(comfyRoot, "requirements.txt");
        if (coreReqs.exists()) {
            logCallback.accept("📦 Installing/Upgrading ComfyUI core requirements...\n");
            runPipInstall(pythonPath, coreReqs, logCallback);
        }

        // 2. Repair Custom Nodes requirements
        File customNodesDir = new File(comfyRoot, "custom_nodes");
        if (customNodesDir.exists() && customNodesDir.isDirectory()) {
            File[] nodes = customNodesDir.listFiles();
            if (nodes != null) {
                for (File node : nodes) {
                    if (node.isDirectory()) {
                        File nodeReqs = new File(node, "requirements.txt");
                        if (nodeReqs.exists()) {
                            logCallback.accept("🔌 Installing requirements for custom node: " + node.getName() + "...\n");
                            runPipInstall(pythonPath, nodeReqs, logCallback);
                        }
                    }
                }
            }
        }

        logCallback.accept("✅ Environment repair completed successfully!\n");
    }

    private void runPipInstall(String pythonPath, File requirementsFile, Consumer<String> logCallback) {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "-m", "pip", "install", "--upgrade", "-r", requirementsFile.getName());
            pb.directory(requirementsFile.getParentFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCallback.accept("  [pip] " + line + "\n");
                }
            }
            int code = p.waitFor();
            if (code == 0) {
                logCallback.accept("  ✅ Successfully installed requirements in " + requirementsFile.getParentFile().getName() + "\n\n");
            } else {
                logCallback.accept("  ⚠️ Completed with code " + code + " in " + requirementsFile.getParentFile().getName() + "\n\n");
            }
        } catch (Exception e) {
            logCallback.accept("  ❌ Failed: " + e.getMessage() + "\n\n");
        }
    }
}
