package ai.modelcost.sdk;

import ai.modelcost.sdk.model.TrackRequest;
import ai.modelcost.sdk.model.TrackResponse;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ModelCostClientTest {

    private MockWebServer mockServer;
    private ModelCostClient client;
    private ModelCostConfig config;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        config = ModelCostConfig.builder()
                .apiKey("mc_test_key")
                .orgId("org-test")
                .baseUrl(mockServer.url("/").toString().replaceAll("/$", ""))
                .failOpen(true)
                .build();

        client = new ModelCostClient(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    @Test
    void trackRequestSendsCorrectJson() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"ok\"}"));

        Instant now = Instant.parse("2025-01-15T10:30:00Z");
        TrackRequest request = TrackRequest.builder()
                .apiKey("mc_test_key")
                .timestamp(now)
                .provider("openai")
                .model("gpt-4o")
                .feature("chat")
                .customerId("cust-123")
                .inputTokens(100)
                .outputTokens(50)
                .latencyMs(250L)
                .build();

        TrackResponse response = client.track(request);

        assertNotNull(response);
        assertEquals("ok", response.getStatus());

        RecordedRequest recorded = mockServer.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/v1/track", recorded.getPath());
        assertEquals("mc_test_key", recorded.getHeader("X-API-Key"));

        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"api_key\":\"mc_test_key\""));
        assertTrue(body.contains("\"provider\":\"openai\""));
        assertTrue(body.contains("\"model\":\"gpt-4o\""));
        assertTrue(body.contains("\"input_tokens\":100"));
        assertTrue(body.contains("\"output_tokens\":50"));
        assertTrue(body.contains("\"customer_id\":\"cust-123\""));
        assertTrue(body.contains("\"latency_ms\":250"));
    }

    @Test
    void circuitBreakerOpensAfterThreeFailures() throws Exception {
        // Enqueue 3 failures
        for (int i = 0; i < 3; i++) {
            mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"internal\"}"));
        }

        // Enqueue a success for the 4th call (should not reach server if circuit is open)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"ok\"}"));

        TrackRequest request = TrackRequest.builder()
                .apiKey("mc_test_key")
                .provider("openai")
                .model("gpt-4o")
                .inputTokens(10)
                .outputTokens(5)
                .build();

        // First 3 calls should return null (fail-open mode)
        for (int i = 0; i < 3; i++) {
            TrackResponse resp = client.track(request);
            assertNull(resp, "Call " + (i + 1) + " should return null in fail-open mode");
        }

        // 4th call: circuit breaker is open, should return null without hitting server
        TrackResponse resp = client.track(request);
        assertNull(resp, "Circuit breaker should be open, returning null");

        // Only 3 requests should have reached the server
        assertEquals(3, mockServer.getRequestCount());
    }

    @Test
    void failOpenModeLogsButDoesNotThrow() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"));

        TrackRequest request = TrackRequest.builder()
                .apiKey("mc_test_key")
                .provider("openai")
                .model("gpt-4o")
                .inputTokens(10)
                .outputTokens(5)
                .build();

        // Should not throw, should return null in fail-open mode
        TrackResponse response = client.track(request);
        assertNull(response);
    }
}
