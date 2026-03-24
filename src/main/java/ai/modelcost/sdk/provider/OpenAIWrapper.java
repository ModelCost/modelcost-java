package ai.modelcost.sdk.provider;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.ModelCostConfig;
import ai.modelcost.sdk.exception.PiiDetectedException;
import ai.modelcost.sdk.model.DetectedViolation;
import ai.modelcost.sdk.model.GovernanceScanRequest;
import ai.modelcost.sdk.model.GovernanceScanResponse;
import ai.modelcost.sdk.model.GovernanceSignalRequest;
import ai.modelcost.sdk.pii.PiiScanner;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps OpenAI client instances using Java dynamic proxies to intercept API calls
 * and extract usage information for cost tracking.
 */
public class OpenAIWrapper implements ProviderWrapper {

    private static final Logger logger = Logger.getLogger(OpenAIWrapper.class.getName());

    private final ModelCostConfig config;
    private final ModelCostClient mcClient;
    private final PiiScanner piiScanner;

    public OpenAIWrapper() {
        this(null, null, null);
    }

    public OpenAIWrapper(ModelCostConfig config, ModelCostClient mcClient, PiiScanner piiScanner) {
        this.config = config;
        this.mcClient = mcClient;
        this.piiScanner = piiScanner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T wrap(T client) {
        if (client == null) {
            throw new IllegalArgumentException("Client must not be null");
        }

        Class<?>[] interfaces = client.getClass().getInterfaces();
        if (interfaces.length == 0) {
            logger.log(Level.WARNING, "OpenAI client class {0} does not implement any interfaces; " +
                    "dynamic proxy wrapping requires at least one interface. Returning unwrapped client.",
                    client.getClass().getName());
            return client;
        }

        InvocationHandler handler = new OpenAIInvocationHandler(client, config, mcClient, piiScanner);
        return (T) Proxy.newProxyInstance(
                client.getClass().getClassLoader(),
                interfaces,
                handler
        );
    }

    @Override
    public UsageInfo extractUsage(Object response) {
        if (response == null) {
            return null;
        }
        try {
            Method getUsage = response.getClass().getMethod("getUsage");
            Object usage = getUsage.invoke(response);
            if (usage == null) {
                return null;
            }

            int promptTokens = 0;
            int outputTokens = 0;
            int cachedTokens = 0;

            try {
                Method getPromptTokens = usage.getClass().getMethod("getPromptTokens");
                Object promptTokensObj = getPromptTokens.invoke(usage);
                promptTokens = ((Number) promptTokensObj).intValue();
            } catch (NoSuchMethodException e) {
                try {
                    Method getTotalTokens = usage.getClass().getMethod("promptTokens");
                    Object promptTokensObj = getTotalTokens.invoke(usage);
                    promptTokens = ((Number) promptTokensObj).intValue();
                } catch (NoSuchMethodException ignored) {
                    logger.log(Level.FINE, "Could not find prompt tokens method on usage object");
                }
            }

            try {
                Method getCompletionTokens = usage.getClass().getMethod("getCompletionTokens");
                Object completionTokensObj = getCompletionTokens.invoke(usage);
                outputTokens = ((Number) completionTokensObj).intValue();
            } catch (NoSuchMethodException e) {
                try {
                    Method completionTokens = usage.getClass().getMethod("completionTokens");
                    Object completionTokensObj = completionTokens.invoke(usage);
                    outputTokens = ((Number) completionTokensObj).intValue();
                } catch (NoSuchMethodException ignored) {
                    logger.log(Level.FINE, "Could not find completion tokens method on usage object");
                }
            }

            // Extract cached tokens from prompt_tokens_details.cached_tokens
            try {
                Method getPromptTokensDetails = usage.getClass().getMethod("getPromptTokensDetails");
                Object details = getPromptTokensDetails.invoke(usage);
                if (details != null) {
                    try {
                        Method getCachedTokens = details.getClass().getMethod("getCachedTokens");
                        Object cachedObj = getCachedTokens.invoke(details);
                        if (cachedObj != null) {
                            cachedTokens = ((Number) cachedObj).intValue();
                        }
                    } catch (NoSuchMethodException e2) {
                        try {
                            Method cachedTokensMethod = details.getClass().getMethod("cachedTokens");
                            Object cachedObj = cachedTokensMethod.invoke(details);
                            if (cachedObj != null) {
                                cachedTokens = ((Number) cachedObj).intValue();
                            }
                        } catch (NoSuchMethodException ignored) {
                            logger.log(Level.FINE, "Could not find cached tokens method on prompt tokens details");
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
                logger.log(Level.FINE, "Could not find getPromptTokensDetails method on usage object");
            }

            // Subtract cached from prompt_tokens so inputTokens reflects only non-cached input
            int inputTokens = Math.max(0, promptTokens - cachedTokens);
            return new UsageInfo(inputTokens, outputTokens, 0, cachedTokens);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to extract usage from OpenAI response: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    /**
     * Invocation handler that intercepts OpenAI client method calls.
     */
    private static class OpenAIInvocationHandler implements InvocationHandler {
        private final Object delegate;
        private final ModelCostConfig config;
        private final ModelCostClient mcClient;
        private final PiiScanner piiScanner;

        OpenAIInvocationHandler(Object delegate, ModelCostConfig config,
                                ModelCostClient mcClient, PiiScanner piiScanner) {
            this.delegate = delegate;
            this.config = config;
            this.mcClient = mcClient;
            this.piiScanner = piiScanner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Run governance scan before API calls that accept text
            if (piiScanner != null && config != null && args != null) {
                for (Object arg : args) {
                    if (arg instanceof String text && !text.isEmpty()) {
                        enforceGovernance(text);
                    }
                }
            }

            long startTime = System.nanoTime();
            try {
                Object result = method.invoke(delegate, args);
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.log(Level.FINE, "OpenAI call {0} completed in {1}ms",
                        new Object[]{method.getName(), elapsedMs});
                return result;
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        private void enforceGovernance(String text) {
            PiiScanner.PiiResult scanResult = piiScanner.scan(text);
            if (!scanResult.isDetected()) {
                return;
            }

            if (config.isContentPrivacy()) {
                // Metadata-only mode: full local classification, never send raw content
                PiiScanner.FullScanResult fullResult = piiScanner.fullScan(text, null);
                if (fullResult.isDetected()) {
                    // Report signals (fire-and-forget)
                    for (PiiScanner.GovernanceViolation v : fullResult.getViolations()) {
                        try {
                            mcClient.reportSignal(GovernanceSignalRequest.builder()
                                    .organizationId(config.getOrgId())
                                    .violationType(v.getCategory())
                                    .violationSubtype(v.getType())
                                    .severity(v.getSeverity())
                                    .environment(config.getEnvironment())
                                    .actionTaken("block")
                                    .wasAllowed(false)
                                    .detectedAt(Instant.now().toString())
                                    .source("metadata_only")
                                    .violationCount(1)
                                    .build());
                        } catch (Exception ignored) {
                            // fire-and-forget
                        }
                    }

                    List<DetectedViolation> entities = fullResult.getViolations().stream()
                            .map(v -> new DetectedViolation(v.getCategory(), v.getType(),
                                    v.getSeverity(), v.getStart(), v.getEnd()))
                            .toList();

                    throw new PiiDetectedException(
                            "Sensitive content detected and blocked locally (metadata-only mode)",
                            entities,
                            piiScanner.redact(text));
                }
            } else {
                // Standard mode: check governance policy server-side
                GovernanceScanResponse govResult = mcClient.scanText(
                        GovernanceScanRequest.builder()
                                .orgId(config.getOrgId())
                                .text(text)
                                .environment(config.getEnvironment())
                                .build());
                if (govResult != null && !govResult.isAllowed()) {
                    throw new PiiDetectedException(
                            "PII detected in request and blocked by policy",
                            govResult.getViolations(),
                            govResult.getRedactedText() != null
                                    ? govResult.getRedactedText()
                                    : piiScanner.redact(text));
                }
            }
        }
    }
}
