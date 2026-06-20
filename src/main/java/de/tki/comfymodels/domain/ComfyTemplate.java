package de.tki.comfymodels.domain;

public class ComfyTemplate {
    private String name;
    private String filename;
    private String content;
    private String filePath;

    public ComfyTemplate() {}

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
