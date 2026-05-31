package de.tki.comfymodels.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.tki.comfymodels.domain.LaunchProfile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProfileManager {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private Path profilesPath;

    public void init(Path appRoot) {
        this.profilesPath = appRoot.resolve("profiles.json");
    }

    public void saveProfiles(List<LaunchProfile> profiles) throws IOException {
        if (profilesPath == null) throw new IllegalStateException("ProfileManager not initialized with appRoot");
        MAPPER.writeValue(profilesPath.toFile(), profiles);
    }

    public List<LaunchProfile> loadProfiles() {
        if (profilesPath == null) return new ArrayList<>();
        
        List<LaunchProfile> defaults = createDefaultProfiles();
        if (!Files.exists(profilesPath)) {
            try { saveProfiles(defaults); } catch (IOException e) { e.printStackTrace(); }
            return defaults;
        }
        
        try {
            List<LaunchProfile> loaded = MAPPER.readValue(profilesPath.toFile(), new TypeReference<>() {});
            
            // Build the updated list of profiles starting with defaults
            List<LaunchProfile> updated = new ArrayList<>(defaults);
            Set<String> defaultIds = new HashSet<>();
            for (LaunchProfile p : defaults) {
                defaultIds.add(p.id());
            }
            
            // Add user's custom profiles (that do not conflict with default IDs)
            boolean listChanged = false;
            for (LaunchProfile p : loaded) {
                if (!defaultIds.contains(p.id())) {
                    updated.add(p);
                    listChanged = true;
                }
            }
            
            // Check if loaded list differs in size or elements from updated list
            if (loaded.size() != updated.size() || !loaded.equals(updated)) {
                listChanged = true;
            }
            
            if (listChanged) {
                try {
                    saveProfiles(updated);
                } catch (IOException e) {
                    System.err.println("Error updating profiles: " + e.getMessage());
                }
            }
            
            return updated;
        } catch (IOException e) {
            System.err.println("Error loading profiles: " + e.getMessage());
            return defaults;
        }
    }

    private List<LaunchProfile> createDefaultProfiles() {
        List<LaunchProfile> profiles = new ArrayList<>();
        
        profiles.add(new LaunchProfile(
            "standard_mode",
            "Standard Mode (Default)",
            "Starts ComfyUI in normal mode with balanced VRAM usage. Recommended for daily use.",
            false, "python", List.of(), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "beast_mode",
            "Beast Mode (High VRAM)",
            "Maximum VRAM usage for high-end hardware (e.g. RTX 3090/4090). Ideal for high resolutions and fast upscaling.",
            false, "python", List.of("--highvram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "background_mode",
            "Background / Gaming Mode (Low VRAM)",
            "Aggressive VRAM caching. Frees up VRAM for other applications and gaming in the foreground.",
            false, "python", List.of("--lowvram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "flux_sd3_fp8",
            "Flux & SD3 Optimization (FP8)",
            "Forces FP8 precision for UNet and text encoder. Allows running large models like Flux or SD3 on GPUs with 8-12 GB VRAM.",
            false, "python", List.of("--fp8_e4m3fn-text-enc", "--fp8_e4m3fn-unet"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "cpu_mode",
            "CPU Mode (No GPU)",
            "Runs all calculations on the main processor (CPU). Extremely slow, but works entirely without a compatible graphics card.",
            false, "python", List.of("--cpu"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "extreme_savings",
            "Extreme Savings (No VRAM)",
            "Offloads all models to system memory and loads them only block-by-block into the GPU. Very slow, but saves maximum graphics memory.",
            false, "python", List.of("--novram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "network_hub",
            "Network Hub (LAN Mode)",
            "Opens the ComfyUI server to the local network (0.0.0.0) so that other devices in the LAN can access it.",
            false, "python", List.of("--listen", "0.0.0.0"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "directml_mode",
            "AMD / Intel GPU Mode (DirectML)",
            "Enables DirectML support for AMD Radeon or Intel Arc graphics cards under Windows.",
            false, "python", List.of("--directml"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "wsl_mode",
            "WSL / Linux Subsystem Mode",
            "Tunnels the startup command through the Windows Subsystem for Linux (WSL) for maximum Linux compatibility.",
            true, "python3", List.of(), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "safe_mode",
            "Safe Mode (Troubleshooting)",
            "Temporarily disables all custom nodes. Perfect for making the web UI work again after faulty updates.",
            false, "python", List.of("--disable-all-custom-nodes"), new java.util.HashMap<>()
        ));

        return profiles;
    }
}
