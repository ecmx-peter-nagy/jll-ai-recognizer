package com.jll.gemini.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jll.gemini.config.AppConfig;
import com.jll.gemini.model.FileUploadResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.jll.gemini.util.HttpBackoff;

/**
 * Handles file uploads to the Gemini File Service.
 *
 * Both subscriptionKey and customUserId are injected externally (from GVL).
 * No fallback to AppConfig is used — values must be passed by the caller.
 */
public class FileService {

    private static final Logger log = LogManager.getLogger(FileService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final String subscriptionKey;
    private final String customUserId;
    private final String fileServiceUrl;

    public FileService(HttpClient httpClient, TokenService tokenService, String subscriptionKey, String customUserId,
            String fileServiceUrl) {
        if (subscriptionKey == null || subscriptionKey.isBlank())
            throw new IllegalArgumentException("subscriptionKey must not be null or blank");
        if (customUserId == null || customUserId.isBlank())
            throw new IllegalArgumentException("customUserId must not be null or blank");
        if (fileServiceUrl == null || fileServiceUrl.isBlank())
            throw new IllegalArgumentException("fileServiceUrl must not be null or blank");

        this.httpClient = httpClient;
        this.tokenService = tokenService;
        this.subscriptionKey = subscriptionKey;
        this.customUserId = customUserId;
        this.fileServiceUrl = fileServiceUrl;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Uploads a file from disk to the Gemini File Service.
     * Tries to detect MIME type; falls back to application/octet-stream.
     */
    public String uploadFile(Path filePath, String model, long backoffTimeoutMillis)
            throws IOException, InterruptedException {
        String boundary = "Boundary-" + System.currentTimeMillis();
        URI uri = URI.create(this.fileServiceUrl + "?model=" + model);
        long requestTimeout = AppConfig.getLong("http.request.timeout.ms", 60000L);

        String accessToken = this.tokenService.getAccessToken();

        String fileName = filePath.getFileName().toString();
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null || mimeType.isBlank())
            mimeType = "application/octet-stream";

        byte[] fileBytes = Files.readAllBytes(filePath);
        HttpRequest.BodyPublisher bodyPublisher = ofMimeMultipartData(fileBytes, fileName, mimeType, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(requestTimeout))
                .header("Content-Type", "multipart/form-data;boundary=" + boundary)
                .header("Authorization", "Bearer " + accessToken)
                .header("Subscription-Key", subscriptionKey)
                .header("jll-request-id", UUID.randomUUID().toString())
                .header("custom-user-id", customUserId)
                .POST(bodyPublisher)
                .build();

        HttpLogger.logRequest(request, "File Upload");
        HttpLogger.logRequest(request, "File Upload");
        HttpResponse<String> response;
        try {
            response = HttpBackoff.execute(backoffTimeoutMillis,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            throw new IOException("File upload failed after backoff", e);
        }
        HttpLogger.logResponse(response);

        if (response.statusCode() != 200) {
            log.error("[FileService] File upload failed with HTTP {} - {}", response.statusCode(), response.body());
            throw new IOException("File upload failed: " + response.statusCode() + " - " + response.body());
        }

        FileUploadResponse uploadResponse = objectMapper.readValue(response.body(), FileUploadResponse.class);
        if (!uploadResponse.success() || uploadResponse.data() == null || uploadResponse.data().id() == null) {
            log.error("[FileService] File upload unsuccessful: {}", uploadResponse.errorMessage());
            throw new IOException("File upload unsuccessful: " + uploadResponse.errorMessage());
        }

        log.debug("[FileService] File uploaded successfully, fileId={}", uploadResponse.data().id());
        return uploadResponse.data().id();
    }

    /**
     * Uploads a file directly from a byte array (PDF-compatible legacy signature).
     * Keeps backward compatibility by defaulting MIME type to application/pdf.
     */
    public String uploadFile(byte[] fileBytes, String fileName, String model, long backoffTimeoutMillis)
            throws IOException, InterruptedException {
        return uploadFile(fileBytes, fileName, "application/pdf", model, backoffTimeoutMillis);
    }

    /**
     * Uploads a file directly from a byte array with explicit MIME type.
     * Use this for non-PDF formats (DOCX, XLSX, PPTX, PNG/JPG, TXT, EML, etc.).
     */
    public String uploadFile(byte[] fileBytes, String fileName, String mimeType, String model,
            long backoffTimeoutMillis)
            throws IOException, InterruptedException {

        String boundary = "Boundary-" + System.currentTimeMillis();
        URI uri = URI.create(this.fileServiceUrl + "?model=" + model);
        long requestTimeout = AppConfig.getLong("http.request.timeout.ms", 60000L);

        String accessToken = this.tokenService.getAccessToken();

        if (mimeType == null || mimeType.isBlank())
            mimeType = "application/octet-stream";
        if (fileName == null || fileName.isBlank())
            fileName = "upload.bin";

        HttpRequest.BodyPublisher bodyPublisher = ofMimeMultipartData(fileBytes, fileName, mimeType, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(requestTimeout))
                .header("Content-Type", "multipart/form-data;boundary=" + boundary)
                .header("Authorization", "Bearer " + accessToken)
                .header("Subscription-Key", subscriptionKey)
                .header("jll-request-id", UUID.randomUUID().toString())
                .header("custom-user-id", customUserId)
                .POST(bodyPublisher)
                .build();

        HttpLogger.logRequest(request, "File Upload");
        HttpLogger.logRequest(request, "File Upload");
        HttpResponse<String> response;
        try {
            response = HttpBackoff.execute(backoffTimeoutMillis,
                    () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()),
                    r -> r.statusCode() == 429 || r.statusCode() >= 500);
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            throw new IOException("File upload failed after backoff", e);
        }
        HttpLogger.logResponse(response);

        if (response.statusCode() != 200) {
            log.error("[FileService] File upload failed with HTTP {} - {}", response.statusCode(), response.body());
            throw new IOException("File upload failed: " + response.statusCode() + " - " + response.body());
        }

        FileUploadResponse uploadResponse = objectMapper.readValue(response.body(), FileUploadResponse.class);
        if (!uploadResponse.success() || uploadResponse.data() == null || uploadResponse.data().id() == null) {
            log.error("[FileService] File upload unsuccessful: {}", uploadResponse.errorMessage());
            throw new IOException("File upload unsuccessful: " + uploadResponse.errorMessage());
        }

        log.debug("[FileService] File uploaded successfully, fileId={}", uploadResponse.data().id());
        return uploadResponse.data().id();
    }

    /**
     * Helper to build multipart body from in-memory bytes with filename + MIME.
     */
    private HttpRequest.BodyPublisher ofMimeMultipartData(byte[] data, String fileName, String mimeType,
            String boundary) {
        String head = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";

        byte[] headBytes = head.getBytes();
        byte[] tailBytes = tail.getBytes();

        byte[] bodyBytes = new byte[headBytes.length + data.length + tailBytes.length];
        System.arraycopy(headBytes, 0, bodyBytes, 0, headBytes.length);
        System.arraycopy(data, 0, bodyBytes, headBytes.length, data.length);
        System.arraycopy(tailBytes, 0, bodyBytes, headBytes.length + data.length, tailBytes.length);

        return HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
    }
}
