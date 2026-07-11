package de.tki.comfymodels.exception;

/**
 * Exception für Konfigurations- und Settings-Fehler.
 * Wird verwendet bei:
 * - Vault-Entsperrungsfehlern
 * - Pfad-Konfigurationsproblemen
 * - Invaliden Einstellungen
 */
public class ConfigException extends CompanionException {
    
    private final String configKey;
    
    public ConfigException(String message) {
        super(message);
        this.configKey = null;
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
        this.configKey = null;
    }
    
    public ConfigException(String message, String configKey) {
        super(message, "configKey=" + configKey);
        this.configKey = configKey;
    }
    
    public ConfigException(String message, String configKey, Throwable cause) {
        super(message, "configKey=" + configKey, cause);
        this.configKey = configKey;
    }
    
    public String getConfigKey() {
        return configKey;
    }
}
