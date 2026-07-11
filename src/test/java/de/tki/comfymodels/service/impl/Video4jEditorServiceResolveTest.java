package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.Video4jEditorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Video4jEditorServiceResolveTest {

    @TempDir
    Path tempDir;

    private Video4jEditorService service;
    private ConfigService configService;

    @BeforeEach
    public void setup() throws Exception {
        configService = mock(ConfigService.class);
        when(configService.getResolvedOutputDir()).thenReturn(tempDir.toString());
        service = new Video4jEditorService(configService);
        // Inject a real ProcessTracker (the @Autowired(required=false) field is null otherwise).
        Field f = Video4jEditorService.class.getDeclaredField("processTracker");
        f.setAccessible(true);
        f.set(service, new de.tki.comfymodels.service.impl.ProcessTracker());
    }

    @Test
    public void resolveSceneVideo_findsFileInWorkspaceRoot() throws Exception {
        File target = new File(System.getProperty("user.dir"), "scene_S42_final.mp4");
        byte[] data = new byte[2048];
        try (FileOutputStream fos = new FileOutputStream(target)) { fos.write(data); }
        try {
            Scene scene = new Scene("S42", "test prompt", 0, 120, null, null);
            File resolved = (File) invokePrivate("resolveSceneVideo", new Class<?>[]{Scene.class}, new Object[]{scene});
            assertNotNull(resolved, "Resolver must find scene_<id>_final.mp4 in the workspace root");
            assertTrue(resolved.getName().endsWith("scene_S42_final.mp4"));
        } finally {
            target.delete();
        }
    }

    @Test
    public void resolveSceneVideo_findsFileInConfiguredOutputDir() throws Exception {
        File target = new File(tempDir.toFile(), "scene_S99_final.mp4");
        try (FileOutputStream fos = new FileOutputStream(target)) { fos.write(new byte[2048]); }
        Scene scene = new Scene("S99", "test prompt", 0, 120, null, null);
        File resolved = (File) invokePrivate("resolveSceneVideo", new Class<?>[]{Scene.class}, new Object[]{scene});
        assertNotNull(resolved, "Resolver must find scene_<id>_final.mp4 in the configured output dir");
        assertEquals(target.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void resolveSceneVideo_findsFileInVideoSubfolder() throws Exception {
        File subdir = new File(tempDir.toFile(), "video");
        subdir.mkdirs();
        File target = new File(subdir, "videoarchitect_S11_00001_.mp4");
        try (FileOutputStream fos = new FileOutputStream(target)) { fos.write(new byte[2048]); }
        Scene scene = new Scene("S11", "test prompt", 0, 120, null, null);
        File resolved = (File) invokePrivate("resolveSceneVideo", new Class<?>[]{Scene.class}, new Object[]{scene});
        assertNotNull(resolved, "Resolver must find scene_<id>.mp4 in the video/ subfolder");
        assertTrue(resolved.getName().contains("S11"));
    }

    @Test
    public void resolveSceneVideo_returnsNullWhenNothingMatches() throws Exception {
        Scene scene = new Scene("NOPE_X", "test prompt", 0, 120, null, null);
        File resolved = (File) invokePrivate("resolveSceneVideo", new Class<?>[]{Scene.class}, new Object[]{scene});
        assertNull(resolved, "Resolver must return null when no candidate file exists");
    }

    @Test
    public void resolveSceneVideo_skipsTooSmallFiles() throws Exception {
        File tiny = new File(tempDir.toFile(), "scene_SMOL_final.mp4");
        try (FileOutputStream fos = new FileOutputStream(tiny)) { fos.write(new byte[100]); }
        Scene scene = new Scene("SMOL", "test prompt", 0, 120, null, null);
        File resolved = (File) invokePrivate("resolveSceneVideo", new Class<?>[]{Scene.class}, new Object[]{scene});
        assertNull(resolved, "Resolver must skip files smaller than 1024 bytes");
    }

    /**
     * Regression: the dummy fallback audio used to be a 440 Hz sine-wave beep
     * ("Tuten"), which made the master export sound like a continuous test tone
     * when TTS was unavailable. The command must now use {@code anullsrc} (silence)
     * instead of {@code sine=f=440}.
     */
    @Test
    public void buildDummyWavCommand_usesSilence_notSineBeep() {
        File dest = new File(tempDir.toFile(), "dummy_S1.wav");
        List<String> cmd = Video4jEditorService.buildDummyWavCommand("ffmpeg", 2, dest);
        assertTrue(cmd.contains("anullsrc=r=16000:cl=mono"),
                "Dummy fallback audio must use anullsrc (silence); was: " + cmd);
        for (String arg : cmd) {
            assertFalse(arg.contains("sine="),
                    "Dummy fallback audio must not use a sine-wave source; was: " + cmd);
        }
        // Duration is the scene length in seconds.
        assertEquals("2", cmd.get(cmd.size() - 2), "Duration must be the second-to-last argument");
        // Output path is the last argument.
        assertEquals(dest.getAbsolutePath(), cmd.get(cmd.size() - 1),
                "Output path must be the last argument");
    }

    private Object invokePrivate(String name, Class<?>[] argTypes, Object[] args) throws Exception {
        Method m = Video4jEditorService.class.getDeclaredMethod(name, argTypes);
        m.setAccessible(true);
        return m.invoke(service, args);
    }
}