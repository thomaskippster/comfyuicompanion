package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Enterprise implementation of {@link IVideoPromptOptimizer}.
 * Guarantees that generated and edited video prompts contain explicit camera trajectories
 * and kinetic physics directives required by video diffusion models (Wan 2.1/2.2, Hunyuan, LTX).
 */
@Service
/**
 * Represents the video prompt optimizer class.
 */
public class VideoPromptOptimizer implements IVideoPromptOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(VideoPromptOptimizer.class);

    // Regex pattern identifying active camera motion directives
    private static final Pattern CAMERA_MOTION_PATTERN = Pattern.compile(
            "(?i)\\b(camera (slowly |rapidly |smoothly )?(pushes|pulls|tracks|pans|tilts|glides|dollies|zooms|moves|sweeps|flies|swoops|orbits)|" +
                    "tracking shot|push-in|crane shot|drone (swoop|flyover|shot)|steadicam|handheld camera motion|orbiting shot|pan (left|right|up|down))\\b"
    );

    // Regex pattern identifying physical/kinetic action verbs (all tenses)
    private static final Pattern KINETIC_ACTION_PATTERN = Pattern.compile(
            "(?i)\\b(blow|blows|blowing|blew|walk|walks|walking|walked|run|runs|running|ran|stride|strides|striding|strode|move|moves|moving|moved|swirl|swirls|swirling|flow|flows|flowing|flowed|crash|crashes|crashing|crashed|surge|surges|surging|surged|flutter|flutters|fluttering|drift|drifts|drifting|drifted|fall|falls|falling|fell|rise|rises|rising|rose|swoop|swoops|swooping|speed|speeds|speeding|spin|spins|spinning|spun|whip|whips|whipping|whipped|dance|dances|dancing|glide|glides|gliding)\\b"
    );

    // Problematic static terms that prompt video diffusion models to freeze
    private static final List<ReplacementRule> STATIC_REPLACEMENTS = List.of(
            new ReplacementRule(Pattern.compile("(?i)\\bwide establishing shot\\b"), "wide cinematic tracking shot"),
            new ReplacementRule(Pattern.compile("(?i)\\bestablishing shot\\b"), "slow push-in tracking shot"),
            new ReplacementRule(Pattern.compile("(?i)\\bphoto of\\b"), "cinematic footage of"),
            new ReplacementRule(Pattern.compile("(?i)\\bphotograph of\\b"), "cinematic footage of"),
            new ReplacementRule(Pattern.compile("(?i)\\bstatic shot\\b"), "smooth cinematic camera glide"),
            new ReplacementRule(Pattern.compile("(?i)\\bfading (orange |golden |warm |dim )?light\\b"), "deep twilight backlight"),
            new ReplacementRule(Pattern.compile("(?i)\\bfading light\\b"), "deep twilight ambient lighting"),
            new ReplacementRule(Pattern.compile("(?i)\\bdust motes dance in the air\\b"), "wind whips swirling dust across the scene")
    );

    private record ReplacementRule(Pattern pattern, String replacement) {}

    @Override
    public String buildStoryboardSystemPrompt() {
        return buildStoryboardSystemPrompt(null, 0, 0, 5);
    }

    @Override
    public String buildStoryboardSystemPrompt(String targetModel, int width, int height, int sceneCount) {
        int count = sceneCount > 0 ? sceneCount : 5;
        boolean isQwen = targetModel != null && targetModel.toLowerCase().contains("qwen");
        String modelDirective = (targetModel != null && !targetModel.isBlank())
                ? "Target Video Diffusion Architecture: " + targetModel + ".\n"
                : "";
        String resolutionDirective = (width > 0 && height > 0)
                ? String.format("Target Resolution & Aspect Ratio: %dx%d.\n", width, height)
                : "";
        String modelSpecificNote = isQwen
                ? "Ensure strictly valid JSON matching Qwen ChatML schema without any explanatory prefix or markdown code fence."
                : "Return strictly valid JSON only without markdown code fences.";

        return String.format("""
                You are an award-winning cinematographer and prompt architect for generative AI video pipelines (Flux, SDXL, Wan 2.1, LTX-Video). Your task is to transform the user's idea into a visually stunning, temporally consistent storyboard in strict JSON format.
                %s%s%s

                STRICT RULES FOR EVERY SCENE:
                1. SEPARATE NARRATION FROM VISUALS:
                   - "narration": Short, atmospheric voiceover/subtitle sentence (max 12 words, in the language of the user's prompt).
                   - "visual_prompt": STRICTLY in English. Describe ONLY what a physical camera lens can see.
                2. NO ABSTRACT METAPHORS IN VISUAL_PROMPT:
                   - NEVER use poetic phrases like "shattered silence", "collective breath", "inevitable", or "primal scream" in "visual_prompt".
                   - Translate emotions into concrete physical visuals (e.g., instead of "tension in the air", write "extreme close-up of a weathered astronaut helmet visor reflecting a bright rocket ignition, crisp rim lighting").
                3. NO UNDEFINED CROWDS OR BLOBS:
                   - NEVER generate vague crowds in fog or darkness. Always focus on 1 clearly defined hero subject per shot with distinct foreground, midground, and background depth.
                4. MANDATORY VISUAL PROMPT FORMULA:
                   Structure every "visual_prompt" exactly in this order:
                   [Camera Shot & Angle] + [Concrete Hero Subject with specific textures] + [Environment & Depth] + [Lighting & Atmosphere] + [Technical Camera Specs].
                5. GLOBAL STYLE ANCHOR FOR TEMPORAL CONSISTENCY:
                   - Define a single "style_anchor" (lens, film stock, color grading, lighting style) that keeps all scenes visually unified without locking pixel coordinates.
                6. GLOBAL NEGATIVE PROMPT:
                   - Provide a strong "negative_prompt": "blurry, out of focus, abstract blobs, faceless crowd, muddy colors, color banding, overexposed vertical streaks, ghosting, morphing, low resolution, deformed, watermark, text".

                REQUIRED SCENE COUNT: Exactly %d sequential scenes.

                OUTPUT STRICTLY VALID JSON ONLY (NO MARKDOWN BLOCKS, NO EXPLANATIONS):
                {
                  "project_title": "String",
                  "style_anchor": "35mm anamorphic lens, f/2.8, cinematic color grading, volumetric lighting, razor-sharp focus, intricate surface textures, 8k photorealistic",
                  "negative_prompt": "blurry, out of focus, abstract blobs, faceless crowd, muddy colors, color banding, overexposed streaks, ghosting, low resolution, text, watermark",
                  "scenes": [
                    {
                      "scene_id": 1,
                      "duration_seconds": 4,
                      "camera_motion": "slow_push_in | slow_pan_right | slow_pan_left | static_focus | crane_up",
                      "narration": "String",
                      "visual_prompt": "String"
                    }
                  ]
                }
                """, modelDirective, resolutionDirective, modelSpecificNote, count).strip();
    }

    @Override
    public String optimizeVideoPrompt(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.trim().isEmpty()) {
            return "Camera slowly pushes forward, cinematic scene with dynamic motion.";
        }

        String cleaned = rawPrompt.trim();

        // 1. Sanitize static photography keywords
        for (ReplacementRule rule : STATIC_REPLACEMENTS) {
            cleaned = rule.pattern().matcher(cleaned).replaceAll(rule.replacement());
        }

        // 2. Ensure active camera motion is present at the beginning or within the prompt
        boolean hasCamera = CAMERA_MOTION_PATTERN.matcher(cleaned).find();
        if (!hasCamera) {
            // Select appropriate camera motion based on content hints
            String cameraPrefix;
            String lower = cleaned.toLowerCase();
            if (lower.contains("landscape") || lower.contains("desert") || lower.contains("mountain") || lower.contains("sky") || lower.contains("sea")) {
                cameraPrefix = "Camera slowly pushes forward over ";
            } else if (lower.contains("portrait") || lower.contains("face") || lower.contains("character") || lower.contains("person") || lower.contains("man") || lower.contains("woman")) {
                cameraPrefix = "Smooth tracking camera moves slowly past ";
            } else {
                cameraPrefix = "Cinematic camera glides forward through ";
            }

            // Lowercase first letter if prepending cleanly
            if (cleaned.length() > 0 && Character.isUpperCase(cleaned.charAt(0))) {
                cleaned = cameraPrefix + Character.toLowerCase(cleaned.charAt(0)) + cleaned.substring(1);
            } else {
                cleaned = cameraPrefix + cleaned;
            }
        }

        // 3. Ensure environmental dynamics or kinetic verbs exist
        boolean hasAction = KINETIC_ACTION_PATTERN.matcher(cleaned).find();
        if (!hasAction) {
            cleaned = cleaned + ", fast-moving clouds drift in the background with windswept atmospheric movement";
        }

        return cleaned;
    }

    @Override
    public String enrichFallbackPrompt(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) {
            return "Camera slowly pushes forward, cinematic scene with dynamic atmospheric movement.";
        }

        String trimmed = sentence.trim();
        // Remove trailing punctuation for smooth sentence chaining
        trimmed = trimmed.replaceAll("[.!?]+$", "");

        String base = "Camera slowly pushes forward, cinematic view of " + trimmed +
                ", fast-moving clouds with windswept atmospheric motion, dynamic cinematic lighting, photorealistic textures";

        return optimizeVideoPrompt(base);
    }

    @Override
    public boolean hasMotionDirectives(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        boolean hasCamera = CAMERA_MOTION_PATTERN.matcher(prompt).find();
        boolean hasAction = KINETIC_ACTION_PATTERN.matcher(prompt).find();
        return hasCamera || hasAction;
    }

    private enum LightingGroup {
        NIGHT("night", java.util.List.of("pitch-black night", "pitch black night", "night silhouette", "dark night sky", "midnight sky", "deep night", "nighttime", "night")),
        DUSK_DAWN("dusk", java.util.List.of("purple-hued dusk", "purple dusk", "dusk twilight", "dusk", "twilight", "dawn", "sunrise")),
        GOLDEN_HOUR("golden hour", java.util.List.of("golden hour sunset", "golden hour", "sunset backlight", "during sunset", "sunset")),
        DAYLIGHT("daylight", java.util.List.of("bright midday", "noon sun", "harsh noon sunlight", "bright midday sun", "clear daylight", "daylight"));

        private final String label;
        private final java.util.List<String> keywords;

        LightingGroup(String label, java.util.List<String> keywords) {
            this.label = label;
            this.keywords = keywords;
        }

        public static LightingGroup find(String prompt) {
            if (prompt == null || prompt.isBlank()) return null;
            String lower = prompt.toLowerCase();
            for (LightingGroup g : values()) {
                for (String kw : g.keywords) {
                    if (lower.contains(kw)) {
                        return g;
                    }
                }
            }
            return null;
        }

        /**
         * Handles the strip from operation.
         * @param prompt the prompt
         * @return the String result
         */
        public String stripFrom(String prompt) {
            String res = prompt;
            for (String kw : keywords) {
                res = res.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(kw) + "\\b", "");
            }
            return res.replaceAll("\\s{2,}", " ").trim();
        }
    }

    @Override
    public void harmonizeSceneLighting(java.util.List<de.tki.comfyuicompanion.domain.Scene> scenes) {
        if (scenes == null || scenes.size() <= 1) {
            return;
        }

        // 1. Detect Scene 1's lighting group
        String scene1Prompt = scenes.get(0).getPrompt();
        LightingGroup masterGroup = LightingGroup.find(scene1Prompt);
        if (masterGroup == null) {
            return; // No explicit lighting group established in Scene 1
        }

        String masterAnchor = extractLightingAnchor(scene1Prompt, masterGroup);

        // 2. Harmonize subsequent scenes ONLY if they contradict Scene 1's lighting group
        for (int i = 1; i < scenes.size(); i++) {
            de.tki.comfyuicompanion.domain.Scene sc = scenes.get(i);
            String prompt = sc.getPrompt();
            if (prompt == null || prompt.isBlank()) continue;

            LightingGroup sceneGroup = LightingGroup.find(prompt);
            if (sceneGroup != null && sceneGroup != masterGroup) {
                // Conflicting lighting group detected! Strip it and apply the master lighting anchor
                String harmonized = sceneGroup.stripFrom(prompt);
                if (harmonized.endsWith(".")) {
                    harmonized = harmonized.substring(0, harmonized.length() - 1).trim();
                }
                harmonized = harmonized + ", " + masterAnchor + ".";
                sc.setPrompt(harmonized);
            }
        }
    }

    private String extractLightingAnchor(String prompt, LightingGroup group) {
        if (prompt == null || prompt.isBlank() || group == null) {
            return "cinematic atmospheric lighting";
        }
        String lower = prompt.toLowerCase();
        for (String kw : group.keywords) {
            int idx = lower.indexOf(kw);
            if (idx >= 0) {
                int endIdx = prompt.indexOf('.', idx);
                if (endIdx > idx) {
                    return prompt.substring(idx, endIdx).trim();
                }
                return kw + " lighting, atmospheric haze";
            }
        }
        return group.label + " lighting, atmospheric haze";
    }
}
