package de.tki.comfyuicompanion.domain;

import java.util.Map;
import java.util.List;

/**
 * Repräsentiert ein Start-Profil für ComfyUI.
 *
 * @param id           the unique identifier of the profile
 * @param name         the display name of the profile
 * @param description  the description of the profile
 * @param executeInWsl whether to execute within WSL
 * @param pythonPath   the path to the python executable
 * @param cliArguments the list of CLI arguments
 * @param envVars      the map of environment variables
 */
public record LaunchProfile(
    String id,
    String name,
    String description,
    boolean executeInWsl,
    String pythonPath,
    List<String> cliArguments,
    Map<String, String> envVars
) {}
