package de.tki.comfyuicompanion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import org.springframework.context.ApplicationEventPublisher;
import de.tki.comfyuicompanion.event.ProgressUpdateEvent;
import de.tki.comfyuicompanion.event.WorkflowCompletedEvent;

/**
 * Handler for WebSocket messages received from the ComfyUI server.
 * Parses incoming events such as progress updates or execution state changes,
 * and publishes corresponding Spring application events.
 */
@Component
public class ComfyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ComfyWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs a new {@code ComfyWebSocketHandler}.
     *
     * @param objectMapper the object mapper used to deserialize JSON payloads
     * @param eventPublisher the event publisher to dispatch internal application events
     */
    public ComfyWebSocketHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Handles an incoming text message from the WebSocket.
     * Parses the JSON payload into a {@link ComfyEvent} and publishes relevant progress or completion events.
     *
     * @param session the current WebSocket session
     * @param message the received text message
     * @throws Exception if an error occurs during processing
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            ComfyEvent event = objectMapper.readValue(payload, ComfyEvent.class);
            if (event.getType() == null) {
                return;
            }

            String promptId = null;
            if (event.getData() != null && event.getData().has("prompt_id")) {
                promptId = event.getData().get("prompt_id").asText(null);
            }

            switch (event.getType()) {
                case "progress":
                    if (event.getData() != null && event.getData().has("value") && event.getData().has("max")) {
                        int value = event.getData().get("value").asInt();
                        int max = event.getData().get("max").asInt();
                        logger.info("Progress [promptId={}]: {}/{}", promptId, value, max);
                        eventPublisher.publishEvent(new ProgressUpdateEvent(value, max, promptId, null));
                    }
                    break;
                case "executing":
                    if (event.getData() != null && event.getData().has("node")) {
                        if (event.getData().get("node").isNull()) {
                            logger.info("Execution complete [promptId={}]: The 'node' value is null, deterministically indicating workflow finish.", promptId);
                            eventPublisher.publishEvent(new WorkflowCompletedEvent(promptId, null));
                        } else {
                            String node = event.getData().get("node").asText();
                            logger.info("Executing node [promptId={}]: {}", promptId, node);
                        }
                    }
                    break;
                case "execution_start":
                    logger.info("Execution started [promptId={}]. Workflow is rendering.", promptId);
                    break;
                default:
                    logger.debug("Received unhandled event type: {}", event.getType());
                    break;
            }
        } catch (Exception e) {
            logger.error("Error parsing WebSocket message: {}", payload, e);
        }
    }
}
