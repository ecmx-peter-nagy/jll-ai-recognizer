package com.jll.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jll.ai.config.AppConfig;
import com.jll.ai.model.ChatRequest;
import com.jll.ai.model.ChatResponse;
import com.jll.ai.model.ChatServiceResult;
import com.jll.ai.util.HttpBackoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final HttpClient httpClient;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public ChatService(HttpClient httpClient, TokenService tokenService) {
        this.httpClient = httpClient;
        this.tokenService = tokenService;
        this.objectMapper = new ObjectMapper();
    }

    public ChatServiceResult sendChatRequest(ChatRequest chatRequest) throws IOException, InterruptedException {
        String accessToken = tokenService.getAccessToken();
        return sendRequestWithRetry(chatRequest, accessToken);
    }

    private ChatServiceResult sendRequestWithRetry(ChatRequest chatRequest, String accessToken)
            throws IOException, InterruptedException {
        URI uri = URI.create(AppConfig.get("chat.service.url"));
        long requestTimeout = AppConfig.getLong("http.request.timeout.ms");

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(chatRequest);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing chat request to JSON", e);
            throw new IOException("Failed to serialize chat request.", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(requestTimeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("Subscription-Key", AppConfig.get("subscription.key"))
                .header("jll-request-id", UUID.randomUUID().toString())
                .header("custom-user-id", AppConfig.get("custom.user.id"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Chat Request Body: {}", requestBody);
        HttpLogger.logRequest(request, "JSON Body (see debug log for full content)");

        // --- TIMING LOGIC START ---
        long startTime = System.currentTimeMillis();
        HttpResponse<String> response;
        try {
            response = HttpBackoff.execute(60000L,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            throw new IOException("Chat request failed after backoff", e);
        }
        long durationMillis = System.currentTimeMillis() - startTime;
        // --- TIMING LOGIC END ---

        HttpLogger.logResponse(response);

        if (response.statusCode() == 401) {
            logger.warn("Received 401 Unauthorized. Invalidating token and retrying once.");
            tokenService.invalidateToken();
            String newAccessToken = tokenService.getAccessToken();

            HttpRequest newRequest = HttpRequest.newBuilder(request, (name, value) -> true)
                    .setHeader("Authorization", "Bearer " + newAccessToken)
                    .build();

            HttpLogger.logRequest(newRequest, "JSON Body on Retry (see debug log for full content)");

            // --- RETRY TIMING LOGIC START ---
            long retryStartTime = System.currentTimeMillis();
            try {
                response = HttpBackoff.execute(60000L,
                        () -> httpClient.send(newRequest, HttpResponse.BodyHandlers.ofString()),
                        r -> r.statusCode() == 429 || r.statusCode() >= 500);
            } catch (Exception e) {
                if (e instanceof IOException)
                    throw (IOException) e;
                if (e instanceof InterruptedException)
                    throw (InterruptedException) e;
                throw new IOException("Chat request retry failed after backoff", e);
            }
            durationMillis = System.currentTimeMillis() - retryStartTime; // Overwrite with the duration of the
                                                                          // successful retry
            // --- RETRY TIMING LOGIC END ---

            HttpLogger.logResponse(response);
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Request failed with status code: " + response.statusCode() + " - " + response.body());
        }

        ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);

        return new ChatServiceResult(chatResponse, durationMillis);
    }
}
