package de.tki.comfyuicompanion.domain;

/**
 * Represents a workflow template loaded from the file system.
 * It holds the raw JSON content and metadata about the template.
 */
public class ComfyTemplate {
    private String name;
    private String filename;
    private String content;
    private String filePath;

    /** Default constructor. */
    public ComfyTemplate() {}

    /**
     * Constructs a ComfyTemplate with the given properties.
     *
     * @param name     the logical name of the template
     * @param filename the file name of the template
     * @param content  the raw JSON content of the workflow
     * @param filePath the absolute path to the template file
     */
    public ComfyTemplate(String name, String filename, String content, String filePath) {
        this.name = name;
        this.filename = filename;
        this.content = content;
        this.filePath = filePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
