package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelArchitecture;

public interface IModelArchitectureService {
    ModelArchitecture detectArchitecture(String modelName);
    void reloadMappings();
    
    ModelDefaults getDefaultsForModel(String modelName);

    void addProgressListener(BlueprintProgressListener listener);
    void removeProgressListener(BlueprintProgressListener listener);
    int getBlueprintProgressPercent();
    String getBlueprintProgressFileName();
    boolean isBlueprintAnalysisCompleted();
    boolean isAnalyzing();
    void runBlueprintAnalysis();
    java.util.List<java.util.Map<String, Object>> getBlueprintScanResults();

    interface BlueprintProgressListener {
        void onProgress(int percent, String currentFileName, boolean completed);
    }

    public static class ModelDefaults {
        public String vaeName;
        public String clipType;
        public String scheduler;
        public String samplerName;
        public String architecture;

        public ModelDefaults(String vaeName, String clipType, String scheduler, String samplerName, String architecture) {
            this.vaeName = vaeName;
            this.clipType = clipType;
            this.scheduler = scheduler;
            this.samplerName = samplerName;
            this.architecture = architecture;
        }
    }
}
