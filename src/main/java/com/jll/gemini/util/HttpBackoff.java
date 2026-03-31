package com.jll.gemini.util;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpBackoff {

    private static final Logger log = LogManager.getLogger(HttpBackoff.class);

    /**
     * Generic exponential backoff handler.
     *
     * @param totalTimeoutMillis total maximum time to retry (e.g. 180000 ms)
     * @param call               the actual logic
     * @param shouldRetry        predicate to determine if the result requires a
     *                           retry
     * @return result
     * @throws Exception if retries fail or timeout reached
     */
    public static <T> T execute(long totalTimeoutMillis, HttpCall<T> call, Predicate<T> shouldRetry) throws Exception {
        long startTime = System.currentTimeMillis();
        int retryCount = 0;

        while (true) {
            try {
                T result = call.execute();
                if (shouldRetry.test(result)) {
                    String detail = "";
                    if (result instanceof HttpResponse) {
                        HttpResponse<?> res = (HttpResponse<?>) result;
                        detail = String.format("[Status: %d, Body: %s]", res.statusCode(), res.body());
                    } else {
                        detail = String.valueOf(result);
                    }
                    log.warn("Result requires retry (attempt {}). Result: {}", retryCount + 1);
                    log.debug(detail);
                    handleBackoff(startTime, totalTimeoutMillis, retryCount);
                    retryCount++;
                    continue;
                }
                return result;
            } catch (IOException e) {
                log.warn("IOException during HTTP call (attempt {}): {}", retryCount + 1, e.getMessage());
                handleBackoff(startTime, totalTimeoutMillis, retryCount);
                retryCount++;
            } catch (Exception e) {
                throw e;
            }
        }
    }

    /**
     * Legacy method for backward compatibility or simpler use cases where only 429
     * in exception message matters.
     * BUT updated to delegate to the new execute method if possible, or just kept
     * as is?
     * Actually, let's replace it with a call to the new method if possible, but the
     * signature is different.
     * The old signature was: performHttpCallWithBackoff(long totalTimeoutMillis,
     * HttpCall<T> call)
     * It assumed checking for "429" in IOException.
     * Let's keep a compatible method but make it smarter if we can, or just
     * deprecate it.
     * Since we are rewriting the client, we can just change the method name or
     * signature.
     * I'll keep the old name 'performHttpCallWithBackoff' but change signature to
     * match 'execute' above,
     * or just replace the old method entirely.
     * The user asked to "Implement exponential backoff", implying I can change
     * things.
     * I will replace the old method with the new one.
     */
    public static <T> T performHttpCallWithBackoff(long totalTimeoutMillis, HttpCall<T> call, Predicate<T> shouldRetry)
            throws Exception {
        return execute(totalTimeoutMillis, call, shouldRetry);
    }

    private static void handleBackoff(long startTime, long totalTimeoutMillis, int retryCount) throws Exception {
        if (System.currentTimeMillis() - startTime > totalTimeoutMillis) {
            throw new RuntimeException("Total timeout reached (" + totalTimeoutMillis + " ms). Stopping retries.");
        }
        long backoffMillis = (long) Math.pow(2, retryCount) * 1000;
        // Cap backoff at 30 seconds to avoid waiting too long
        if (backoffMillis > 30000) {
            backoffMillis = 30000;
        }

        log.info("Backing off for {} ms...", backoffMillis);
        Thread.sleep(backoffMillis);
    }

    @FunctionalInterface
    public interface HttpCall<T> {
        T execute() throws Exception;
    }
}
