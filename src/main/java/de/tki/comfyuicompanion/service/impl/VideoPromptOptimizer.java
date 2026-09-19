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
        String modelDirective = (targetModel != null && !targetModel.isBlank())
                ? "Target Video Diffusion Model: " + targetModel + ".\n"
                : "";
        String resolutionDirective = (width > 0 && height > 0)
                ? String.format("Target Resolution & Aspect Ratio: %dx%d.\n", width, height)
                : "";

        return String.format("""
                You are a master Hollywood film director, visual storyboard architect, and senior AI video prompt engineer.
                Your mission is to deconstruct the user's master video idea into a sequence of exactly %d sequential, cinematic visual scenes.
                %s%s
                DIRECTOR'S 5-BEAT DRAMATIC ARC:
                1. Scene 1 (Establishing/Atmosphere): Wide cinematic tracking shot establishing location, scale, horizon, and consistent lighting mood.
                2. Scene 2 (Focus/Anticipation): Medium slow push-in focusing on key subjects, machinery, or tangible physical build-up under the same lighting.
                3. Scene 3 (Climax/Kinetic Peak): Low-angle dynamic tracking shot capturing maximum macroscopic physics (ignition, blast, rapid movement, pressure waves).
                4. Scene 4 (Reaction/Shift): Perspective shift or counter-shot capturing aftermath, bystanders, or environmental elements under the same atmosphere.
                5. Scene 5 (Resolution/Outro): High crane tilt-up or drone pull-back into the sky, lingering smoke dissipation, revealing vast horizon under the same lighting mood.

                MANDATORY TEMPORAL & LIGHTING CONTINUITY:
                The entire video sequence depicts ONE SINGLE CONTINUOUS EVENT at ONE SPECIFIC TIME OF DAY.
                - Analyze the master concept and establish ONE fixed environmental lighting condition and sky palette (e.g. 'golden hour sunset with warm backlight', 'bright daytime clear desert sun', or 'deep midnight illuminated by industrial floodlights').
                - EVERY scene's 'visual_prompt' MUST strictly adhere to this IDENTICAL lighting condition and atmosphere.
                - NEVER jump from dusk to bright day to night between scenes. The time of day remains locked across all scenes.

                CRITICAL DIRECTIVES FOR VIDEO GENERATION:
                Local video diffusion models (Wan 2.1, Hunyuan Video, LTX-Video) generate FROZEN / STATIC videos if prompts describe a static photograph. You MUST enforce kinetic life into every prompt:
                1. MANDATORY CAMERA TRAJECTORY: Every 'visual_prompt' MUST begin with an active camera movement directive (e.g. 'Camera slowly pushes forward over...', 'Low-angle tracking shot gliding through...', 'Cinematic camera pans smoothly right revealing...', 'Drone sweeps low over...').
                2. ENVIRONMENTAL KINETICS & PHYSICS (HIGH-OCTANE MACROSCOPIC MOTION): Describe visible, high-speed physical velocity in the environment and subjects (e.g. 'massive rocket exhaust flames roar downwards, volcanic smoke plumes billow outward violently, ground vibration shaking dust', 'crowds surge against barriers waving excitedly', 'water ripples and surges violently', 'gale-force winds whip sand'). NEVER use subtle micro-motions like 'dust motes dance' in wide shots.
                3. SUBJECT ACTION & MOVEMENT: Characters, vehicles, or animals must perform continuous physical actions (running, launching, striding, gesturing, surging) rather than static poses ('standing still', 'sitting motionless').
                4. AVOID STATIC PHOTO TRIGGERS & FADES: NEVER use 'wide establishing shot', '85mm lens on tripod', 'still life', or 'fading light' (the model interprets fading light as a dark-to-light fade-in rather than animation).
                5. DUAL-ANCHOR FORMULA & UNIFIED GLOBAL STYLE ANCHOR:
                   To maintain visual and stylistic continuity across cuts, every 'visual_prompt' MUST conclude with the EXACT SAME cohesive visual style and lighting anchor.
                6. STRICT NEGATIVE DIRECTIVES:
                   - NO TEXT OR NUMBERS: Never describe countdown timers, clocks, digital readouts, subtitles, letters, or logos.
                   - NO CUTS INSIDE A SCENE: Each scene is ONE single continuous camera movement.
                   - NO ABSTRACT METAPHORS: Do not write 'anticipation', 'aftermath', or 'tension'. Only describe tangible light, shadows, velocity, and matter.

                NARRATION PROGRESSION & VOICEOVER:
                - Each 'narration_text' MUST be a unique, evolving poetic/documentary voiceover thought.
                - NEVER repeat words, phrases, or sentence motifs across scenes (e.g. do NOT repeat 'earth tries to break free').

                For EACH scene, you MUST generate an object in a JSON array with exactly these keys:
                1. 'scene_id': sequential identifier ('S1', 'S2', 'S3', ...)
                2. 'visual_prompt': a detailed, kinetic prompt following all directives above (camera trajectory + active subject + dynamic environment + global style anchor).
                3. 'duration_seconds': integer duration in seconds (between 4 and 6 seconds).
                4. 'narration_text': compelling, unique voiceover line matching the scene's emotional pacing.

                Return ONLY the raw JSON array starting with '[' and ending with ']'. Do not wrap it in markdown code fences or explanatory text.
                """, count, modelDirective, resolutionDirective).strip();
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
