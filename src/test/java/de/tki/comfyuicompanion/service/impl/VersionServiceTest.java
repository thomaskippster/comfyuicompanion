package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private ProcessTracker processTracker;

    @InjectMocks
    private VersionService versionService;

    @Test
    void shouldReturnDefaultPythonVersion_forRemote() {
        String version = versionService.getRemotePythonVersion();
        assertThat(version).isEqualTo("3.12.9");
    }

    @Test
    void shouldReturnSystemPython_whenPythonPathNullOrEmpty() {
        assertThat(versionService.getInstalledPythonVersion(null)).isEqualTo("System Python");
        assertThat(versionService.getInstalledPythonVersion("")).isEqualTo("System Python");
        assertThat(versionService.getInstalledPythonVersion("python")).isEqualTo("System Python");
    }

    @Test
    void shouldReturnUnknown_whenComfyPathNullOrEmpty() {
        assertThat(versionService.getInstalledComfyVersion(null)).isEqualTo("Unknown");
        assertThat(versionService.getInstalledComfyVersion("")).isEqualTo("Unknown");
    }
}
