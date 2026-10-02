package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * Storage and supplier of default fallback ComfyUI API workflow templates.
 */
public final class DefaultComfyTemplates {
    private static final Logger log = LoggerFactory.getLogger(DefaultComfyTemplates.class);
    private static final String CLASSPATH_PREFIX = "/templates/comfyui/";
    private static final Path FS_DIR = Path.of("templates", "comfyui");
    private DefaultComfyTemplates() {}

    /** Resolves the default JSON template corresponding to a template filename. */
    public static String getDefaultTemplateForName(String name) {
        return switch (name) {
            case "template_sdxl_api.json", "sdxl_base_api.json" -> getSdxlDefaultTemplate();
            case "template_flux_api.json", "flux_base_api.json" -> getFluxDefaultTemplate();
            case "template_lumina2_api.json", "lumina2_base_api.json" -> getLumina2DefaultTemplate();
            case "template_sd3_api.json" -> getSd3DefaultTemplate();
            case "template_wan_api.json" -> getWanDefaultTemplate();
            case "template_hunyuan_api.json" -> getHunyuanDefaultTemplate();
            default -> getSd15DefaultTemplate();
        };
    }
    /** Returns Stable Diffusion 1.5 default template JSON. */
    public static String getSd15DefaultTemplate() { return load("template_sd15_api.json"); }
    /** Returns Stable Diffusion XL default template JSON. */
    public static String getSdxlDefaultTemplate() { return load("template_sdxl_api.json"); }
    /** Returns Flux default template JSON. */
    public static String getFluxDefaultTemplate() { return load("template_flux_api.json"); }
    /** Returns Lumina2 default template JSON. */
    public static String getLumina2DefaultTemplate() { return load("template_lumina2_api.json"); }
    /** Returns Stable Diffusion 3 default template JSON. */
    public static String getSd3DefaultTemplate() { return load("template_sd3_api.json"); }
    /** Returns Wan default template JSON. */
    public static String getWanDefaultTemplate() { return load("template_wan_api.json"); }
    /** Returns Hunyuan default template JSON. */
    public static String getHunyuanDefaultTemplate() { return load("template_hunyuan_api.json"); }

    private static String load(String filename) {
        // 1. Check local AppData cache
        Path appDataCache = Path.of(System.getProperty("user.home"), ".comfyui-companion", "templates", "comfyui", filename);
        if (Files.exists(appDataCache)) {
            try { return Files.readString(appDataCache, StandardCharsets.UTF_8); }
            catch (IOException e) { log.warn("Failed reading AppData cache for {}: {}", filename, e.getMessage()); }
        }

        // 2. Check local filesystem directory if present
        Path path = FS_DIR.resolve(filename);
        if (Files.exists(path)) {
            try { return Files.readString(path, StandardCharsets.UTF_8); }
            catch (IOException e) { log.warn("Filesystem read failed for {}: {}", path, e.getMessage()); }
        }

        // 3. Dynamic on-demand download from remote GitHub repository
        String remoteUrl = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/" + filename;
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(remoteUrl))
                    .timeout(java.time.Duration.ofSeconds(6))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                if (!Files.exists(appDataCache.getParent())) Files.createDirectories(appDataCache.getParent());
                Files.writeString(appDataCache, resp.body(), StandardCharsets.UTF_8);
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("Remote template download unavailable for {}: {}", filename, e.getMessage());
        }

        return "{}";
    }
}
