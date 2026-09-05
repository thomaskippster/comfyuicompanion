package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import de.tki.comfymodels.service.impl.PathResolver;
import de.tki.comfymodels.service.impl.WorkflowDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorkflowIsolationTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private IComfyRegistryClient mockRegistryClient;
    private WorkflowDownloader workflowDownloader;

    @BeforeEach
    void setUp() {
        System.setProperty("comfyuicompanion.appdata", tempDir.toAbsolutePath().toString());
        configService = new ConfigService(new EncryptionUtils(), new PathResolver());
        mockRegistryClient = mock(IComfyRegistryClient.class);
        workflowDownloader = new WorkflowDownloader(mockRegistryClient, configService);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("comfyuicompanion.appdata");
    }

    @Test
    void testUserWorkflowsDirectoryIsIsolatedInAppData() {
        File userWorkflows = configService.getUserWorkflowsDir();
        assertNotNull(userWorkflows);
        assertTrue(userWorkflows.exists());
        assertTrue(userWorkflows.isDirectory());
        assertEquals(new File(tempDir.toFile(), "user_workflows").getAbsolutePath(), userWorkflows.getAbsolutePath());

        File shippedWorkflows = configService.getShippedWorkflowsDir();
        assertNotNull(shippedWorkflows);
        assertNotEquals(userWorkflows.getAbsolutePath(), shippedWorkflows.getAbsolutePath());
    }

    @Test
    void testWorkflowDownloaderSavesToUserWorkflowsDir() throws Exception {
        ComfyRegistryWorkflow workflow = new ComfyRegistryWorkflow();
        workflow.setId("iso-test-wf");
        workflow.setTitle("Isolated Test Workflow");
        workflow.setJsonDownloadUrl("https://example.com/iso.json");

        String payload = "{\"isolated\": true}";
        when(mockRegistryClient.downloadFileAsync(workflow.getJsonDownloadUrl()))
                .thenReturn(CompletableFuture.completedFuture(payload.getBytes(StandardCharsets.UTF_8)));

        File downloaded = workflowDownloader.downloadWorkflowAsync(workflow).get();
        assertNotNull(downloaded);
        assertTrue(downloaded.exists());
        assertEquals(new File(configService.getUserWorkflowsDir(), "isolated_test_workflow.json").getAbsolutePath(),
                downloaded.getAbsolutePath());

        // Verify shipped workflows directory does not contain this file
        File inShipped = new File(configService.getShippedWorkflowsDir(), "isolated_test_workflow.json");
        assertFalse(inShipped.exists(), "Shipped workflows directory must remain clean and untouched");
    }
}
