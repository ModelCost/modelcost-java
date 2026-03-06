package ai.modelcost.sdk.provider;

/**
 * Interface for wrapping AI provider clients to intercept calls and track usage.
 */
public interface ProviderWrapper {

    /**
     * Wraps a provider client with usage-tracking instrumentation.
     *
     * @param client the provider client to wrap
     * @param <T>    the type of the provider client
     * @return a wrapped proxy of the client
     */
    <T> T wrap(T client);

    /**
     * Extracts usage information from a provider response object.
     *
     * @param response the provider response
     * @return the extracted usage info, or null if unable to extract
     */
    UsageInfo extractUsage(Object response);

    /**
     * Returns the canonical provider name (e.g., "openai", "anthropic", "google").
     *
     * @return the provider name
     */
    String getProviderName();

    /**
     * Usage information extracted from a provider response.
     */
    record UsageInfo(int inputTokens, int outputTokens) {
    }
}
