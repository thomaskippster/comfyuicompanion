package de.tki.comfymodels;

import de.tki.comfymodels.domain.LaunchProfile;
import de.tki.comfymodels.service.impl.ProfileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileManagerTest {

    @Test
    void testTenDefaultProfilesLoaded(@TempDir Path tempDir) {
        ProfileManager manager = new ProfileManager();
        manager.init(tempDir);

        List<LaunchProfile> profiles = manager.loadProfiles();
        assertNotNull(profiles);
        assertEquals(10, profiles.size(), "There must be exactly 10 default launch profiles as defined in README.md");

        List<String> ids = profiles.stream().map(LaunchProfile::id).toList();
        assertTrue(ids.contains("standard_mode"));
        assertTrue(ids.contains("beast_mode"));
        assertTrue(ids.contains("background_mode"));
        assertTrue(ids.contains("flux_sd3_fp8"));
        assertTrue(ids.contains("cpu_mode"));
        assertTrue(ids.contains("extreme_savings"));
        assertTrue(ids.contains("network_hub"));
        assertTrue(ids.contains("directml_mode"));
        assertTrue(ids.contains("wsl_mode"));
        assertTrue(ids.contains("safe_mode"));

        LaunchProfile wsl = profiles.stream().filter(p -> "wsl_mode".equals(p.id())).findFirst().orElseThrow();
        assertTrue(wsl.executeInWsl());
    }
}
