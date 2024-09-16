package org.AItestAutomationEngineer.CodeToReview;

public class CodeChange {
    private final String filePath;
    private final String changedCode;
    private final int newLineNumber; // Line number in the new file after changes

    public CodeChange(String filePath, String changedCode, int newLineNumber) {
        this.filePath = filePath;
        this.changedCode = changedCode;
        this.newLineNumber = newLineNumber;
    }

    // Getters
    public String getFilePath() {
        return filePath;
    }

    public String getChangedCode() {
        return changedCode;
    }

    public int getNewLineNumber() {
        return newLineNumber;
    }
}