package com.jll.ai.model;

/**
 * A record to hold the results of analyzing a ChatResponse's completeness.
 *
 * @param confidence The confidence level regarding potential interruption.
 * @param diagnosis A human-readable explanation of the findings.
 * @param recommendation An actionable suggestion for the user, if applicable.
 */
public record ResponseAnalysis(
    ConfidenceLevel confidence,
    String diagnosis,
    String recommendation
) {}
