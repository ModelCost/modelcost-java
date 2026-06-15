package ai.modelcost.sdk;

import ai.modelcost.sdk.exception.PiiDetectedException;
import ai.modelcost.sdk.governance.GovernanceEnforcer;
import ai.modelcost.sdk.model.TrackRequest;
import ai.modelcost.sdk.pii.PiiScanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enforced egress invariant: nothing but allowlisted, de-identified telemetry may
 * leave the customer boundary. Inspects ACTUAL outbound bytes and the egress DTOs.
 */
class EgressInvariantTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static JsonNode allowlist() throws Exception {
        return MAPPER.readTree(new File("egress-allowlist.json"));
    }

    /** Canonicalize a key so camelCase and snake_case compare equal across SDKs. */
    private static String canon(String key) {
        return key.toLowerCase().replace("_", "");
    }

    private static Set<String> canonForbidden() throws Exception {
        Set<String> out = new HashSet<>();
        allowlist().get("forbiddenKeys").forEach(n -> out.add(canon(n.asText())));
        return out;
    }

    private static Set<String> canonAllowedFor(String path) throws Exception {
        JsonNode eps = allowlist().get("endpoints");
        String key;
        if (path.equals("/api/v1/track")) key = "POST /api/v1/track";
        else if (path.equals("/api/v1/governance/signals")) key = "POST /api/v1/governance/signals";
        else if (path.equals("/api/v1/sessions")) key = "POST /api/v1/sessions";
        else if (path.endsWith("/calls")) key = "POST /api/v1/sessions/{id}/calls";
        else if (path.endsWith("/close")) key = "POST /api/v1/sessions/{id}/close";
        else return null;
        Set<String> out = new HashSet<>();
        eps.get(key).get("allowedKeys").forEach(n -> out.add(canon(n.asText())));
        return out;
    }

    private void assertPayloadSafe(String path, String body) throws Exception {
        assertFalse(path.contains("/governance/scan"), "raw-content scan endpoint must never be called");
        if (body == null || body.isEmpty()) return;
        JsonNode node = MAPPER.readTree(body);

        Set<String> allKeys = new HashSet<>();
        collectKeys(node, allKeys);
        Set<String> forbidden = canonForbidden();
        for (String k : allKeys) {
            assertFalse(forbidden.contains(canon(k)), "forbidden key '" + k + "' reached transport at " + path);
        }

        Set<String> allowed = canonAllowedFor(path);
        assertNotNull(allowed, "request to non-allowlisted endpoint " + path);
        Set<String> topLevel = StreamSupport.stream(
                        ((Iterable<Map.Entry<String, JsonNode>>) node::fields).spliterator(), false)
                .map(e -> canon(e.getKey())).collect(Collectors.toSet());
        topLevel.removeAll(allowed);
        assertTrue(topLevel.isEmpty(), "non-allowlisted field(s) " + topLevel + " reached transport at " + path);
    }

    private void collectKeys(JsonNode node, Set<String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                out.add(e.getKey());
                collectKeys(e.getValue(), out);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, out));
        }
    }

    // ---- Model-level closure -------------------------------------------------

    @Test
    void trackRequestBuilderRejectsMetadata() {
        assertThrows(UnsupportedOperationException.class,
                () -> TrackRequest.builder().metadata(Map.of("phi", "patient data")));
    }

    @Test
    void contentPrivacyBuilderThrows() {
        assertThrows(UnsupportedOperationException.class,
                () -> ModelCostConfig.builder().contentPrivacy(true));
    }

    @Test
    void governanceScanModelsAreGone() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("ai.modelcost.sdk.model.GovernanceScanRequest"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("ai.modelcost.sdk.model.GovernanceScanResponse"));
    }

    @Test
    void clientHasNoScanTextMethod() {
        boolean present = false;
        for (var m : ModelCostClient.class.getMethods()) {
            if (m.getName().equals("scanText")) present = true;
        }
        assertFalse(present, "scanText must not exist on the client");
    }

    @Test
    void trackPayloadOnlyAllowlistedKeys() throws Exception {
        TrackRequest req = TrackRequest.builder()
                .apiKey("mc_test").timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .provider("openai").model("gpt-4o").feature("chatbot").customerId("cust_123")
                .inputTokens(100).outputTokens(50).latencyMs(250L).build();
        String body = MAPPER.writeValueAsString(req);
        assertPayloadSafe("/api/v1/track", body);
    }

    // ---- Transport-level: real outbound bytes -------------------------------

    @Test
    void phiIsBlockedAndNeverTransmitted() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            for (int i = 0; i < 10; i++) {
                server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            }
            ModelCostConfig config = ModelCostConfig.builder()
                    .apiKey("mc_test").orgId("org-test")
                    .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                    .failOpen(true).build();
            ModelCostClient client = new ModelCostClient(config);
            PiiScanner scanner = new PiiScanner();

            String phi = "Patient SSN 123-45-6789 has diabetes; email jane.doe@hospital.org";
            assertThrows(PiiDetectedException.class,
                    () -> GovernanceEnforcer.enforceLocal(phi, config, client, scanner));

            int count = server.getRequestCount();
            assertTrue(count > 0, "expected at least one metadata signal");
            for (int i = 0; i < count; i++) {
                RecordedRequest rec = server.takeRequest();
                String body = rec.getBody().readUtf8();
                assertPayloadSafe(rec.getPath(), body);
                assertFalse(body.contains("123-45-6789"));
                assertFalse(body.contains("diabetes"));
                assertFalse(body.contains("jane.doe@hospital.org"));
            }
            client.close();
        } finally {
            server.shutdown();
        }
    }

    @Test
    void obfuscatedPhiMissedByScannerStillCarriesNoContent() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        try {
            ModelCostConfig config = ModelCostConfig.builder()
                    .apiKey("mc_test").orgId("org-test")
                    .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                    .failOpen(true).build();
            ModelCostClient client = new ModelCostClient(config);
            PiiScanner scanner = new PiiScanner();

            // zero-width-joined SSN the regex will not match
            String obfuscated = "SSN 1​2​3-4​5-6​7​8​9";
            // not blocked (scanner misses it) -> no exception, no outbound request
            GovernanceEnforcer.enforceLocal(obfuscated, config, client, scanner);
            assertEquals(0, server.getRequestCount(), "no content/signal should be transmitted");
            client.close();
        } finally {
            server.shutdown();
        }
    }
}
