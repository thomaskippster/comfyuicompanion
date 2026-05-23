package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class HardwareMonitorService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HardwareMonitorThread");
        t.setDaemon(true);
        return t;
    });

    private double cpuLoad = 0.0;
    private long ramUsed = 0L;
    private long ramTotal = 0L;
    
    private String gpuName = "N/A";
    private int gpuUtilization = 0;
    private long vramUsed = 0L;
    private long vramTotal = 0L;
    private boolean hasNvidia = false;

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

    private void queryNvidiaGpu() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits");
            } else {
                pb = new ProcessBuilder("nvidia-smi", "--query-gpu=name,utilization.gpu,memory.used,memory.total", "--format=csv,noheader,nounits");
            }
            
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        this.gpuName = parts[0].trim();
                        this.gpuUtilization = Integer.parseInt(parts[1].trim());
                        this.vramUsed = Long.parseLong(parts[2].trim()) * 1024L * 1024L; // in MB to Bytes
                        this.vramTotal = Long.parseLong(parts[3].trim()) * 1024L * 1024L;
                        this.hasNvidia = true;
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        this.hasNvidia = false;
        this.gpuName = "N/A";
    }
}
