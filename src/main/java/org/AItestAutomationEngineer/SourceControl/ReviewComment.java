package org.AItestAutomationEngineer.SourceControl;

public class ReviewComment {
    private final String filePath;
    private final String comment;
    private final int lineNumber;

    public ReviewComment(String filePath, String comment, int lineNumber) {
        this.filePath = filePath;
        this.comment = comment;
        this.lineNumber = lineNumber;
    }

    // Getters
    public String getFilePath() {
        return filePath;
    }

    public String getComment() {
        return comment;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}