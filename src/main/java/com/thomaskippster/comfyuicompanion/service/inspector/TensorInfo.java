package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TensorInfo {
    private String dtype;
    private List<Long> shape;
    private List<Long> data_offsets;

    public String getDtype() {
        return dtype;
    }

    public void setDtype(String dtype) {
        this.dtype = dtype;
    }

    public List<Long> getShape() {
        return shape;
    }

    public void setShape(List<Long> shape) {
        this.shape = shape;
    }

    public List<Long> getData_offsets() {
        return data_offsets;
    }

    public void setData_offsets(List<Long> data_offsets) {
        this.data_offsets = data_offsets;
    }
}
