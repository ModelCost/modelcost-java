package ai.modelcost.sdk.tracking;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.model.TrackRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks AI model usage costs with local buffering and periodic flushing.
 * Pricing table is fetched from the API on init and refreshed periodically
 * via {@link #syncPricingFromApi}.
 */
public class CostTracker {

    private static final Logger logger = Logger.getLogger(CostTracker.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mutable pricing map. Populated by {@link #syncPricingFromApi} on init
     * and refreshed periodically. Uses volatile reference swap for thread safety.
     */
    private static volatile Map<String, ModelPricing> MODEL_PRICING = new ConcurrentHashMap<>();

    /**
     * Fetch the latest pricing table from the server and update the local cache.
     * Called on SDK init and periodically by the background sync timer.
     *
     * @param baseUrl the ModelCost API base URL
     * @param apiKey  the API key for authentication
     */
    private static final HttpClient PRICING_HTTP_CLIENT = HttpClient.newHttpClient();

    public static void syncPricingFromApi(String baseUrl, String apiKey) {
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/api/v1/pricing/models";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-Key", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = PRICING_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.log(Level.FINE, "Pricing sync returned HTTP {0}", response.statusCode());
                return;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode modelsNode = root.get("models");
            if (modelsNode == null || !modelsNode.isArray()) {
                return;
            }

            Map<String, ModelPricing> updated = new HashMap<>();
            for (JsonNode entry : modelsNode) {
                String model = entry.get("model").asText();
                updated.put(model, new ModelPricing(
                        entry.get("provider").asText(),
                        entry.get("input_cost_per_1k").asDouble(),
                        entry.get("output_cost_per_1k").asDouble()
                ));
            }

            if (!updated.isEmpty()) {
                MODEL_PRICING = new ConcurrentHashMap<>(updated);
                logger.log(Level.INFO, "Synced pricing table: {0} models", updated.size());
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to sync pricing from API, using local table", e);
        }
    }

    /**
     * Pricing information for a single model.
     */
    public static class ModelPricing {
        private final String provider;
        private final double inputCostPer1k;
        private final double outputCostPer1k;

        public ModelPricing(String provider, double inputCostPer1k, double outputCostPer1k) {
            this.provider = provider;
            this.inputCostPer1k = inputCostPer1k;
            this.outputCostPer1k = outputCostPer1k;
        }

        public String getProvider() {
            return provider;
        }

        public double getInputCostPer1k() {
            return inputCostPer1k;
        }

        public double getOutputCostPer1k() {
            return outputCostPer1k;
        }
    }

    /**
     * Calculates the estimated cost for a model invocation.
     *
     * @param model        the model name
     * @param inputTokens  number of input tokens
     * @param outputTokens number of output tokens
     * @return the estimated cost in USD, or 0.0 if the model is unknown
     */
    public static double calculateCost(String model, int inputTokens, int outputTokens) {
        ModelPricing pricing = MODEL_PRICING.get(model);
        if (pricing == null) {
            logger.log(Level.WARNING, "Unknown model for cost calculation: {0}", model);
            return 0.0;
        }
        double inputCost = (inputTokens / 1000.0) * pricing.inputCostPer1k;
        double outputCost = (outputTokens / 1000.0) * pricing.outputCostPer1k;
        return inputCost + outputCost;
    }

    /**
     * Returns the pricing for a given model, or null if unknown.
     */
    public static ModelPricing getPricing(String model) {
        return MODEL_PRICING.get(model);
    }

    private final List<TrackRequest> buffer = Collections.synchronizedList(new ArrayList<>());
    private final int batchSize;
    private ScheduledExecutorService executor;

    public CostTracker(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Records a tracking request into the buffer.
     * Triggers an immediate flush if the buffer reaches the configured batch size.
     *
     * @param request the tracking request
     */
    public void record(TrackRequest request) {
        buffer.add(request);
        if (buffer.size() >= batchSize) {
            logger.log(Level.FINE, "Buffer reached batch size {0}, triggering flush", batchSize);
        }
    }

    /**
     * Flushes the buffer by sending all pending records to the API.
     *
     * @param client the ModelCost API client
     */
    public void flush(ModelCostClient client) {
        List<TrackRequest> toSend;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            toSend = new ArrayList<>(buffer);
            buffer.clear();
        }

        logger.log(Level.FINE, "Flushing {0} tracking records", toSend.size());

        for (TrackRequest request : toSend) {
            try {
                client.track(request);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send tracking record: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Starts a periodic auto-flush task.
     *
     * @param client     the ModelCost API client
     * @param intervalMs the flush interval in milliseconds
     */
    public void startAutoFlush(ModelCostClient client, long intervalMs) {
        if (executor != null && !executor.isShutdown()) {
            logger.log(Level.WARNING, "Auto-flush already running");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modelcost-flush");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(
                () -> {
                    try {
                        flush(client);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Auto-flush error: " + e.getMessage(), e);
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        logger.log(Level.FINE, "Auto-flush started with interval {0}ms", intervalMs);
    }

    private static final long PRICING_SYNC_INTERVAL_MS = 300_000; // 5 minutes
    private ScheduledExecutorService pricingSyncExecutor;

    /**
     * Starts periodic pricing sync from the server.
     *
     * @param baseUrl the ModelCost API base URL
     * @param apiKey  the API key for authentication
     */
    public void startPricingSync(String baseUrl, String apiKey) {
        if (pricingSyncExecutor != null && !pricingSyncExecutor.isShutdown()) {
            return;
        }
        pricingSyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modelcost-pricing-sync");
            t.setDaemon(true);
            return t;
        });
        pricingSyncExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        syncPricingFromApi(baseUrl, apiKey);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Pricing sync error: " + e.getMessage(), e);
                    }
                },
                PRICING_SYNC_INTERVAL_MS,
                PRICING_SYNC_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        logger.log(Level.FINE, "Pricing sync started with interval {0}ms", PRICING_SYNC_INTERVAL_MS);
    }

    /**
     * Shuts down the auto-flush executor and flushes any remaining records.
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (pricingSyncExecutor != null && !pricingSyncExecutor.isShutdown()) {
            pricingSyncExecutor.shutdown();
            try {
                if (!pricingSyncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    pricingSyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                pricingSyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returns the current number of buffered records.
     */
    public int getBufferSize() {
        return buffer.size();
    }
}
