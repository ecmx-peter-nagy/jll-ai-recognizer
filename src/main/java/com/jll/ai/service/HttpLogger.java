package com.jll.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpLogger {

    private static final Logger logger = LoggerFactory.getLogger(HttpLogger.class);

    /**
     * Logs an HttpRequest's metadata, printing only a custom message for the body.
     * This is suitable for all request types, especially binary or multi-part
     * bodies.
     * The service calling this method is responsible for logging the actual body
     * content if desired.
     */
    public static void logRequest(HttpRequest request, String bodyMessage) {
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("--> ").append(request.method()).append(" ").append(request.uri()).append("\n");
            request.headers().map()
                    .forEach((k, v) -> sb.append(k).append(": ").append(String.join(", ", v)).append("\n"));
            sb.append("Body: [").append(bodyMessage).append("]");

            logger.debug(sb.toString());
        }
    }

    /**
     * Logs an HttpResponse.
     */
    public static void logResponse(HttpResponse<String> response) {
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("<-- ").append(response.statusCode()).append(" ").append(response.uri()).append("\n");
            response.headers().map()
                    .forEach((k, v) -> sb.append(k).append(": ").append(String.join(", ", v)).append("\n"));
            sb.append("\n").append(response.body());

            logger.debug(sb.toString());
        }
    }
}
