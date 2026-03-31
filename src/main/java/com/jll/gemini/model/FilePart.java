package com.jll.gemini.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilePart(
    @JsonProperty("file_data") String fileData,
    String filename,
    @JsonProperty("mime_type") String mimeType
) {}
