package de.tki.comfyuicompanion.service;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service for capturing and restoring ComfyUI custom node environment snapshots
 * (Git commit hashes and pip freeze dependencies) to protect against dependency conflicts.
 */
public interface ICustomNodeSnapshotService {

    /**
     * Git state of an individual custom node repository.
     *
     * @param folderName directory name inside custom_nodes
     * @param repoName display or repository name
     * @param branch active git branch
     * @param commitHash exact HEAD commit hash
     * @param remoteUrl remote repository origin URL
     */
    record NodeGitState(
            String folderName,
            String repoName,
            String branch,
            String commitHash,
            String remoteUrl
    ) {}

    /**
     * Immutable environment snapshot containing node versions and pip freeze.
     *
     * @param id unique snapshot identifier
     * @param timestamp timestamp when snapshot was captured
     * @param description user or automated explanation (e.g. pre-update)
     * @param nodes recorded git states for all active custom nodes
     * @param hasPipFreeze whether pip freeze output was recorded
     */
    record EnvironmentSnapshot(
            String id,
            Instant timestamp,
            String description,
            List<NodeGitState> nodes,
            boolean hasPipFreeze
    ) {}

    /**
     * Outcome of a snapshot rollback operation.
     *
     * @param success whether all nodes were successfully reverted
     * @param nodesRestored count of repositories checked out to the target commit
     * @param pipRestored whether pip dependencies were re-installed
     * @param messages log messages and warnings generated during rollback
     */
    record RollbackResult(
            boolean success,
            int nodesRestored,
            boolean pipRestored,
            List<String> messages
    ) {}

    /**
     * Captures a snapshot of all currently installed custom nodes and Python packages.
     *
     * @param description description or trigger reason for the snapshot
     * @param progressCallback optional progress sink
     * @return the captured snapshot record
     */
    EnvironmentSnapshot captureSnapshot(String description, Consumer<String> progressCallback);

    /**
     * Lists all available historical environment snapshots, sorted chronologically descending.
     *
     * @return list of snapshots
     */
    List<EnvironmentSnapshot> listSnapshots();

    /**
     * Performs a 1-click rollback of all custom node repositories and pip packages to the specified snapshot.
     *
     * @param snapshotId identifier of the snapshot to restore
     * @param restorePip whether to also run pip install to revert Python packages
     * @param progressCallback progress sink for live updates
     * @return result of the rollback
     */
    RollbackResult rollbackToSnapshot(String snapshotId, boolean restorePip, Consumer<String> progressCallback);

    /**
     * Deletes a stored snapshot from disk.
     *
     * @param snapshotId identifier of the snapshot to delete
     * @return true if deleted successfully
     */
    boolean deleteSnapshot(String snapshotId);
}
