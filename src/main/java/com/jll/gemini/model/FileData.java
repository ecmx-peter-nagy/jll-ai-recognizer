package com.jll.gemini.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileData(
    String id,
    String fileName,
    @JsonProperty("userADGuid") String userAdGuid,
    int originalWordCount,
    boolean truncated,
    int truncatedWordCount,
    boolean isImage,
    String modelFamily
) {}