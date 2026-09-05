package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.service.impl.WorkflowDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorkflowDownloaderTest {

    private IComfyRegistryClient mockRegistryClient;
    private WorkflowDownloader workflowDownloader;

    @BeforeEach
    void setUp() {
        mockRegistryClient = mock(IComfyRegistryClient.class);
        workflowDownloader = new WorkflowDownloader(mockRegistryClient);
    }

    @AfterEach
    void cleanUp() {
        File testUserFile = new File("user_workflows", "test_resilient_workflow.json");
        if (testUserFile.exists()) {
            testUserFile.delete();
        }
        File testUserThumb = new File("user_workflows", "test_resilient_workflow.png");
        if (testUserThumb.exists()) {
            testUserThumb.delete();
        }
        File testShippedFile = new File("workflows", "test_resilient_workflow.json");
        if (testShippedFile.exists()) {
            testShippedFile.delete();
        }
        File testShippedThumb = new File("workflows", "test_resilient_workflow.png");
        if (testShippedThumb.exists()) {
            testShippedThumb.delete();
        }
    }

    @Test
    void testDownloadWorkflowAsync_succeedsEvenIfThumbnailTimesOut() throws Exception {
        ComfyRegistryWorkflow workflow = new ComfyRegistryWorkflow();
        workflow.setId("test-resilient-wf");
        workflow.setTitle("Test Resilient Workflow");
        workflow.setJsonDownloadUrl("https://raw.githubusercontent.com/example/workflow.json");
        workflow.setThumbnailUrl("https://raw.githubusercontent.com/example/thumbnail.png");

        String expectedJson = "{\"nodes\": []}";

        // Mock JSON download success
        when(mockRegistryClient.downloadFileAsync(workflow.getJsonDownloadUrl()))
                .thenReturn(CompletableFuture.completedFuture(expectedJson.getBytes(StandardCharsets.UTF_8)));

        // Mock Thumbnail download timeout exception
        CompletableFuture<byte[]> failedThumb = new CompletableFuture<>();
        failedThumb.completeExceptionally(new HttpTimeoutException("request timed out"));
        when(mockRegistryClient.downloadFileAsync(workflow.getThumbnailUrl()))
                .thenReturn(failedThumb);

        // Execute download
        CompletableFuture<File> future = workflowDownloader.downloadWorkflowAsync(workflow);
        File result = future.get();

        assertNotNull(result);
        assertTrue(result.exists(), "Workflow JSON file should have been written to disk");
        assertEquals(new File("user_workflows", "test_resilient_workflow.json").getAbsolutePath(), result.getAbsolutePath());
        String content = Files.readString(result.toPath(), StandardCharsets.UTF_8);
        assertEquals(expectedJson, content);

        verify(mockRegistryClient, times(1)).downloadFileAsync(workflow.getJsonDownloadUrl());
        verify(mockRegistryClient, times(1)).downloadFileAsync(workflow.getThumbnailUrl());
    }

    @Test
    void testDownloadWorkflowAsync_missingJsonUrl_failsFast() {
        ComfyRegistryWorkflow workflow = new ComfyRegistryWorkflow();
        workflow.setId("missing-url-wf");
        workflow.setTitle("Missing URL Workflow");
        workflow.setJsonDownloadUrl(null);

        CompletableFuture<File> future = workflowDownloader.downloadWorkflowAsync(workflow);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void testDownloadWorkflowAsync_usesUserWorkflowIfAlreadyCached() throws Exception {
        ComfyRegistryWorkflow workflow = new ComfyRegistryWorkflow();
        workflow.setId("cached-wf");
        workflow.setTitle("Test Resilient Workflow");
        workflow.setJsonDownloadUrl("https://raw.githubusercontent.com/example/cached_workflow.json");

        File userDir = new File("user_workflows");
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        File existingFile = new File(userDir, "test_resilient_workflow.json");
        Files.writeString(existingFile.toPath(), "{\"cached\": true}", StandardCharsets.UTF_8);

        CompletableFuture<File> future = workflowDownloader.downloadWorkflowAsync(workflow);
        File result = future.get();

        assertNotNull(result);
        assertEquals(existingFile.getAbsolutePath(), result.getAbsolutePath());
        // Verify no network request was made for JSON
        verify(mockRegistryClient, never()).downloadFileAsync(workflow.getJsonDownloadUrl());
    }

    @Test
    void testDownloadWorkflowAsync_usesShippedWorkflowFallbackIfPresent() throws Exception {
        ComfyRegistryWorkflow workflow = new ComfyRegistryWorkflow();
        workflow.setId("shipped-wf");
        workflow.setTitle("Test Resilient Workflow");
        workflow.setJsonDownloadUrl("https://raw.githubusercontent.com/example/shipped_workflow.json");

        File shippedDir = new File("workflows");
        if (!shippedDir.exists()) {
            shippedDir.mkdirs();
        }
        File shippedFile = new File(shippedDir, "test_resilient_workflow.json");
        Files.writeString(shippedFile.toPath(), "{\"shipped\": true}", StandardCharsets.UTF_8);

        CompletableFuture<File> future = workflowDownloader.downloadWorkflowAsync(workflow);
        File result = future.get();

        assertNotNull(result);
        assertEquals(shippedFile.getAbsolutePath(), result.getAbsolutePath());
        verify(mockRegistryClient, never()).downloadFileAsync(workflow.getJsonDownloadUrl());
    }
}
