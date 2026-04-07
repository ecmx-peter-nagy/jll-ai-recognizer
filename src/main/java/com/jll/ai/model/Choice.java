package com.jll.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty; // Add this import

@JsonIgnoreProperties(ignoreUnknown = true)
public record Choice(
    Message message,
    @JsonProperty("finish_reason") String finishReason // Add this field
) {}
