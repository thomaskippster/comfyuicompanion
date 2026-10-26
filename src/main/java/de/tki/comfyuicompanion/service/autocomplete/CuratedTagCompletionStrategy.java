package de.tki.comfyuicompanion.service.autocomplete;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Strategy implementation providing instant autocomplete suggestions for curated quality,
 * lighting, camera, composition, and Booru prompt tags.
 */
@Component
public class CuratedTagCompletionStrategy implements IPromptCompletionStrategy {

    private static final List<String> CURATED_TAGS = List.of(
            "masterpiece", "best quality", "ultra-detailed", "extremely detailed", "highres",
            "absurdres", "8k uhd", "photorealistic", "hyperrealistic", "raw photo",
            "cinematic lighting", "volumetric lighting", "volumetric fog", "god rays",
            "rembrandt lighting", "studio lighting", "soft lighting", "dramatic shadows",
            "rim lighting", "bioluminescent", "neon glow", "cyberpunk", "steampunk",
            "depth of field", "bokeh", "sharp focus", "macro shot", "wide angle lens",
            "anamorphic lens", "35mm photograph", "85mm portrait lens", "f/1.4", "film grain",
            "subsurface scattering", "ray tracing", "unreal engine 5", "octane render",
            "concept art", "digital painting", "oil painting", "watercolor", "ink illustration",
            "anime screencap", "cel shading", "lineart", "monochrome", "pastel colors",
            "vibrant colors", "muted colors", "chiaroscuro", "rule of thirds",
            "dynamic angle", "from above", "from below", "dutch angle", "close-up",
            "full body", "cowboy shot", "upper body", "portrait", "solo", "1girl", "1boy",
            "looking at viewer", "detailed eyes", "intricate armor", "flowing hair",
            "windswept", "rain", "reflections", "cybernetic enhancements", "sci-fi",
            "fantasy landscape", "ancient ruins", "enchanted forest", "golden hour",
            "blue hour", "starry sky", "aurora borealis", "low quality", "worst quality",
            "blurry", "bad anatomy", "bad hands", "missing fingers", "extra digits",
            "watermark", "signature", "text", "jpeg artifacts", "cropped", "out of focus"
    );

    @Override
    public List<PromptCompletionCandidate> suggest(String activeToken, Path modelsBaseDir) {
        if (activeToken == null) {
            return Collections.emptyList();
        }
        String query = activeToken.trim().toLowerCase(Locale.ROOT);
        if (query.length() < 2 || query.startsWith("<lora:") || query.startsWith("embedding:")) {
            return Collections.emptyList();
        }

        List<PromptCompletionCandidate> matches = new ArrayList<>();
        for (String tag : CURATED_TAGS) {
            String lowerTag = tag.toLowerCase(Locale.ROOT);
            if (lowerTag.startsWith(query)) {
                matches.add(PromptCompletionCandidate.builder()
                        .insertText(tag)
                        .displayText(tag + " [Tag]")
                        .category("Tag")
                        .priority(65)
                        .build());
            } else if (lowerTag.contains(query)) {
                matches.add(PromptCompletionCandidate.builder()
                        .insertText(tag)
                        .displayText(tag + " [Tag]")
                        .category("Tag")
                        .priority(50)
                        .build());
            }
        }
        return matches;
    }
}
