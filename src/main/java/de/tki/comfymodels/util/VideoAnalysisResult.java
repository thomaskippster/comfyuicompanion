package de.tki.comfymodels.util;

public class VideoAnalysisResult {
    private final boolean coherent;
    private final String issueDescription;

    public VideoAnalysisResult(boolean coherent, String issueDescription) {
        this.coherent = coherent;
        this.issueDescription = issueDescription;
    }

    public boolean isCoherent() { return coherent; }
    public String getIssueDescription() { return issueDescription; }
}
