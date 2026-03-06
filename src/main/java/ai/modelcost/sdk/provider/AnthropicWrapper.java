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
 * Wraps Anthropic client instances using Java dynamic proxies to intercept API calls
 * and extract usage information (input_tokens/output_tokens from response.usage).
 */
public class AnthropicWrapper implements ProviderWrapper {

    private static final Logger logger = Logger.getLogger(AnthropicWrapper.class.getName());

    private final ModelCostConfig config;
    private final ModelCostClient mcClient;
    private final PiiScanner piiScanner;

    public AnthropicWrapper() {
        this(null, null, null);
    }

    public AnthropicWrapper(ModelCostConfig config, ModelCostClient mcClient, PiiScanner piiScanner) {
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
            logger.log(Level.WARNING, "Anthropic client class {0} does not implement any interfaces; " +
                    "returning unwrapped client.", client.getClass().getName());
            return client;
        }

        InvocationHandler handler = new AnthropicInvocationHandler(client, config, mcClient, piiScanner);
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

            int inputTokens = 0;
            int outputTokens = 0;

            try {
                Method getInputTokens = usage.getClass().getMethod("getInputTokens");
                Object inputObj = getInputTokens.invoke(usage);
                inputTokens = ((Number) inputObj).intValue();
            } catch (NoSuchMethodException e) {
                try {
                    Method inputTokensMethod = usage.getClass().getMethod("inputTokens");
                    Object inputObj = inputTokensMethod.invoke(usage);
                    inputTokens = ((Number) inputObj).intValue();
                } catch (NoSuchMethodException ignored) {
                    logger.log(Level.FINE, "Could not find input tokens method on Anthropic usage object");
                }
            }

            try {
                Method getOutputTokens = usage.getClass().getMethod("getOutputTokens");
                Object outputObj = getOutputTokens.invoke(usage);
                outputTokens = ((Number) outputObj).intValue();
            } catch (NoSuchMethodException e) {
                try {
                    Method outputTokensMethod = usage.getClass().getMethod("outputTokens");
                    Object outputObj = outputTokensMethod.invoke(usage);
                    outputTokens = ((Number) outputObj).intValue();
                } catch (NoSuchMethodException ignored) {
                    logger.log(Level.FINE, "Could not find output tokens method on Anthropic usage object");
                }
            }

            return new UsageInfo(inputTokens, outputTokens);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to extract usage from Anthropic response: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    private static class AnthropicInvocationHandler implements InvocationHandler {
        private final Object delegate;
        private final ModelCostConfig config;
        private final ModelCostClient mcClient;
        private final PiiScanner piiScanner;

        AnthropicInvocationHandler(Object delegate, ModelCostConfig config,
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
                logger.log(Level.FINE, "Anthropic call {0} completed in {1}ms",
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
                PiiScanner.FullScanResult fullResult = piiScanner.fullScan(text, null);
                if (fullResult.isDetected()) {
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
