package com.jll.gemini.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileUploadResponse(
    boolean success,
    FileData data,
    String errorMessage
) {}