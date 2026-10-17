package de.tki.comfyuicompanion;

import de.tki.comfyuicompanion.service.IComfyConnectionMonitorService.ConnectionState;
import de.tki.comfyuicompanion.service.impl.ComfyConnectionMonitorService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying ComfyUI connection monitor states, heartbeat error handling,
 * and device summary extraction.
 */
public class ComfyConnectionMonitorServiceTest {

    @Test
    public void testSuccessfulHeartbeatUpdatesStateToConnected() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://127.0.0.1:8188");

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn("{\"devices\":[{\"name\":\"NVIDIA GeForce RTX 4080\"}],\"system\":{\"os\":\"nt\"}}");

        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ComfyConnectionMonitorService service = new ComfyConnectionMonitorService(mockConfig, mockClient);
        assertEquals(ConnectionState.DISCONNECTED, service.getState());

        AtomicReference<ConnectionState> notifiedState = new AtomicReference<>();
        service.addStateListener(notifiedState::set);

        service.performCheck();

        assertEquals(ConnectionState.CONNECTED, service.getState());
        assertEquals("NVIDIA GeForce RTX 4080", service.getSystemSummary());
    }

    @Test
    public void testFailedHeartbeatTransitionsToConnectingThenDisconnected() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://127.0.0.1:8188");

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn("{\"system\":{\"os\":\"linux\"}}");

        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ComfyConnectionMonitorService service = new ComfyConnectionMonitorService(mockConfig, mockClient);
        service.performCheck();
        assertEquals(ConnectionState.CONNECTED, service.getState());

        // Now emulate transient connection failure
        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        // First failure: transient, should enter CONNECTING
        service.performCheck();
        assertEquals(ConnectionState.CONNECTING, service.getState());

        // Second failure: still CONNECTING
        service.performCheck();
        assertEquals(ConnectionState.CONNECTING, service.getState());

        // Third failure: marks DISCONNECTED
        service.performCheck();
        assertEquals(ConnectionState.DISCONNECTED, service.getState());
    }

    @Test
    public void testEmptyUrlMarksDisconnected() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("");

        ComfyConnectionMonitorService service = new ComfyConnectionMonitorService(mockConfig, Mockito.mock(HttpClient.class));
        service.performCheck();
        assertEquals(ConnectionState.DISCONNECTED, service.getState());
    }
}
