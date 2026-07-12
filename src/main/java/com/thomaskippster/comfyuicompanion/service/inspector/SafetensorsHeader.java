package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SafetensorsHeader {

    @JsonProperty("__metadata__")
    private Map<String, String> metadata;

    private Map<String, TensorInfo> tensors = new HashMap<>();

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @JsonAnySetter
    public void addTensor(String name, TensorInfo info) {
        if (!"__metadata__".equals(name) && info != null) {
            tensors.put(name, info);
        }
    }

    public Map<String, TensorInfo> getTensors() {
        return tensors;
    }
}
