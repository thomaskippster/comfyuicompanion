package de.tki.comfymodels.util;

/**
 * Zentrale Konstanten für die Anwendung.
 * Vermeidet Magic Numbers und Strings im gesamten Codebase.
 */
public final class ConfigConstants {
    
    private ConfigConstants() {
        // Utility class, keine Instanziierung
    }
    
    // ========================================
    // NETWORK & PORTS
    // ========================================
    
    /** Default ComfyUI HTTP Port */
    public static final int DEFAULT_COMFYUI_PORT = 8188;
    
    /** Default ComfyUI URL */
    public static final String DEFAULT_COMFYUI_URL = "http://127.0.0.1:" + DEFAULT_COMFYUI_PORT;
    
    /** Default ComfyUI Host */
    public static final String DEFAULT_COMFYUI_HOST = "127.0.0.1";
    
    /** Alternative Ports für Auto-Discovery */
    public static final int[] ALTERNATIVE_PORTS = {
        DEFAULT_COMFYUI_PORT, 8189, 8190, 8000, 3000, 8080
    };
    
    /** Port-Range für erweiterte Auto-Discovery */
    public static final int DISCOVERY_RANGE_START = 8191;
    public static final int DISCOVERY_RANGE_END = 8199;
    
    /** Legacy Port (wird migriert) */
    public static final int LEGACY_PORT = 8000;
    
    // ========================================
    // TIMEOUTS (in Millisekunden)
    // ========================================
    
    /** HTTP Connection Timeout */
    public static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    
    /** HTTP Read Timeout für normale Requests */
    public static final int HTTP_READ_TIMEOUT_MS = 30_000;
    
    /** HTTP Timeout für kurze Checks (Port-Scan) */
    public static final int HTTP_SHORT_TIMEOUT_MS = 300;
    
    /** Download Timeout pro Segment */
    public static final int DOWNLOAD_SEGMENT_TIMEOUT_MS = 60_000;
    
    /** Hardware Monitor Interval */
    public static final int HARDWARE_MONITOR_INTERVAL_SEC = 2;
    
    /** Retry Delay nach Fehler */
    public static final int RETRY_DELAY_MS = 50;
    
    /** Thread Sleep für Polling */
    public static final int POLL_SLEEP_MS = 3000;
    
    // ========================================
    // DOWNLOAD & STORAGE
    // ========================================
    
    /** Buffer für Disk-Space Check (10 GB) */
    public static final long DISK_SPACE_BUFFER_BYTES = 10L * 1024 * 1024 * 1024;
    
    /** Minimum File Size für Multi-Segment Download (100 MB) */
    public static final long MULTI_SEGMENT_THRESHOLD_BYTES = 100L * 1024 * 1024;
    
    /** Default Buffer Size für File I/O (64 KB) */
    public static final int IO_BUFFER_SIZE_BYTES = 65_536;
    
    /** Download Status Update Interval (800ms) */
    public static final int DOWNLOAD_STATUS_UPDATE_MS = 800;
    
    /** Default Anzahl paralleler Downloads */
    public static final int DEFAULT_PARALLEL_DOWNLOADS = 3;
    
    /** Default Segment-Anzahl pro File */
    public static final int DEFAULT_SEGMENTS_PER_FILE = 1;
    
    // ========================================
    // FILE EXTENSIONS & FORMATS
    // ========================================
    
    /** Safetensors File Extension */
    public static final String SAFETENSORS_EXTENSION = ".safetensors";
    
    /** ComfyUI Companion Download Extension */
    public static final String DOWNLOAD_PART_EXTENSION = ".cmfd";
    
    /** Safetensors LFS Stub Threshold (5 KB) */
    public static final long SAFETENSORS_STUB_THRESHOLD_BYTES = 5_000;
    
    // ========================================
    // UI & HARDWARE
    // ========================================
    
    /** VRAM Auto-Detection Failure Threshold */
    public static final long VRAM_UNKNOWN_BYTES = 0L;
    
    /** CPU Load Normalization Factor (0.0 - 1.0 → 0 - 100%) */
    public static final double CPU_LOAD_MULTIPLIER = 100.0;
    
    // ========================================
    // FILE PATHS & NAMES
    // ========================================
    
    /** App Settings Filename */
    public static final String SETTINGS_FILE = "app_settings.json";
    
    /** Vault Filename */
    public static final String VAULT_FILE = "settings.vault";
    
    /** Workflow Input Filename */
    public static final String WORKFLOW_INPUT_FILE = "input.json";
    
    /** Remote Workflow Filename */
    public static final String REMOTE_WORKFLOW_FILE = "remote_workflow.json";
    
    /** AppData Directory Name */
    public static final String APPDATA_DIR = ".comfyui-companion";
    
    // ========================================
    // HTTP STATUS CODES
    // ========================================
    
    public static final int HTTP_OK = 200;
    public static final int HTTP_PARTIAL_CONTENT = 206;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    
    // ========================================
    // API ENDPOINTS
    // ========================================
    
    /** ComfyUI Refresh Models Endpoint */
    public static final String COMFYUI_REFRESH_ENDPOINT = "/cmfc/refresh-models";
    
    /** ComfyUI System Stats Endpoint */
    public static final String COMFYUI_SYSTEM_STATS_ENDPOINT = "/system_stats";
    
    // ========================================
    // USER AGENTS
    // ========================================
    
    /** Default User Agent für HTTP Requests */
    public static final String USER_AGENT = "Mozilla/5.0";
    
    // ============================================
    // Additional Non-OS-Dependent Constants
    // ============================================
    
    /** Main entry point file for ComfyUI */
    public static final String COMFYUI_MAIN_FILE = "main.py";
    
    /** Default tools directory for ffmpeg */
    public static final String TOOLS_DIR = "tools";
    
    /** FFmpeg binary name (without extension) */
    public static final String FFMPEG_BINARY = "ffmpeg";
    
    /** FFmpeg binary name with Windows extension */
    public static final String FFMPEG_BINARY_WIN = "ffmpeg.exe";
    
    /** ComfyUI configuration file */
    public static final String COMFYUI_CONFIG_FILE = "config.json";
    
    /** Web UI directory for ComfyUI */
    public static final String WEB_DIR = "web";
    
    /** ComfyUI virtual environment directory */
    public static final String VENV_DIR = ".venv";
    
    /** ComfyUI models directory */
    public static final String COMFYUI_MODELS_DIR = "models";
    
    /** ComfyUI output directory */
    public static final String COMFYUI_OUTPUT_DIR = "output";
    
    /** ComfyUI input directory */
    public static final String COMFYUI_INPUT_DIR = "input";
}
