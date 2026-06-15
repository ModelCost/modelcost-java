package ai.modelcost.sdk;

import ai.modelcost.sdk.exception.ConfigurationException;

/**
 * Immutable configuration for the ModelCost SDK.
 * Use {@link Builder} to construct instances, or {@link #fromEnv()} to read from environment variables.
 */
public final class ModelCostConfig {

    /**
     * Action to take when a budget limit is exceeded.
     */
    public enum BudgetAction {
        ALERT,
        THROTTLE,
        HARD_STOP
    }

    private final String apiKey;
    private final String orgId;
    private final String environment;
    private final String baseUrl;
    private final Double monthlyBudget;
    private final BudgetAction budgetAction;
    private final boolean failOpen;
    private final long flushIntervalMs;
    private final int flushBatchSize;
    private final long syncIntervalMs;
    private final String identifierSecret;

    private ModelCostConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.orgId = builder.orgId;
        this.environment = builder.environment;
        this.baseUrl = builder.baseUrl;
        this.monthlyBudget = builder.monthlyBudget;
        this.budgetAction = builder.budgetAction;
        this.failOpen = builder.failOpen;
        this.flushIntervalMs = builder.flushIntervalMs;
        this.flushBatchSize = builder.flushBatchSize;
        this.syncIntervalMs = builder.syncIntervalMs;
        this.identifierSecret = builder.identifierSecret;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a configuration from environment variables.
     * <p>
     * Reads: MODELCOST_API_KEY, MODELCOST_ORG_ID, MODELCOST_ENV, MODELCOST_BASE_URL.
     * Falls back to system properties if environment variables are not set.
     *
     * @return a ModelCostConfig instance
     * @throws ConfigurationException if required values are missing
     */
    public static ModelCostConfig fromEnv() {
        Builder builder = new Builder();

        String apiKey = resolveEnv("MODELCOST_API_KEY");
        if (apiKey != null) {
            builder.apiKey(apiKey);
        }

        String orgId = resolveEnv("MODELCOST_ORG_ID");
        if (orgId != null) {
            builder.orgId(orgId);
        }

        String env = resolveEnv("MODELCOST_ENV");
        if (env != null) {
            builder.environment(env);
        }

        String baseUrl = resolveEnv("MODELCOST_BASE_URL");
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }

        String identifierSecret = resolveEnv("MODELCOST_IDENTIFIER_SECRET");
        if (identifierSecret != null) {
            builder.identifierSecret(identifierSecret);
        }

        return builder.build();
    }

    private static String resolveEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return (value != null && !value.isBlank()) ? value : null;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Double getMonthlyBudget() {
        return monthlyBudget;
    }

    public BudgetAction getBudgetAction() {
        return budgetAction;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    /**
     * Optional customer-held secret used ONLY locally to pseudonymize identifier refs
     * (customer_id / user_id) via HMAC. Never transmitted to ModelCost.
     */
    public String getIdentifierSecret() {
        return identifierSecret;
    }

    /**
     * Builder for {@link ModelCostConfig}.
     */
    public static class Builder {
        private String apiKey;
        private String orgId;
        private String environment = "production";
        private String baseUrl = "https://api.modelcost.ai";
        private Double monthlyBudget;
        private BudgetAction budgetAction = BudgetAction.ALERT;
        private boolean failOpen = true;
        private long flushIntervalMs = 5000;
        private int flushBatchSize = 100;
        private long syncIntervalMs = 10000;
        private String identifierSecret;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder monthlyBudget(Double monthlyBudget) {
            this.monthlyBudget = monthlyBudget;
            return this;
        }

        public Builder budgetAction(BudgetAction budgetAction) {
            this.budgetAction = budgetAction;
            return this;
        }

        public Builder failOpen(boolean failOpen) {
            this.failOpen = failOpen;
            return this;
        }

        public Builder flushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
            return this;
        }

        public Builder flushBatchSize(int flushBatchSize) {
            this.flushBatchSize = flushBatchSize;
            return this;
        }

        public Builder syncIntervalMs(long syncIntervalMs) {
            this.syncIntervalMs = syncIntervalMs;
            return this;
        }

        /**
         * @deprecated Removed in 0.4.0. Prompt/response content is never transmitted by
         *     any code path, so this flag no longer exists. This setter throws to surface
         *     the change rather than silently no-op. Remove the call.
         */
        @Deprecated
        public Builder contentPrivacy(boolean contentPrivacy) {
            throw new UnsupportedOperationException(
                    "contentPrivacy was removed in modelcost 0.4.0: prompt/response content is "
                            + "never transmitted to ModelCost by any code path. Remove this call.");
        }

        /**
         * Sets the optional customer-held secret used to locally pseudonymize identifier
         * refs (HMAC). Never transmitted. Also readable from MODELCOST_IDENTIFIER_SECRET.
         */
        public Builder identifierSecret(String identifierSecret) {
            this.identifierSecret = identifierSecret;
            return this;
        }

        /**
         * Builds and validates the configuration.
         *
         * @return an immutable ModelCostConfig
         * @throws ConfigurationException if validation fails
         */
        public ModelCostConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new ConfigurationException("API key is required. Set it via builder or MODELCOST_API_KEY environment variable.");
            }
            if (!apiKey.startsWith("mc_")) {
                throw new ConfigurationException("API key must start with 'mc_'. Received: " + apiKey.substring(0, Math.min(apiKey.length(), 6)) + "...");
            }
            if (orgId == null || orgId.isBlank()) {
                throw new ConfigurationException("Organization ID is required. Set it via builder or MODELCOST_ORG_ID environment variable.");
            }
            return new ModelCostConfig(this);
        }
    }
}
