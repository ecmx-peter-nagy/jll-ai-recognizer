package com.jll.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.jll.gemini.config.AppConfig;
import com.jll.gemini.model.ChatRequest;
import com.jll.gemini.model.Message;
import com.jll.gemini.service.FileService;
import com.jll.gemini.service.TokenService;
import com.jll.gemini.util.HttpBackoff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class GeminiClient {

    private static final Logger log = LogManager.getLogger(GeminiClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final FileService fileService;

    private final String chatServiceUrl;
    private final String model;
    private final String subscriptionKey;
    private final String customUserId;
    private final double temperature;
    private final int maxTokens;

    public GeminiClient(String model, String subscriptionKey, int maxTokens, double temperature,
            String clientId, String clientSecret, String customUserId, String chatServiceUrl, String fileServiceUrl,
            String oktaTokenUrl, String oktaScope) {

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.chatServiceUrl = chatServiceUrl;
        this.model = model;
        this.subscriptionKey = subscriptionKey;
        this.customUserId = customUserId;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        this.tokenService = new TokenService(httpClient, clientId, clientSecret, oktaTokenUrl, oktaScope);

        this.fileService = new FileService(httpClient, tokenService, subscriptionKey, customUserId, fileServiceUrl);

        log.debug("[GeminiClient] Initialized with model={}, tokens={}, temp={}, user={}, chatUrl={}",
                model, maxTokens, temperature, customUserId, chatServiceUrl);
    }

    public GeminiClient(String model, String subscriptionKey, int maxTokens, double temperature,
            String clientId, String clientSecret) {
        this(model, subscriptionKey, maxTokens, temperature, clientId, clientSecret, "unknown",
                AppConfig.get("chat.service.url"), AppConfig.get("file.service.url"),
                AppConfig.get("okta.token.url"), AppConfig.get("okta.scope"));
    }

    public String analyzeFinancialDocument(byte[] pdfBytes, String prompt, double tempOverride,
            long backoffTimeoutMillis)
            throws IOException, InterruptedException {

        double temp = tempOverride > 0 ? tempOverride : this.temperature;
        String accessToken = tokenService.getAccessToken();

        String fileId = fileService.uploadFile(pdfBytes, "document.pdf", model, backoffTimeoutMillis);
        log.debug("[GeminiClient] File uploaded successfully, fileId={}", fileId);

        Message systemMsg = new Message("system", prompt);
        Message userMsg = new Message("user", "Please analyze the uploaded document.");

        ChatRequest chatRequest = new ChatRequest(
                model,
                List.of(systemMsg, userMsg),
                maxTokens,
                temp,
                List.of(fileId));

        String jsonBody = objectMapper.writeValueAsString(chatRequest);
        // log.debug("[GeminiClient] ChatRequest JSON: {}", jsonBody);

        URI uri = URI.create(chatServiceUrl);
        long timeout = AppConfig.getLong("http.request.timeout.ms", 60000L);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("Subscription-Key", subscriptionKey)
                .header("jll-request-id", UUID.randomUUID().toString())
                .header("custom-user-id", customUserId)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response;

        try {
            response = HttpBackoff.performHttpCallWithBackoff(backoffTimeoutMillis,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            log.error("[GeminiClient] HTTP call failed after retries: {}", e.getMessage(), e);
            if (e instanceof IOException)
                throw (IOException) e;
            throw new IOException("HTTP call failed after backoff: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - start;

        log.debug("[GeminiClient] Chat request completed in {} ms", duration);
        log.debug("[GeminiClient] HTTP status={}, response={}", response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            log.error("[GeminiClient] Gemini returned error {}: {}", response.statusCode(), response.body());
            throw new IOException("Gemini returned HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();

        try {
            JsonNode root = objectMapper.readTree(body);
            String finishReason = root.path("choices").get(0).path("finish_reason").asText(null);
            if (finishReason != null)
                log.debug("[GeminiClient] finish_reason={}", finishReason);
        } catch (Exception ignore) {
        }

        return body;
    }

    public String analyzeDocument(byte[] bytes,
            String fileName,
            String mimeType,
            String prompt,
            double tempOverride,
            long backoffTimeoutMillis)
            throws IOException, InterruptedException {

        double temp = tempOverride > 0 ? tempOverride : this.temperature;
        String accessToken = tokenService.getAccessToken();

        String fileId = fileService.uploadFile(bytes, fileName, mimeType, model, backoffTimeoutMillis);
        log.debug("[GeminiClient] File uploaded successfully, fileId={}, name={}, mime={}", fileId, fileName, mimeType);

        Message systemMsg = new Message("system", prompt);
        Message userMsg = new Message("user", "Please analyze the uploaded document.");

        ChatRequest chatRequest = new ChatRequest(
                model,
                List.of(systemMsg, userMsg),
                maxTokens,
                temp,
                List.of(fileId));

        String jsonBody = objectMapper.writeValueAsString(chatRequest);
        log.debug("[GeminiClient] ChatRequest JSON: {}", jsonBody);

        URI uri = URI.create(chatServiceUrl);
        long timeout = AppConfig.getLong("http.request.timeout.ms", 60000L);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("Subscription-Key", subscriptionKey)
                .header("jll-request-id", UUID.randomUUID().toString())
                .header("custom-user-id", customUserId)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        log.debug("[GeminiClient] Sending HTTP POST to {}", uri);
        long start = System.currentTimeMillis();
        HttpResponse<String> response;

        try {
            response = HttpBackoff.performHttpCallWithBackoff(backoffTimeoutMillis,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            log.error("[GeminiClient] HTTP call failed after retries: {}", e.getMessage(), e);
            if (e instanceof IOException)
                throw (IOException) e;
            throw new IOException("HTTP call failed after backoff: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - start;

        log.debug("[GeminiClient] Chat request completed in {} ms", duration);
        log.debug("[GeminiClient] HTTP status={}, response={}", response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            log.error("[GeminiClient] Gemini returned error {}: {}", response.statusCode(), response.body());
            throw new IOException("Gemini returned HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();

        try {
            JsonNode root = objectMapper.readTree(body);
            String finishReason = root.path("choices").get(0).path("finish_reason").asText(null);
            if (finishReason != null)
                log.debug("[GeminiClient] finish_reason={}", finishReason);
        } catch (Exception ignore) {
        }

        return body;
    }
}
