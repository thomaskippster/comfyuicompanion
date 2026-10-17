package de.tki.comfyuicompanion;

import de.tki.comfyuicompanion.domain.PromptHistoryEntry;
import de.tki.comfyuicompanion.service.impl.PromptHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying prompt history recording, recall, deduplication,
 * and the maximum rolling capacity.
 */
public class PromptHistoryServiceTest {

    private File tempHistoryFile;
    private PromptHistoryService historyService;

    @BeforeEach
    public void setUp() throws IOException {
        tempHistoryFile = File.createTempFile("test_prompt_history_", ".json");
        historyService = new PromptHistoryService(tempHistoryFile);
    }

    @AfterEach
    public void tearDown() {
        if (tempHistoryFile != null && tempHistoryFile.exists()) {
            tempHistoryFile.delete();
        }
    }

    @Test
    public void testRecordAndRecallPrompt() {
        PromptHistoryEntry entry = new PromptHistoryEntry(
                System.currentTimeMillis(),
                "A futuristic neon city at dusk",
                "blurry, ugly",
                "flux1-dev-fp8.safetensors",
                1024,
                1024,
                25,
                3.5,
                "euler",
                "simple"
        );

        historyService.recordPrompt(entry);

        List<PromptHistoryEntry> list = historyService.getHistory();
        assertEquals(1, list.size());
        assertEquals("A futuristic neon city at dusk", list.get(0).positivePrompt());
        assertEquals(1024, list.get(0).width());
        assertEquals(3.5, list.get(0).cfg(), 0.001);
    }

    @Test
    public void testAvoidConsecutiveDuplicatePrompt() {
        PromptHistoryEntry entry1 = new PromptHistoryEntry(
                1000L, "Cyberpunk motorcycle", "bad", "flux", 512, 512, 20, 7.0, "euler", "normal"
        );
        PromptHistoryEntry entry2 = new PromptHistoryEntry(
                2000L, "Cyberpunk motorcycle", "bad", "flux", 512, 512, 20, 7.0, "euler", "normal"
        );

        historyService.recordPrompt(entry1);
        historyService.recordPrompt(entry2);

        assertEquals(1, historyService.getHistory().size(), "Consecutive identical prompt should not create duplicate entries");
    }

    @Test
    public void testHistoryCapAt25Entries() {
        for (int i = 0; i < 30; i++) {
            historyService.recordPrompt(new PromptHistoryEntry(
                    System.currentTimeMillis() + i,
                    "Prompt variation " + i,
                    "",
                    "model.safetensors",
                    512,
                    512,
                    20,
                    7.0,
                    "euler",
                    "normal"
            ));
        }

        List<PromptHistoryEntry> list = historyService.getHistory();
        assertEquals(25, list.size(), "History should be capped at 25 entries");
        assertEquals("Prompt variation 29", list.get(0).positivePrompt(), "Most recent prompt should be at index 0");
    }

    @Test
    public void testClearHistory() {
        historyService.recordPrompt(new PromptHistoryEntry(
                1000L, "Test prompt", "", "model", 512, 512, 20, 7.0, "euler", "normal"
        ));
        assertFalse(historyService.getHistory().isEmpty());

        historyService.clearHistory();
        assertTrue(historyService.getHistory().isEmpty());
    }

    @Test
    public void testPromptHistoryEntryDisplayTitle() {
        PromptHistoryEntry shortPrompt = new PromptHistoryEntry(
                1000L, "A short prompt", "", "model", 512, 512, 20, 7.0, "euler", "normal"
        );
        assertEquals("A short prompt", shortPrompt.getDisplayTitle());

        PromptHistoryEntry longPrompt = new PromptHistoryEntry(
                1000L, "A very long prompt that exceeds sixty characters in total length to verify truncation formatting",
                "", "model", 512, 512, 20, 7.0, "euler", "normal"
        );
        assertTrue(longPrompt.getDisplayTitle().endsWith("..."));
        assertTrue(longPrompt.getDisplayTitle().length() <= 60);

        PromptHistoryEntry emptyPrompt = new PromptHistoryEntry(
                1000L, "", "", "model", 512, 512, 20, 7.0, "euler", "normal"
        );
        assertEquals("[Empty Prompt]", emptyPrompt.getDisplayTitle());
    }
}
