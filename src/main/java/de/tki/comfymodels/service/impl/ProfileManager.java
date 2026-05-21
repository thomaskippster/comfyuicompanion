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
                    System.err.println("Fehler beim Aktualisieren der Profile: " + e.getMessage());
                }
            }
            
            return updated;
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Profile: " + e.getMessage());
            return defaults;
        }
    }

    private List<LaunchProfile> createDefaultProfiles() {
        List<LaunchProfile> profiles = new ArrayList<>();
        
        profiles.add(new LaunchProfile(
            "standard_mode",
            "Standard Mode (Default)",
            "Startet ComfyUI im normalen Modus mit ausgewogener VRAM-Nutzung. Empfohlen für den täglichen Gebrauch.",
            false, "python", List.of("--normal-vram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "beast_mode",
            "Beast Mode (High VRAM)",
            "Maximale VRAM-Nutzung für High-End-Hardware (z.B. RTX 3090/4090). Ideal für große Auflösungen und schnelles Upscaling.",
            false, "python", List.of("--highvram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "background_mode",
            "Background / Gaming Mode (Low VRAM)",
            "Aggressives VRAM-Caching. Gibt VRAM für andere Anwendungen und Gaming im Vordergrund frei.",
            false, "python", List.of("--lowvram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "flux_sd3_fp8",
            "Flux & SD3 Optimization (FP8)",
            "Erzwingt FP8-Präzision für UNet und Text-Encoder. Ermöglicht das Ausführen großer Modelle wie Flux oder SD3 auf GPUs mit 8-12 GB VRAM.",
            false, "python", List.of("--fp8_e4m3fn-text-enc", "--fp8_e4m3fn-unet"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "cpu_mode",
            "CPU Mode (No GPU)",
            "Führt alle Berechnungen auf dem Hauptprozessor (CPU) aus. Extrem langsam, funktioniert aber komplett ohne kompatible Grafikkarte.",
            false, "python", List.of("--cpu"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "extreme_savings",
            "Extreme Savings (No VRAM)",
            "Lagert alle Modelle in den Hauptspeicher aus und lädt sie nur blockweise in die GPU. Sehr langsam, spart aber maximalen Grafikspeicher.",
            false, "python", List.of("--novram"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "network_hub",
            "Network Hub (LAN Mode)",
            "Öffnet den ComfyUI-Server für das lokale Netzwerk (0.0.0.0), damit andere Geräte im LAN darauf zugreifen können.",
            false, "python", List.of("--listen", "0.0.0.0"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "directml_mode",
            "AMD / Intel GPU Mode (DirectML)",
            "Aktiviert DirectML-Unterstützung für AMD Radeon oder Intel Arc Grafikkarten unter Windows.",
            false, "python", List.of("--directml"), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "wsl_mode",
            "WSL / Linux Subsystem Mode",
            "Tunnelt den Startbefehl durch das Windows Subsystem für Linux (WSL) für maximale Linux-Kompatibilität.",
            true, "python3", List.of(), new java.util.HashMap<>()
        ));

        profiles.add(new LaunchProfile(
            "safe_mode",
            "Safe Mode (Troubleshooting)",
            "Deaktiviert alle Custom Nodes temporär. Perfekt, um nach fehlerhaften Updates die Web-UI wieder lauffähig zu machen.",
            false, "python", List.of("--disable-custom-nodes"), new java.util.HashMap<>()
        ));

        return profiles;
    }
}
