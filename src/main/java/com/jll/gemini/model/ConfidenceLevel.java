package com.jll.gemini.model;

/**
 * Represents the confidence level regarding the completeness of an LLM response.
 */
public enum ConfidenceLevel {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String displayName;

    ConfidenceLevel(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}