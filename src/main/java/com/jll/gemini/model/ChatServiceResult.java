package com.jll.gemini.model;

/**
 * A container record to hold the result of a chat service call,
 * including the API response and the duration of the network request.
 */
public record ChatServiceResult(
    ChatResponse response,
    long durationMillis
) {}