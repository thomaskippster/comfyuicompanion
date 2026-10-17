package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service component responsible for loading, downloading, and converting
 * ComfyUI workflow JSON blueprints into API executable format.
 */
public class PromptLabWorkflowResolver {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabWorkflowResolver.class);

    private final ConfigService configService;
    private final IComfyTemplateService comfyTemplateService;
    private final IWorkflowDownloader workflowDownloader;
    private final PromptLabModelResolver modelResolver;

    /**
     * Constructs a new PromptLabWorkflowResolver.
     *
     * @param configService        configuration service for file paths
     * @param comfyTemplateService template generator service
     * @param workflowDownloader   remote workflow downloader
     * @param modelResolver        model injection resolver
     */
    public PromptLabWorkflowResolver(ConfigService configService,
                                     IComfyTemplateService comfyTemplateService,
                                     IWorkflowDownloader workflowDownloader,
                                     PromptLabModelResolver modelResolver) {
        this.configService = configService;
        this.comfyTemplateService = comfyTemplateService;
        this.workflowDownloader = workflowDownloader;
        this.modelResolver = modelResolver;
    }

    /**
     * Resolves the workflow JSON content for a target blueprint from local disks,
     * remote registry download, or template generation.
     *
     * @param targetEntry the blueprint entry to resolve
     * @return the resolved JSON content string, or null if unresolvable
     */
    public String resolveBlueprintWorkflowJson(BlueprintGalleryTab.BlueprintEntry targetEntry) {
        if (targetEntry == null) {
            return null;
        }
        try {
            String jsonContent = null;
            if (targetEntry.filePath != null) {
                File localFile = new File(targetEntry.filePath);
                if (localFile.exists()) {
                    jsonContent = Files.readString(localFile.toPath(), StandardCharsets.UTF_8).trim();
                }
            }

            // 1. Local-First: Check user workflows first, then fallback to shipped workflows folder
            if (jsonContent == null) {
                List<File> searchDirs = new ArrayList<>();
                if (configService != null) {
                    searchDirs.add(configService.getUserWorkflowsDir());
                    searchDirs.add(configService.getShippedWorkflowsDir());
                } else {
                    searchDirs.add(new File("user_workflows"));
                    searchDirs.add(new File("workflows"));
                }

                for (File dir : searchDirs) {
                    if (dir != null && dir.exists() && dir.isDirectory()) {
                        if (targetEntry.registryWorkflow != null) {
                            if (targetEntry.registryWorkflow.getId() != null) {
                                File registryFile = new File(dir, targetEntry.registryWorkflow.getId() + ".json");
                                if (registryFile.exists()) {
                                    jsonContent = Files.readString(registryFile.toPath(), StandardCharsets.UTF_8).trim();
                                    break;
                                }
                            }
                            if (jsonContent == null && targetEntry.registryWorkflow.getTitle() != null) {
                                String sanitized = targetEntry.registryWorkflow.getTitle()
                                        .replaceAll("[^a-zA-Z0-9\\-_\\s]", "")
                                        .trim()
                                        .replaceAll("\\s+", "_")
                                        .toLowerCase(Locale.ROOT);
                                File titleFile = new File(dir, sanitized + ".json");
                                if (titleFile.exists()) {
                                    jsonContent = Files.readString(titleFile.toPath(), StandardCharsets.UTF_8).trim();
                                    break;
                                }
                            }
                        }
                        if (jsonContent == null) {
                            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
                            if (files != null) {
                                for (File f : files) {
                                    if (isBlueprintNameMatch(targetEntry.name, f.getName().replace(".json", ""))
                                            || (targetEntry.filename != null && f.getName().equalsIgnoreCase(targetEntry.filename))) {
                                        jsonContent = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
                                        break;
                                    }
                                }
                            }
                        }
                        if (jsonContent != null) {
                            break;
                        }
                    }
                }
            }

            // 2. Remote Download fallback if not available on disk
            if (jsonContent == null && targetEntry.registryWorkflow != null && workflowDownloader != null) {
                try {
                    File downloaded = workflowDownloader.downloadWorkflowAsync(targetEntry.registryWorkflow).get();
                    if (downloaded != null && downloaded.exists()) {
                        jsonContent = Files.readString(downloaded.toPath(), StandardCharsets.UTF_8).trim();
                    }
                } catch (Exception dlEx) {
                    logger.warn("Failed to download workflow for blueprint {}: {}", targetEntry.name, dlEx.getMessage());
                }
            }

            // 3. Fallback to template generator if workflow cannot be found
            if (jsonContent == null && comfyTemplateService != null && modelResolver != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(targetEntry.name);
                if (template != null) {
                    jsonContent = modelResolver.injectModelsIntoTemplate(template.getContent(), targetEntry.name);
                }
            }

            return jsonContent;
        } catch (Exception ex) {
            logger.error("Error resolving workflow JSON for blueprint: " + targetEntry.name, ex);
            return null;
        }
    }

    /**
     * Converts a UI-formatted workflow JSON graph to API prompt format if nodes are present.
     *
     * @param jsonContent the raw workflow JSON string
     * @return the API-formatted JSON string or original string if already formatted
     */
    public String convertUiToApiIfNeeded(String jsonContent) {
        if (jsonContent == null) {
            return null;
        }
        try {
            JSONObject rawObj = new JSONObject(jsonContent);
            if (rawObj.has("nodes")) {
                JSONObject flattened = ComfyPipelineService.flattenWorkflow(rawObj);
                JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);
                return apiPayload.toString(2);
            }
        } catch (Exception ignored) {
            // Keep raw content if JSON parse fails
        }
        return jsonContent;
    }

    /**
     * Compares two blueprint names using fuzzy matching rules.
     *
     * @param name1 first name
     * @param name2 second name
     * @return true if both names refer to the same blueprint
     */
    public boolean isBlueprintNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String clean2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) return true;

        String[] words1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        String[] words2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        int matchCount = 0;
        for (String w1 : words1) {
            if (w1.length() < 3) continue;
            for (String w2 : words2) {
                if (w1.equals(w2)) {
                    matchCount++;
                    break;
                }
            }
        }
        return matchCount >= Math.min(words1.length, words2.length);
    }
}
