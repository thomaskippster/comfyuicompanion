package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ModelArchitecture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Repository responsible for loading, persisting, and evaluating regular expression
 * mapping rules that classify model file names into their corresponding {@link ModelArchitecture}.
 */
@Component
public class ArchitectureMappingRepository {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectureMappingRepository.class);

    /**
     * Immutable mapping rule binding a compiled regular expression to a model architecture.
     */
    public record MappingRule(Pattern pattern, ModelArchitecture architecture) {
        public MappingRule(String regex, ModelArchitecture architecture) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), architecture);
        }
    }

    /**
     * Loads regex mapping rules from the specified JSON file.
     *
     * @param mappingFile configuration file containing rules
     * @return list of parsed mapping rules
     */
    public List<MappingRule> loadRules(File mappingFile) {
        List<MappingRule> loadedRules = new ArrayList<>();
        if (!mappingFile.exists()) {
            writeDefaultMappingFile(mappingFile);
        }

        try {
            String content = Files.readString(mappingFile.toPath(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            if (root.has("rules")) {
                JSONArray rulesArray = root.getJSONArray("rules");
                for (int i = 0; i < rulesArray.length(); i++) {
                    JSONObject ruleObj = rulesArray.getJSONObject(i);
                    String patternStr = ruleObj.getString("pattern");
                    String archStr = ruleObj.getString("architecture");
                    try {
                        ModelArchitecture arch = ModelArchitecture.valueOf(archStr);
                        loadedRules.add(new MappingRule(patternStr, arch));
                    } catch (IllegalArgumentException e) {
                        logger.error("⚠️ [ArchitectureMapping] Unknown architecture in mapping rule: {}", archStr);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("❌ [ArchitectureMapping] Failed to load architecture mapping: {}", e.getMessage());
            return loadFallbackRules();
        }
        return loadedRules;
    }

    /**
     * Provides built-in fallback rules when the configuration file cannot be read.
     *
     * @return default mapping rules list
     */
    public List<MappingRule> loadFallbackRules() {
        List<MappingRule> rules = new ArrayList<>();
        rules.add(new MappingRule(".*flux.*", ModelArchitecture.ARCH_FLUX));
        rules.add(new MappingRule(".*schnell.*", ModelArchitecture.ARCH_FLUX));
        rules.add(new MappingRule(".*sd[_-]?xl.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*pony.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*juggernaut.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*sd[_-]?3.*", ModelArchitecture.ARCH_SD3));
        rules.add(new MappingRule(".*stable[_-]?diffusion[_-]?3.*", ModelArchitecture.ARCH_SD3));
        rules.add(new MappingRule(".*hunyuan.*", ModelArchitecture.ARCH_HUNYUAN));
        rules.add(new MappingRule(".*wan2.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*wan_.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*lightx.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*bernini.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*ltx.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*longcat.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*lumina.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*qwen.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*firered.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*z[_-]image.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*acestep.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*ernie.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*1[_-]?5.*", ModelArchitecture.ARCH_SD15));
        rules.add(new MappingRule(".*sd[_-]?15.*", ModelArchitecture.ARCH_SD15));
        return rules;
    }

    /**
     * Generates a initial default JSON mapping file.
     *
     * @param file target file to write
     */
    public void writeDefaultMappingFile(File file) {
        String content = """
                {
                  "rules": [
                    { "pattern": ".*flux.*", "architecture": "ARCH_FLUX" },
                    { "pattern": ".*schnell.*", "architecture": "ARCH_FLUX" },
                    { "pattern": ".*sd[_-]?xl.*", "architecture": "ARCH_SDXL" },
                    { "pattern": ".*pony.*", "architecture": "ARCH_SDXL" },
                    { "pattern": ".*juggernaut.*", "architecture": "ARCH_SDXL" },
                    { "pattern": ".*sd[_-]?3.*", "architecture": "ARCH_SD3" },
                    { "pattern": ".*stable[_-]?diffusion[_-]?3.*", "architecture": "ARCH_SD3" },
                    { "pattern": ".*hunyuan.*", "architecture": "ARCH_HUNYUAN" },
                    { "pattern": ".*wan2.*", "architecture": "ARCH_WAN" },
                    { "pattern": ".*wan_.*", "architecture": "ARCH_WAN" },
                    { "pattern": ".*lightx.*", "architecture": "ARCH_WAN" },
                    { "pattern": ".*bernini.*", "architecture": "ARCH_WAN" },
                    { "pattern": ".*ltx.*", "architecture": "ARCH_WAN" },
                    { "pattern": ".*longcat.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*lumina.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*qwen.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*firered.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*z[_-]image.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*acestep.*", "architecture": "ARCH_LUMINA2" },
                    { "pattern": ".*ernie.*", "architecture": "ARCH_SDXL" },
                    { "pattern": ".*1[_-]?5.*", "architecture": "ARCH_SD15" },
                    { "pattern": ".*sd[_-]?15.*", "architecture": "ARCH_SD15" }
                  ]
                }""";
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            logger.info("💾 [ArchitectureMapping] Wrote default model architecture mapping config to: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("❌ [ArchitectureMapping] Failed to write default mapping config: {}", e.getMessage());
        }
    }
}
