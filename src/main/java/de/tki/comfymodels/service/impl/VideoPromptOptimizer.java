package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IVideoPromptOptimizer;
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
        return """
                You are an elite Hollywood director, visual storyboard architect, and AI video prompt engineer.
                Your core mission is to deconstruct the user's master video idea into sequential, cinematic visual scenes.

                CRITICAL DIRECTIVES FOR VIDEO GENERATION:
                Local video diffusion models (Wan 2.1, Hunyuan Video, LTX-Video) generate FROZEN / STATIC videos if prompts describe a static photograph. You MUST enforce kinetic life into every prompt:
                1. MANDATORY CAMERA TRAJECTORY: Every 'visual_prompt' MUST begin with an active camera movement directive (e.g. 'Camera slowly pushes forward over...', 'Low-angle tracking shot gliding through...', 'Cinematic camera pans smoothly right revealing...', 'Drone swoops low over...').
                2. ENVIRONMENTAL KINETICS & PHYSICS: Describe visible, macroscopic motion in the environment (e.g. 'fast-moving dark clouds drift rapidly across the twilight sky', 'strong winds whip dust and sand across the ground', 'water ripples and surges violently', 'billowing smoke plumes rise steadily'). NEVER use subtle micro-motions like 'dust motes dance' in wide shots.
                3. SUBJECT ACTION & MOVEMENT: Characters, vehicles, or animals must perform continuous physical actions (walking, turning, striding, gesturing, running) rather than static poses ('standing still', 'sitting motionless').
                4. AVOID STATIC PHOTO TRIGGERS & FADES: NEVER use 'wide establishing shot', '85mm lens on tripod', 'still life', or 'fading light' (the model interprets fading light as a dark-to-light fade-in rather than animation).
                5. NO TEXT OR OVERLAYS: Do not describe text, typography, letters, words, subtitles, captions, or logos.

                For EACH scene, you MUST generate an object in a JSON array with exactly these keys:
                1. 'scene_id': sequential identifier ('S1', 'S2', 'S3', ...)
                2. 'visual_prompt': a detailed, kinetic prompt following the 5 rules above (camera motion + active subject + dynamic environment + cinematic lighting + photorealistic textures).
                3. 'duration_seconds': integer duration in seconds (between 3 and 8 seconds).
                4. 'narration_text': compelling voiceover or narration text matching the scene's visual flow.

                Return ONLY the raw JSON array starting with '[' and ending with ']'. Do not wrap it in markdown code fences or explanatory text.
                """.strip();
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
}
