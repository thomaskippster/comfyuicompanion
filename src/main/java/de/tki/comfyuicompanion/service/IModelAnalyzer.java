package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ModelInfo;
import java.util.List;

public interface IModelAnalyzer {
    List<ModelInfo> analyze(String jsonText, String fileName);
}
