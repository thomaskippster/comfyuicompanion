package de.tki.comfyuicompanion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Main Spring configuration class for Companion for ComfyUI.
 */
@Configuration
@EnableWebFlux
@ComponentScan(basePackages = {"de.tki.comfyuicompanion"})
public class AppConfig {

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
