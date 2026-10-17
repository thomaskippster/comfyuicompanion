package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise service for managing ComfyUI custom node snapshots and performing 1-click rollbacks
 * to prevent dependency breakage and broken node environments.
 */
@Service
public class CustomNodeSnapshotService implements ICustomNodeSnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeSnapshotService.class);

    private final ConfigService configService;
    private final ProcessTracker processTracker;
    private final Path storageDirectory;

    public CustomNodeSnapshotService() {
        this(null, null);
    }

    public CustomNodeSnapshotService(ConfigService configService) {
        this(configService, null);
    }

    @Autowired
    public CustomNodeSnapshotService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this(configService, processTracker, Paths.get(System.getProperty("user.home", "."), ".comfyuicompanion", "snapshots"));
    }

    public CustomNodeSnapshotService(ConfigService configService, ProcessTracker processTracker, Path storageDirectory) {
        this.configService = configService;
        this.processTracker = processTracker;
        this.storageDirectory = storageDirectory != null ? storageDirectory : Paths.get(System.getProperty("user.home", "."), ".comfyuicompanion", "snapshots");
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (Exception e) {
            logger.warn("Could not create snapshot storage directory: {}", e.getMessage());
        }
    }

    @Override
    public EnvironmentSnapshot captureSnapshot(String description, Consumer<String> progressCallback) {
        String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-").replace(".", "-");
        String snapshotId = "snapshot_" + ts;
        log(progressCallback, "📸 Creating snapshot '" + snapshotId + "'...");

        List<NodeGitState> nodes = new ArrayList<>();
        File customNodesDir = getCustomNodesDirectory();

        if (customNodesDir != null && customNodesDir.exists() && customNodesDir.isDirectory()) {
            File[] children = customNodesDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && new File(child, ".git").exists()) {
                        NodeGitState state = inspectRepoState(child);
                        if (state != null) {
                            nodes.add(state);
                            log(progressCallback, "  Recorded node: " + state.folderName() + " (" + state.commitHash() + ")");
                        }
                    }
                }
            }
        }

        String pipFreeze = capturePipFreeze(progressCallback);
        boolean hasPip = pipFreeze != null && !pipFreeze.isBlank();

        Path snapshotDir = storageDirectory.resolve(snapshotId);
        try {
            Files.createDirectories(snapshotDir);

            JSONObject meta = new JSONObject();
            meta.put("id", snapshotId);
            meta.put("timestamp", Instant.now().toString());
            meta.put("description", description != null ? description : "Manual Snapshot");
            meta.put("hasPipFreeze", hasPip);

            JSONArray nodesArr = new JSONArray();
            for (NodeGitState n : nodes) {
                JSONObject no = new JSONObject();
                no.put("folderName", n.folderName());
                no.put("repoName", n.repoName());
                no.put("branch", n.branch());
                no.put("commitHash", n.commitHash());
                no.put("remoteUrl", n.remoteUrl());
                nodesArr.put(no);
            }
            meta.put("nodes", nodesArr);

            Files.writeString(snapshotDir.resolve("snapshot.json"), meta.toString(2), StandardCharsets.UTF_8);

            if (hasPip) {
                Files.writeString(snapshotDir.resolve("pip_freeze.txt"), pipFreeze, StandardCharsets.UTF_8);
            }

            log(progressCallback, "✅ Snapshot '" + snapshotId + "' saved (" + nodes.size() + " nodes recorded).");
            return new EnvironmentSnapshot(snapshotId, Instant.now(), description, nodes, hasPip);
        } catch (Exception ex) {
            logger.error("Failed to persist snapshot: {}", ex.getMessage(), ex);
            log(progressCallback, "❌ Failed to persist snapshot: " + ex.getMessage());
            return new EnvironmentSnapshot(snapshotId, Instant.now(), description, nodes, hasPip);
        }
    }

    @Override
    public List<EnvironmentSnapshot> listSnapshots() {
        List<EnvironmentSnapshot> list = new ArrayList<>();
        if (!Files.exists(storageDirectory)) return list;

        try (var stream = Files.list(storageDirectory)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                Path metaPath = p.resolve("snapshot.json");
                if (Files.exists(metaPath)) {
                    try {
                        String raw = Files.readString(metaPath, StandardCharsets.UTF_8);
                        JSONObject json = new JSONObject(raw);
                        String id = json.optString("id", p.getFileName().toString());
                        Instant timestamp = Instant.parse(json.optString("timestamp", Instant.now().toString()));
                        String desc = json.optString("description", "Snapshot");
                        boolean hasPip = json.optBoolean("hasPipFreeze", Files.exists(p.resolve("pip_freeze.txt")));

                        List<NodeGitState> nodes = new ArrayList<>();
                        JSONArray arr = json.optJSONArray("nodes");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                nodes.add(new NodeGitState(
                                        o.optString("folderName"),
                                        o.optString("repoName"),
                                        o.optString("branch"),
                                        o.optString("commitHash"),
                                        o.optString("remoteUrl")
                                ));
                            }
                        }
                        list.add(new EnvironmentSnapshot(id, timestamp, desc, nodes, hasPip));
                    } catch (Exception ex) {
                        logger.warn("Could not read snapshot at {}: {}", p, ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            logger.warn("Error listing snapshots: {}", e.getMessage());
        }

        list.sort(Comparator.comparing(EnvironmentSnapshot::timestamp).reversed());
        return list;
    }

    @Override
    public RollbackResult rollbackToSnapshot(String snapshotId, boolean restorePip, Consumer<String> progressCallback) {
        log(progressCallback, "🔄 Initiating 1-Click Rollback to '" + snapshotId + "'...");
        Path snapshotDir = storageDirectory.resolve(snapshotId);
        if (!Files.exists(snapshotDir)) {
            log(progressCallback, "❌ Snapshot not found: " + snapshotId);
            return new RollbackResult(false, 0, false, List.of("Snapshot folder does not exist: " + snapshotId));
        }

        List<EnvironmentSnapshot> all = listSnapshots();
        EnvironmentSnapshot target = all.stream().filter(s -> s.id().equals(snapshotId)).findFirst().orElse(null);
        if (target == null) {
            log(progressCallback, "❌ Snapshot metadata not found: " + snapshotId);
            return new RollbackResult(false, 0, false, List.of("Metadata unreadable for: " + snapshotId));
        }

        File customNodesDir = getCustomNodesDirectory();
        if (customNodesDir == null || !customNodesDir.exists()) {
            log(progressCallback, "❌ custom_nodes directory not accessible.");
            return new RollbackResult(false, 0, false, List.of("custom_nodes directory not found."));
        }

        int restoredCount = 0;
        List<String> errors = new ArrayList<>();

        for (NodeGitState n : target.nodes()) {
            File nodeDir = new File(customNodesDir, n.folderName());
            log(progressCallback, "Reverting " + n.folderName() + " to commit " + n.commitHash() + "...");

            if (nodeDir.exists() && new File(nodeDir, ".git").exists()) {
                try (Git git = Git.open(nodeDir)) {
                    git.checkout().setName(n.commitHash()).call();
                    log(progressCallback, "  ✅ " + n.folderName() + " checked out to " + n.commitHash());
                    restoredCount++;
                } catch (Exception e) {
                    String msg = "Failed to checkout " + n.folderName() + ": " + e.getMessage();
                    logger.warn(msg);
                    log(progressCallback, "  ⚠️ " + msg);
                    errors.add(msg);
                }
            } else if (n.remoteUrl() != null && !n.remoteUrl().isBlank()) {
                log(progressCallback, "  Missing folder, cloning " + n.remoteUrl() + "...");
                try {
                    Git.cloneRepository()
                            .setURI(n.remoteUrl())
                            .setDirectory(nodeDir)
                            .call();
                    try (Git git = Git.open(nodeDir)) {
                        git.checkout().setName(n.commitHash()).call();
                    }
                    log(progressCallback, "  ✅ Cloned and checked out " + n.folderName() + " to " + n.commitHash());
                    restoredCount++;
                } catch (Exception e) {
                    String msg = "Failed to clone missing node " + n.folderName() + ": " + e.getMessage();
                    logger.warn(msg);
                    log(progressCallback, "  ❌ " + msg);
                    errors.add(msg);
                }
            }
        }

        boolean pipRestored = false;
        if (restorePip && Files.exists(snapshotDir.resolve("pip_freeze.txt"))) {
            String pythonPath = configService != null ? configService.getPythonPath() : null;
            if (pythonPath != null && !pythonPath.isBlank() && new File(pythonPath).exists()) {
                log(progressCallback, "📦 Restoring Python pip packages from snapshot freeze...");
                try {
                    File freezeFile = snapshotDir.resolve("pip_freeze.txt").toFile();
                    ProcessBuilder pb = new ProcessBuilder(pythonPath, "-m", "pip", "install", "-r", freezeFile.getAbsolutePath(), "--no-warn-script-location");
                    pb.redirectErrorStream(true);
                    Process p = processTracker != null ? processTracker.start(pb) : pb.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            log(progressCallback, "  [pip] " + line);
                        }
                    }
                    int code = p.waitFor();
                    pipRestored = (code == 0);
                    log(progressCallback, pipRestored ? "✅ Python packages restored successfully." : "⚠️ pip install exited with code " + code);
                } catch (Exception ex) {
                    log(progressCallback, "⚠️ Failed to execute pip rollback: " + ex.getMessage());
                }
            }
        }

        log(progressCallback, "🎉 Rollback complete! Restored " + restoredCount + " of " + target.nodes().size() + " custom nodes.");
        return new RollbackResult(errors.isEmpty(), restoredCount, pipRestored, errors);
    }

    @Override
    public boolean deleteSnapshot(String snapshotId) {
        Path dir = storageDirectory.resolve(snapshotId);
        if (!Files.exists(dir)) return false;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
            return true;
        } catch (Exception e) {
            logger.warn("Failed to delete snapshot {}: {}", snapshotId, e.getMessage());
            return false;
        }
    }

    private NodeGitState inspectRepoState(File dir) {
        try (Git git = Git.open(dir)) {
            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            ObjectId head = repo.resolve("HEAD");
            String commit = head != null ? head.name() : "unknown";
            String remoteUrl = repo.getConfig().getString("remote", "origin", "url");
            return new NodeGitState(dir.getName(), dir.getName(), branch != null ? branch : "main", commit, remoteUrl != null ? remoteUrl : "");
        } catch (Exception e) {
            logger.debug("Could not inspect git repo {}: {}", dir.getName(), e.getMessage());
            return null;
        }
    }

    private String capturePipFreeze(Consumer<String> progressCallback) {
        String pythonPath = configService != null ? configService.getPythonPath() : null;
        if (pythonPath == null || pythonPath.isBlank() || !new File(pythonPath).exists()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "-m", "pip", "freeze");
            pb.redirectErrorStream(true);
            Process p = processTracker != null ? processTracker.start(pb) : pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            logger.debug("Failed to capture pip freeze: {}", e.getMessage());
            return null;
        }
    }

    private File getCustomNodesDirectory() {
        if (configService == null) return null;
        String cp = configService.getComfyUIPath();
        if (cp == null || cp.isBlank()) return null;
        return new File(cp, "custom_nodes");
    }

    private void log(Consumer<String> callback, String msg) {
        if (callback != null) {
            callback.accept(msg + "\n");
        }
    }
}
