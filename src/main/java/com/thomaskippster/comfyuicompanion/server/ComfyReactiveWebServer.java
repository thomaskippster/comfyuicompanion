package com.thomaskippster.comfyuicompanion.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Embedded reactive HTTP web server hosting reactive REST controllers (ComfyCompanionController)
 * and global exception handlers via Reactor Netty and Spring WebFlux.
 */
@Component
public class ComfyReactiveWebServer {

    private static final Logger logger = LoggerFactory.getLogger(ComfyReactiveWebServer.class);

    private final ApplicationContext applicationContext;
    private final int port;
    private final boolean enabled;
    private DisposableServer disposableServer;

    public ComfyReactiveWebServer(
            ApplicationContext applicationContext,
            @Value("${companion.server.port:12345}") int port,
            @Value("${companion.server.enabled:true}") boolean enabled) {
        this.applicationContext = applicationContext;
        this.port = port;
        this.enabled = enabled;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            logger.info("Reactive REST server is disabled by configuration.");
            return;
        }

        if (isTestEnvironment()) {
            logger.info("Test environment detected. Skipping reactive web server port binding.");
            return;
        }

        try {
            HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(applicationContext).build();
            ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

            this.disposableServer = HttpServer.create()
                    .host("127.0.0.1")
                    .port(port)
                    .handle(adapter)
                    .bindNow();

            logger.info("🚀 ComfyUI Companion Reactive REST Server started on http://127.0.0.1:{}", port);
        } catch (Exception e) {
            logger.error("Failed to start Reactive REST Server on port {}: {}", port, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (disposableServer != null) {
            logger.info("Stopping ComfyUI Companion Reactive REST Server...");
            disposableServer.disposeNow();
            disposableServer = null;
        }
    }

    public boolean isRunning() {
        return disposableServer != null && !disposableServer.isDisposed();
    }

    public int getPort() {
        return port;
    }

    private boolean isTestEnvironment() {
        String override = System.getProperty("comfyuicompanion.appdata");
        if (override != null && !override.isEmpty()) {
            return true;
        }
        String command = System.getProperty("sun.java.command", "");
        return command.contains("surefire") || command.contains("junit") || command.contains("testng");
    }
}
