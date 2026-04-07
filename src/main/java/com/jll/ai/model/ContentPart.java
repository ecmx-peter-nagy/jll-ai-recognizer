package com.jll.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentPart(
    String type,
    String text,
    FilePart file
) {}
