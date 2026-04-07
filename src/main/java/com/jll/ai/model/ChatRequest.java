package com.jll.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatRequest(
    String model,
    List<Message> messages,
    @JsonProperty("max_tokens") int maxTokens,
    double temperature,
    @JsonProperty("contextFileIds") List<String> contextFileIds
) {}
