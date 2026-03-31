package com.jll.gemini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jll.gemini.config.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jll.gemini.util.HttpBackoff;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TokenService {

    private static final Logger log = LogManager.getLogger(TokenService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Lock tokenLock = new ReentrantLock();

    private volatile String accessToken;
    private volatile long tokenExpiryTime;

    // configuration
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String scope;

    public TokenService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();

        this.clientId = AppConfig.get("okta.client.id");
        this.clientSecret = AppConfig.get("okta.client.secret");
        this.tokenUrl = AppConfig.get("okta.token.url");
        this.scope = AppConfig.get("okta.scope");

        log.debug("[TokenService] Initialized (AppConfig) tokenUrl={}, scope={}", tokenUrl, scope);
    }

    public TokenService(HttpClient httpClient, String clientId, String clientSecret, String tokenUrl, String scope) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.scope = scope;

        log.debug("[TokenService] Initialized (GVL-based) tokenUrl={}, scope={}", tokenUrl, scope);
    }

    public String getAccessToken() throws IOException, InterruptedException {
        tokenLock.lock();
        try {
            if (isTokenInvalid()) {
                this.accessToken = fetchNewToken();
            }
            return this.accessToken;
        } finally {
            tokenLock.unlock();
        }
    }

    public void invalidateToken() {
        tokenLock.lock();
        try {
            this.tokenExpiryTime = 0;
            this.accessToken = null;
            log.debug("[TokenService] Access token invalidated.");
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenInvalid() {
        return accessToken == null || System.currentTimeMillis() >= (tokenExpiryTime - 60_000);
    }

    private String fetchNewToken() throws IOException, InterruptedException {
        log.debug("[TokenService] Fetching new OKTA token from {}", tokenUrl);

        String body = "grant_type=client_credentials&scope=" + scope;
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpLogger.logRequest(request, "Token Fetch");
        HttpResponse<String> response;
        try {
            // Retry for up to 30 seconds
            response = HttpBackoff.execute(30000L,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            throw new IOException("Token fetch failed after backoff", e);
        }
        HttpLogger.logResponse(response);

        if (response.statusCode() != 200) {
            log.error("[TokenService] Failed to fetch token: {}", response.body());
            throw new IOException("Failed to fetch OKTA token (HTTP " + response.statusCode() + ")");
        }

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        String newToken = jsonResponse.get("access_token").asText();
        long expiresIn = jsonResponse.get("expires_in").asLong();
        this.tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000);

        log.debug("[TokenService] Token retrieved successfully. Expires in {}s.", expiresIn);
        return newToken;
    }
}
