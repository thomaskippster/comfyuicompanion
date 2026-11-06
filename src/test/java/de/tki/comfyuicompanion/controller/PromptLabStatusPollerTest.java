package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ComfyWebSocketClient;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import java.net.ConnectException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptLabStatusPoller} validating connection failure resilience
 * and early abortion when ComfyUI is offline.
 */
@ExtendWith(MockitoExtension.class)
class PromptLabStatusPollerTest {

    @Mock
    private ComfyApiClient comfyApiClient;

    @Mock
    private ComfyWebSocketClient webSocketClient;

    @Test
    @DisplayName("Should abort polling after consecutive connection failures without spamming 180 attempts")
    void shouldAbortPollingOnConnectionRefused() throws Exception {
        AutoCloseable mockSession = mock(AutoCloseable.class);
        when(webSocketClient.connect(anyString(), anyString(), any(), any(), any())).thenReturn(mockSession);

        AtomicInteger callCount = new AtomicInteger(0);
        when(comfyApiClient.fetchPromptHistoryImage(anyString(), anyString())).thenAnswer(inv -> {
            callCount.incrementAndGet();
            throw new ConnectException("Connection refused: getsockopt");
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        PromptLabStatusPoller poller = new PromptLabStatusPoller(comfyApiClient, webSocketClient, executor, null);

        PromptLabView view = mock(PromptLabView.class);
        JProgressBar bar = new JProgressBar();
        JLabel label = new JLabel();
        JTextArea console = new JTextArea();
        when(view.getPromptLabProgressBar()).thenReturn(bar);
        when(view.getPromptImagePreviewLabel()).thenReturn(label);
        when(view.getPromptLabConsole()).thenReturn(console);

        poller.pollPromptStatus("http://127.0.0.1:8188", "test-prompt-id", view, img -> {});

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(callCount.get()).isGreaterThanOrEqualTo(3).isLessThan(10);
        verify(mockSession).close();
    }
}
