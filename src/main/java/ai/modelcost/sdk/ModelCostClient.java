package ai.modelcost.sdk;

import ai.modelcost.sdk.exception.ModelCostApiException;
import ai.modelcost.sdk.exception.ModelCostException;
import ai.modelcost.sdk.model.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for communicating with the ModelCost API.
 * Includes circuit breaker logic to avoid cascading failures.
 */
public class ModelCostClient {

    private static final Logger logger = Logger.getLogger(ModelCostClient.class.getName());
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final int FAILURE_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 60_000;

    private final ModelCostConfig config;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public ModelCostClient(ModelCostConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    // Visible for testing
    ModelCostClient(ModelCostConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = httpClient;
    }

    /**
     * Tracks an AI model usage event.
     *
     * @param request the tracking request
     * @return the API response
     */
    public TrackResponse track(TrackRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/track", json);
            return executeRequest(httpRequest, TrackResponse.class);
        } catch (IOException e) {
            throw handleException("Failed to serialize track request", e);
        }
    }

    /**
     * Performs a pre-flight budget check.
     *
     * @param orgId         the organization ID
     * @param feature       the feature name
     * @param estimatedCost the estimated cost in USD
     * @return the budget check response
     */
    public BudgetCheckResponse checkBudget(String orgId, String feature, double estimatedCost) {
        HttpUrl url = HttpUrl.parse(config.getBaseUrl() + "/api/v1/budgets/check")
                .newBuilder()
                .addQueryParameter("org_id", orgId)
                .addQueryParameter("feature", feature)
                .addQueryParameter("estimated_cost", String.valueOf(estimatedCost))
                .build();

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", JSON_MEDIA_TYPE))
                .addHeader("X-API-Key", config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        return executeRequest(httpRequest, BudgetCheckResponse.class);
    }

    /**
     * Scans text for PII and governance violations.
     *
     * @param request the governance scan request
     * @return the scan response
     */
    public GovernanceScanResponse scanText(GovernanceScanRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/governance/scan", json);
            return executeRequest(httpRequest, GovernanceScanResponse.class);
        } catch (IOException e) {
            throw handleException("Failed to serialize governance scan request", e);
        }
    }

    /**
     * Reports a governance signal (metadata-only mode). Fire-and-forget.
     *
     * @param request the signal request
     */
    public void reportSignal(GovernanceSignalRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/governance/signals", json);
            executeRequest(httpRequest, Object.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "reportSignal() failed (fire-and-forget): {0}", e.getMessage());
        }
    }

    /**
     * Gets the current budget status for an organization.
     *
     * @param orgId the organization ID
     * @return the budget status response
     */
    public BudgetStatusResponse getBudgetStatus(String orgId) {
        HttpUrl url = HttpUrl.parse(config.getBaseUrl() + "/api/v1/budgets/status")
                .newBuilder()
                .addQueryParameter("org_id", orgId)
                .build();

        Request httpRequest = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-API-Key", config.getApiKey())
                .build();

        return executeRequest(httpRequest, BudgetStatusResponse.class);
    }

    /**
     * Creates a new agent session on the server.
     *
     * @param request the create session request
     * @return the session creation response
     */
    public CreateSessionResponse createSession(CreateSessionRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/sessions", json);
            return executeRequest(httpRequest, CreateSessionResponse.class);
        } catch (IOException e) {
            throw handleException("Failed to serialize create session request", e);
        }
    }

    /**
     * Records a call within an agent session. Fire-and-forget.
     *
     * @param sessionId the server-side session ID
     * @param request   the record call request
     */
    public void recordSessionCall(String sessionId, RecordSessionCallRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/sessions/" + sessionId + "/calls", json);
            executeRequest(httpRequest, Object.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "recordSessionCall() failed (fire-and-forget): {0}", e.getMessage());
        }
    }

    /**
     * Closes an agent session on the server. Fire-and-forget.
     *
     * @param sessionId the server-side session ID
     * @param request   the close session request
     */
    public void closeSession(String sessionId, CloseSessionRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = newPostRequest("/api/v1/sessions/" + sessionId + "/close", json);
            executeRequest(httpRequest, Object.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "closeSession() failed (fire-and-forget): {0}", e.getMessage());
        }
    }

    /**
     * Shuts down the HTTP client dispatcher and connection pool.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // Package-private for testing
    ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private <T> T executeRequest(Request request, Class<T> responseType) {
        // Circuit breaker: check if circuit is open
        long now = System.currentTimeMillis();
        if (consecutiveFailures.get() >= FAILURE_THRESHOLD && now < circuitOpenUntil.get()) {
            if (config.isFailOpen()) {
                logger.log(Level.WARNING, "Circuit breaker open, fail-open mode: returning null for {0}", request.url());
                return null;
            }
            throw new ModelCostException("Circuit breaker is open. API requests are temporarily disabled.");
        }

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                // Only count 5xx (server) errors toward the circuit breaker.
                // 4xx errors are client-side issues and should not trip the breaker.
                if (response.code() >= 500) {
                    consecutiveFailures.incrementAndGet();
                    if (consecutiveFailures.get() >= FAILURE_THRESHOLD) {
                        circuitOpenUntil.set(System.currentTimeMillis() + COOLDOWN_MS);
                        logger.log(Level.WARNING, "Circuit breaker opened after {0} consecutive failures", FAILURE_THRESHOLD);
                    }
                }

                ErrorResponse errorResponse = null;
                try {
                    errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
                } catch (IOException ignored) {
                    // Could not parse error response
                }

                String errorMessage = errorResponse != null && errorResponse.getMessage() != null
                        ? errorResponse.getMessage()
                        : "API request failed with status " + response.code();
                String errorCode = errorResponse != null && errorResponse.getError() != null
                        ? errorResponse.getError()
                        : "UNKNOWN";

                if (config.isFailOpen()) {
                    logger.log(Level.WARNING, "API error (fail-open): {0}", errorMessage);
                    return null;
                }

                throw new ModelCostApiException(errorMessage, response.code(), errorCode);
            }

            // Success: reset circuit breaker
            consecutiveFailures.set(0);
            return objectMapper.readValue(responseBody, responseType);
        } catch (ModelCostException e) {
            throw e;
        } catch (IOException e) {
            consecutiveFailures.incrementAndGet();
            if (consecutiveFailures.get() >= FAILURE_THRESHOLD) {
                circuitOpenUntil.set(System.currentTimeMillis() + COOLDOWN_MS);
                logger.log(Level.WARNING, "Circuit breaker opened after {0} consecutive failures", FAILURE_THRESHOLD);
            }
            if (config.isFailOpen()) {
                logger.log(Level.WARNING, "Network error (fail-open): {0}", e.getMessage());
                return null;
            }
            throw new ModelCostException("Network error communicating with ModelCost API", e);
        }
    }

    private Request newPostRequest(String path, String json) {
        return new Request.Builder()
                .url(config.getBaseUrl() + path)
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .addHeader("X-API-Key", config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
    }

    private ModelCostException handleException(String message, Exception cause) {
        if (config.isFailOpen()) {
            logger.log(Level.WARNING, message + " (fail-open): " + cause.getMessage());
            return null;
        }
        return new ModelCostException(message, cause);
    }
}
