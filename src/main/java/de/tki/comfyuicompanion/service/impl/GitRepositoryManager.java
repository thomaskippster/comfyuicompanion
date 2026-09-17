package de.tki.comfyuicompanion.service.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Service managing Git operations including repository cloning, updating (pulling),
 * process execution, and JGit fallbacks for custom nodes and ComfyUI installations.
 */
@Service
public class GitRepositoryManager {

    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryManager.class);

    private final ProcessTracker processTracker;

    /**
     * Default constructor without process tracker.
     */
    public GitRepositoryManager() {
        this(null);
    }

    /**
     * Constructor allowing injection of a custom {@link ProcessTracker}.
     *
     * @param processTracker process tracker for child process management
     */
    @Autowired
    public GitRepositoryManager(@Autowired(required = false) ProcessTracker processTracker) {
        this.processTracker = processTracker;
    }

    /**
     * Synchronizes a repository by pulling if it already exists as a valid Git directory,
     * or cloning it cleanly if it is absent or corrupted.
     *
     * @param repoUrl          the remote Git repository URL
     * @param targetDir        the destination path on disk
     * @param branch           the target branch name (e.g., "main" or "master")
     * @param progressCallback consumer receiving progress updates
     * @return true if the repository is successfully updated or cloned, false otherwise
     */
    public boolean cloneOrUpdateRepository(String repoUrl, Path targetDir, String branch, Consumer<String> progressCallback) {
        Consumer<String> cb = progressCallback != null ? progressCallback : s -> {};

        if (Files.exists(targetDir)) {
            Path gitDir = targetDir.resolve(".git");
            if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                boolean pullSuccess = pullRepository(targetDir, cb);
                if (pullSuccess) {
                    cb.accept("✅ Repository successfully updated: " + targetDir.getFileName());
                    return true;
                }
                cb.accept("⚠️ Git pull failed. Cleaning directory and recloning: " + targetDir.getFileName());
            } else {
                cb.accept("⚠️ Directory exists but is not a git repo. Cleaning and recloning: " + targetDir.getFileName());
            }

            try {
                deleteDirectoryRecursively(targetDir);
            } catch (IOException e) {
                cb.accept("❌ Failed to clean existing directory " + targetDir + ": " + e.getMessage());
                return false;
            }
        }

        try {
            cloneRepository(repoUrl, targetDir, branch, cb);
            cb.accept("✅ Repository successfully cloned: " + targetDir.getFileName());
            return true;
        } catch (Exception e) {
            cb.accept("❌ Failed to clone repository " + repoUrl + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to update an existing repository via system git pull, falling back to JGit.
     *
     * @param targetDir        the local repository directory
     * @param progressCallback consumer receiving progress updates
     * @return true if pull succeeded, false otherwise
     */
    public boolean pullRepository(Path targetDir, Consumer<String> progressCallback) {
        Consumer<String> cb = progressCallback != null ? progressCallback : s -> {};
        try {
            runSystemGitPull(targetDir, cb);
            return true;
        } catch (Exception systemEx) {
            cb.accept("⚠️ System git pull failed (" + systemEx.getMessage() + "). Trying JGit fallback...");
            try (Git git = Git.open(targetDir.toFile())) {
                git.pull()
                        .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                            @Override
                            public void println(String x) { cb.accept(x); }
                        }))
                        .call();
                return true;
            } catch (Exception jgitEx) {
                cb.accept("⚠️ JGit pull failed: " + jgitEx.getMessage());
                return false;
            }
        }
    }

    /**
     * Clones a repository using system git, falling back to JGit if system git fails.
     *
     * @param repoUrl          the remote repository URL
     * @param targetDir        the local target directory
     * @param branch           the branch to check out
     * @param progressCallback consumer receiving progress updates
     * @throws Exception if both system git and JGit fail
     */
    public void cloneRepository(String repoUrl, Path targetDir, String branch, Consumer<String> progressCallback) throws Exception {
        Consumer<String> cb = progressCallback != null ? progressCallback : s -> {};
        cb.accept("Starting clone of " + repoUrl + " (branch: " + branch + ")...");

        try {
            runSystemGitClone(repoUrl, targetDir, branch, cb);
        } catch (Exception systemEx) {
            cb.accept("⚠️ System git clone failed or git is not in PATH: " + systemEx.getMessage() + ". Falling back to JGit...");
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(targetDir.toFile())
                    .setBranch(branch)
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                        @Override
                        public void println(String x) { cb.accept(x); }
                    }))
                    .call()) {
                cb.accept("✅ Successfully cloned via JGit: " + repoUrl);
            }
        }
    }

    /**
     * Executes a system 'git clone' process.
     *
     * @param repoUrl          the repository URL
     * @param targetDir        the target directory path
     * @param branch           the branch to check out
     * @param progressCallback consumer receiving process stdout/stderr
     * @throws Exception if process execution fails or returns a non-zero exit code
     */
    public void runSystemGitClone(String repoUrl, Path targetDir, String branch, Consumer<String> progressCallback) throws Exception {
        Consumer<String> cb = progressCallback != null ? progressCallback : s -> {};
        cb.accept("Executing system git clone for " + repoUrl + "...");
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "-b", branch, repoUrl, targetDir.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = startProcess(pb);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cb.accept("[git] " + line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("System git clone failed with exit code " + exitCode);
        }
    }

    /**
     * Executes a system 'git pull' process in the target directory.
     *
     * @param targetDir        the repository directory
     * @param progressCallback consumer receiving process stdout/stderr
     * @throws Exception if process execution fails or returns a non-zero exit code
     */
    public void runSystemGitPull(Path targetDir, Consumer<String> progressCallback) throws Exception {
        Consumer<String> cb = progressCallback != null ? progressCallback : s -> {};
        cb.accept("Executing system git pull in " + targetDir + "...");
        ProcessBuilder pb = new ProcessBuilder("git", "pull");
        pb.directory(targetDir.toFile());
        pb.redirectErrorStream(true);
        Process p = startProcess(pb);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cb.accept("[git] " + line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("System git pull failed with exit code " + exitCode);
        }
    }

    /**
     * Recursively deletes a directory and its contents, resetting write permissions if necessary.
     *
     * @param path directory path to delete
     * @throws IOException if directory walk or file deletion fails
     */
    public void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        File file = p.toFile();
                        if (!file.canWrite()) {
                            file.setWritable(true);
                        }
                        Files.delete(p);
                    } catch (IOException e) {
                        logger.warn("Could not delete file during cleanup: {}", p, e);
                    }
                });
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return processTracker != null ? processTracker.start(pb) : pb.start();
    }
}
