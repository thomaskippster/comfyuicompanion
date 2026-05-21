package de.tki.comfymodels.domain;

import java.util.Map;
import java.util.List;

/**
 * Repräsentiert ein Start-Profil für ComfyUI.
 */
public record LaunchProfile(
    String id,
    String name,
    String description,
    boolean executeInWsl,
    String pythonPath, // Pfad zur embedded python.exe
    List<String> cliArguments,
    Map<String, String> envVars
) {}
