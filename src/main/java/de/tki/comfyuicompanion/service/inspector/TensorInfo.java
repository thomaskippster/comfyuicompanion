package de.tki.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents information about a single tensor in a Safetensors file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TensorInfo {
    private String dtype;
    private List<Long> shape;
    private List<Long> data_offsets;

    /**
     * Gets the data type of the tensor.
     * @return The data type.
     */
    public String getDtype() { return dtype; }

    /**
     * Sets the data type of the tensor.
     * @param dtype The data type.
     */
    public void setDtype(String dtype) { this.dtype = dtype; }

    /**
     * Gets the shape of the tensor.
     * @return The shape dimensions.
     */
    public List<Long> getShape() { return shape; }

    /**
     * Sets the shape of the tensor.
     * @param shape The shape dimensions.
     */
    public void setShape(List<Long> shape) { this.shape = shape; }

    /**
     * Gets the data offsets of the tensor.
     * @return The data offsets.
     */
    public List<Long> getData_offsets() { return data_offsets; }

    /**
     * Sets the data offsets of the tensor.
     * @param data_offsets The data offsets.
     */
    public void setData_offsets(List<Long> data_offsets) { this.data_offsets = data_offsets; }
}
