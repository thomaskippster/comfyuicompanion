package de.tki.comfyuicompanion.util;

/**
 * Represents the result of a video analysis operation.
 * Contains information about the video's coherence and any identified issues.
 */
public class VideoAnalysisResult {
    private final boolean coherent;
    private final String issueDescription;

    /**
     * Constructs a new {@code VideoAnalysisResult}.
     *
     * @param coherent true if the video is determined to be coherent, false otherwise
     * @param issueDescription a description of any issues found, or an empty string if none
     */
    public VideoAnalysisResult(boolean coherent, String issueDescription) {
        this.coherent = coherent;
        this.issueDescription = issueDescription;
    }

    /**
     * Returns whether the analyzed video is coherent.
     *
     * @return true if coherent, false otherwise
     */
    public boolean isCoherent() { return coherent; }

    /**
     * Returns a description of any issues identified during analysis.
     *
     * @return the issue description
     */
    public String getIssueDescription() { return issueDescription; }
}
