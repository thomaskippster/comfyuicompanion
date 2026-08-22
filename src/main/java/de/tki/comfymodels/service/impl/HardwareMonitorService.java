package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class HardwareMonitorService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HardwareMonitorThread");
        t.setDaemon(true);
        return t;
    });

    private volatile double cpuLoad = 0.0;
    private volatile long ramUsed = 0L;
    private volatile long ramTotal = 0L;
    
    private volatile String gpuName = "N/A";
    private volatile int gpuUtilization = 0;
    private volatile long vramUsed = 0L;
    private volatile long vramTotal = 0L;
    private volatile boolean hasNvidia = false;

    public static class HardwareStats {
        public double cpuLoad;
        public long ramUsed;
        public long ramTotal;
        public String gpuName;
        public int gpuUtilization;
        public long vramUsed;
        public long vramTotal;
        public boolean hasNvidia;
    }


    @Autowired(required = false)
    private ProcessTracker processTracker;
    public void start(Consumer<HardwareStats> callback) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                queryCpuAndRam();
                queryNvidiaGpu();
                
                HardwareStats stats = new HardwareStats();
                stats.cpuLoad = this.cpuLoad;
                stats.ramUsed = this.ramUsed;
                stats.ramTotal = this.ramTotal;
                stats.gpuName = this.gpuName;
                stats.gpuUtilization = this.gpuUtilization;
                stats.vramUsed = this.vramUsed;
                stats.vramTotal = this.vramTotal;
                stats.hasNvidia = this.hasNvidia;
                
                callback.accept(stats);
            } catch (Exception e) {
                // Suppress background errors
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void queryCpuAndRam() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunBean = (com.sun.management.OperatingSystemMXBean) osBean;
                this.cpuLoad = sunBean.getCpuLoad() * 100.0;
                long total = sunBean.getTotalPhysicalMemorySize();
                long free = sunBean.getFreePhysicalMemorySize();
                this.ramTotal = total;
                this.ramUsed = total - free;
            }
        } catch (Exception ignored) {}
    }

    private int nvidiaFailCount = 0;
    private long lastNvidiaCheck = 0;

    private void queryNvidiaGpu() {
        long now = System.currentTimeMillis();
        if (nvidiaFailCount >= 5 && (now - lastNvidiaCheck < 30000)) {
            queryFallbackGpu();
            return;
        }
        lastNvidiaCheck = now;

        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=name,utilization.gpu,memory.used,memory.total", "--format=csv,noheader,nounits");
            
            Process p = processTracker != null ? processTracker.start(pb) : pb.start();
            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        String[] parts = line.split(",");
                        if (parts.length >= 4) {
                            this.gpuName = parts[0].trim();
                            this.gpuUtilization = Integer.parseInt(parts[1].trim());
                            this.vramUsed = Long.parseLong(parts[2].trim()) * 1024L * 1024L; // in MB to Bytes
                            this.vramTotal = Long.parseLong(parts[3].trim()) * 1024L * 1024L;
                            this.hasNvidia = true;
                            this.nvidiaFailCount = 0;
                            return;
                        }
                    }
                }
                p.waitFor(2, TimeUnit.SECONDS);
            } finally {
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        } catch (Exception ignored) {}
        this.hasNvidia = false;
        this.nvidiaFailCount++;
        queryFallbackGpu();
    }

    private boolean fallbackInitialized = false;

    private void queryFallbackGpu() {
        if (fallbackInitialized) return;
        fallbackInitialized = true;
        
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Query primary GPU name using PowerShell (fast one-time call)
                ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", 
                    "Get-CimInstance Win32_VideoController | Sort-Object AdapterRAM -Descending | Select-Object -First 1 -ExpandProperty Name");
                Process p = processTracker.start(pb);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        this.gpuName = line.trim();
                    }
                }
                p.waitFor(3, TimeUnit.SECONDS);

                // Query AdapterRAM
                pb = new ProcessBuilder("powershell", "-Command", 
                    "Get-CimInstance Win32_VideoController | Sort-Object AdapterRAM -Descending | Select-Object -First 1 -ExpandProperty AdapterRAM");
                p = processTracker.start(pb);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        try {
                            this.vramTotal = Long.parseLong(line.trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                p.waitFor(3, TimeUnit.SECONDS);
            } else if (os.contains("mac")) {
                // Mac display info
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", "system_profiler SPDisplaysDataType | grep 'Chipset Model'");
                Process p = processTracker.start(pb);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null && line.contains(":")) {
                        this.gpuName = line.substring(line.indexOf(":") + 1).trim();
                    }
                }
                p.waitFor(3, TimeUnit.SECONDS);
            } else {
                // Linux fallback via lspci
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", "lspci | grep -i -E 'vga|3d'");
                Process p = processTracker.start(pb);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null && line.contains(":")) {
                        this.gpuName = line.substring(line.lastIndexOf(":") + 1).trim();
                    }
                }
                p.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Returns the most recently observed total VRAM in bytes. Returns 0 if
     * the monitor has not yet queried the GPU or no NVIDIA GPU is present.
     * Triggers an immediate refresh on first call so callers that need a
     * synchronous answer at startup do not have to wait for the periodic
     * scheduler to fire.
     */
    public synchronized long getVramBytes() {
        if (vramTotal == 0 && !hasNvidia) {
            // One-shot synchronous refresh so the very first caller gets a
            // real value instead of zero. The scheduler also runs this in
            // the background, but we cannot assume it has fired yet.
            queryNvidiaGpu();
        }
        return vramTotal;
    }

    /** Returns the GPU model name as last reported by the monitor. */
    public synchronized String getGpuName() {
        return gpuName;
    }}
