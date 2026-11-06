package de.tki.comfyuicompanion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

/**
 * Main Spring configuration class for Companion for ComfyUI.
 */
@Configuration
@EnableWebFlux
@ComponentScan(basePackages = {"de.tki.comfyuicompanion"})
public class AppConfig {

    static {
        configureSystemProxyFromEnv();
    }

    /**
     * Automatically maps standard {@code HTTPS_PROXY} / {@code HTTP_PROXY} environment variables
     * to JVM system properties ({@code https.proxyHost}, {@code https.proxyPort}, etc.) if not already set,
     * ensuring {@link java.net.http.HttpClient} honours environment proxy configurations.
     */
    public static void configureSystemProxyFromEnv() {
        try {
            if (System.getProperty("https.proxyHost") == null) {
                String httpsProxy = System.getenv("HTTPS_PROXY");
                if (httpsProxy == null || httpsProxy.isBlank()) {
                    httpsProxy = System.getenv("https_proxy");
                }
                if (httpsProxy != null && !httpsProxy.isBlank()) {
                    URI uri = URI.create(httpsProxy.trim());
                    if (uri.getHost() != null && uri.getPort() > 0) {
                        System.setProperty("https.proxyHost", uri.getHost());
                        System.setProperty("https.proxyPort", String.valueOf(uri.getPort()));
                    }
                }
            }
            if (System.getProperty("http.proxyHost") == null) {
                String httpProxy = System.getenv("HTTP_PROXY");
                if (httpProxy == null || httpProxy.isBlank()) {
                    httpProxy = System.getenv("http_proxy");
                }
                if (httpProxy != null && !httpProxy.isBlank()) {
                    URI uri = URI.create(httpProxy.trim());
                    if (uri.getHost() != null && uri.getPort() > 0) {
                        System.setProperty("http.proxyHost", uri.getHost());
                        System.setProperty("http.proxyPort", String.valueOf(uri.getPort()));
                    }
                }
            }
            if (System.getProperty("http.nonProxyHosts") == null) {
                String noProxy = System.getenv("NO_PROXY");
                if (noProxy == null || noProxy.isBlank()) {
                    noProxy = System.getenv("no_proxy");
                }
                if (noProxy != null && !noProxy.isBlank()) {
                    String converted = noProxy.trim().replace(",", "|");
                    System.setProperty("http.nonProxyHosts", converted);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed proxy env variables
        }
    }

    /**
     * Provides a WebClient.Builder bean for reactive web requests.
     * 
     * @return a new WebClient.Builder instance
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
