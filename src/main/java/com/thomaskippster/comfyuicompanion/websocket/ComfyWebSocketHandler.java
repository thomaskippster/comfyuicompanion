package com.thomaskippster.comfyuicompanion.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import org.springframework.context.ApplicationEventPublisher;
import com.thomaskippster.comfyuicompanion.event.ProgressUpdateEvent;
import com.thomaskippster.comfyuicompanion.event.WorkflowCompletedEvent;

@Component
public class ComfyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ComfyWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ComfyWebSocketHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        try {
            ComfyEvent event = objectMapper.readValue(payload, ComfyEvent.class);
            if (event.getType() == null) {
                return;
            }

            switch (event.getType()) {
                case "progress":
                    if (event.getData() != null && event.getData().has("value") && event.getData().has("max")) {
                        int value = event.getData().get("value").asInt();
                        int max = event.getData().get("max").asInt();
                        logger.info("Progress: {}/{}", value, max);
                        eventPublisher.publishEvent(new ProgressUpdateEvent(value, max));
                    }
                    break;
                case "executing":
                    if (event.getData() != null && event.getData().has("node")) {
                        if (event.getData().get("node").isNull()) {
                            logger.info("Execution complete: The 'node' value is null, deterministically indicating workflow finish.");
                            eventPublisher.publishEvent(new WorkflowCompletedEvent());
                        } else {
                            String node = event.getData().get("node").asText();
                            logger.info("Executing node: {}", node);
                        }
                    }
                    break;
                case "execution_start":
                    logger.info("Execution started. Workflow is rendering.");
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
