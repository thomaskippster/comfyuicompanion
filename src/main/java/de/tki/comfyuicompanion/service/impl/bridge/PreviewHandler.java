package de.tki.comfyuicompanion.service.impl.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Handles {@code GET /api/preview?url=...}: streams a template preview from a loopback ComfyUI
 * instance to the browser, falling back from {@code name-1.ext} to {@code name.ext} when needed.
 */
public class PreviewHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(PreviewHandler.class);

    private final BridgeHttpSupport http;
    private final ProxyUrlValidator urlValidator;
    private final MediaContentTypeResolver contentTypeResolver;

    /**
     * Creates the handler.
     *
     * @param http                shared HTTP helper
     * @param urlValidator        SSRF guard for the proxied URL
     * @param contentTypeResolver content-type fallback resolver
     */
    public PreviewHandler(BridgeHttpSupport http, ProxyUrlValidator urlValidator,
                          MediaContentTypeResolver contentTypeResolver) {
        this.http = http;
        this.urlValidator = urlValidator;
        this.contentTypeResolver = contentTypeResolver;
    }

    private String getFallbackUrl(String url) {
        int lastDot = url.lastIndexOf('.');
        if (lastDot != -1) {
            String base = url.substring(0, lastDot);
            String ext = url.substring(lastDot);
            if (base.endsWith("-1")) {
                return base.substring(0, base.length() - 2) + ext;
            }
        }
        return null;
    }

    private HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "ComfyFS-Java/0.1")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!http.handleCorsAndOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String remoteUrl = http.getQueryParam(exchange, "url");
            if (remoteUrl == null || remoteUrl.isEmpty() || !urlValidator.isValidProxyUrl(remoteUrl)) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<InputStream> response = client.send(
                buildRequest(remoteUrl), HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            if (statusCode != 200) {
                // Try fallback if url contains "-1." before extension
                String fallbackUrl = getFallbackUrl(remoteUrl);
                if (fallbackUrl != null && urlValidator.isValidProxyUrl(fallbackUrl)) {
                    HttpResponse<InputStream> fallbackResponse = client.send(
                        buildRequest(fallbackUrl), HttpResponse.BodyHandlers.ofInputStream());
                    if (fallbackResponse.statusCode() == 200) {
                        response = fallbackResponse;
                        statusCode = 200;
                        remoteUrl = fallbackUrl;
                    }
                }
            }

            if (statusCode != 200) {
                exchange.sendResponseHeaders(statusCode, -1);
                return;
            }

            String contentType = contentTypeResolver.resolve(
                response.headers().firstValue("Content-Type").orElse(null), remoteUrl);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");

            long contentLength = -1;
            Optional<String> lenOpt = response.headers().firstValue("Content-Length");
            if (lenOpt.isPresent()) {
                try {
                    contentLength = Long.parseLong(lenOpt.get());
                } catch (NumberFormatException ignored) {}
            }

            exchange.sendResponseHeaders(200, contentLength > 0 ? contentLength : 0);

            try (InputStream in = response.body();
                 OutputStream out = exchange.getResponseBody()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            logger.error("REST Bridge: Error fetching preview: {}", e.getMessage(), e);
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }
}
