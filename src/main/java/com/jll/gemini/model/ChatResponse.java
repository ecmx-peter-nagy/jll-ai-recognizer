package com.jll.gemini.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(
    String id,
    List<Choice> choices,
    Usage usage
) {

    /**
     * Analyzes the response to determine if it was likely interrupted or incomplete.
     *
     * @param maxTokens The maximum number of tokens that was set for this request.
     * @return A ResponseAnalysis object containing the confidence level, diagnosis, and recommendation.
     */
    public ResponseAnalysis analyze(int maxTokens) {
        if (choices == null || choices.isEmpty() || usage == null) {
            return new ResponseAnalysis(ConfidenceLevel.LOW, "Analysis unavailable: No choice or usage data in response.", null);
        }

        Choice firstChoice = choices.get(0);
        String finishReason = firstChoice.finishReason() != null ? firstChoice.finishReason() : "unknown";
        int completionTokens = usage.completionTokens();
        double usageRatio = (double) completionTokens / maxTokens;

        // HIGH CONFIDENCE: Definitely interrupted
        if ("maxtokens".equalsIgnoreCase(finishReason)) {
            String diagnosis = "The model was cut off because it reached the token limit.";
            int recommendedTokens = Math.max(completionTokens * 2, completionTokens + 1000);
            String recommendation = String.format("Try again with max_tokens set to at least %d for a complete answer.", recommendedTokens);
            return new ResponseAnalysis(ConfidenceLevel.HIGH, diagnosis, recommendation);
        }

        // MEDIUM CONFIDENCE: Possibly rushed
        if ("stop".equalsIgnoreCase(finishReason) && usageRatio >= 0.95) {
            String diagnosis = String.format("The model finished, but used %.0f%% of the available tokens. The response may be rushed or artificially concise.", usageRatio * 100);
            int recommendedTokens = (int) (completionTokens * 1.25);
            String recommendation = String.format("To ensure a more detailed answer, consider trying again with max_tokens set to %d.", recommendedTokens);
            return new ResponseAnalysis(ConfidenceLevel.MEDIUM, diagnosis, recommendation);
        }

        // LOW CONFIDENCE: Likely complete
        String diagnosis = String.format("The model appears to have finished naturally (Finish Reason: %s).", finishReason);
        return new ResponseAnalysis(ConfidenceLevel.LOW, diagnosis, null);
    }
}