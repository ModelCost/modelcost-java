package ai.modelcost.sdk.tracking;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.model.TrackRequest;
import ai.modelcost.sdk.model.TrackResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CostTrackerTest {

    private CostTracker costTracker;
    private ModelCostClient mockClient;
    private MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        costTracker = new CostTracker(100);
        mockClient = Mockito.mock(ModelCostClient.class);

        // Seed pricing from a mock API so cost calculation tests work
        mockServer = new MockWebServer();
        mockServer.start();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode models = root.putArray("models");

        ObjectNode gpt4o = mapper.createObjectNode();
        gpt4o.put("model", "gpt-4o");
        gpt4o.put("provider", "openai");
        gpt4o.put("input_cost_per_1k", 0.005);
        gpt4o.put("output_cost_per_1k", 0.015);
        models.add(gpt4o);

        ObjectNode claudeOpus = mapper.createObjectNode();
        claudeOpus.put("model", "claude-opus-4");
        claudeOpus.put("provider", "anthropic");
        claudeOpus.put("input_cost_per_1k", 0.015);
        claudeOpus.put("output_cost_per_1k", 0.075);
        models.add(claudeOpus);

        mockServer.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(root))
                .addHeader("Content-Type", "application/json"));

        CostTracker.syncPricingFromApi(mockServer.url("/").toString(), "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }

    @Test
    void calculateCostForGpt4o() {
        // gpt-4o pricing: input=$0.005/1k, output=$0.015/1k
        // 150 input tokens = (150/1000) * 0.005 = 0.00075
        // 50 output tokens = (50/1000) * 0.015 = 0.00075
        // Total = 0.0015
        double cost = CostTracker.calculateCost("gpt-4o", 150, 50);
        assertEquals(0.0015, cost, 0.0000001);
    }

    @Test
    void bufferFlushSendsCorrectNumberOfRecords() {
        when(mockClient.track(any(TrackRequest.class))).thenReturn(new TrackResponse("ok"));

        // Buffer 5 records
        for (int i = 0; i < 5; i++) {
            costTracker.record(TrackRequest.builder()
                    .apiKey("mc_test")
                    .provider("openai")
                    .model("gpt-4o")
                    .inputTokens(100)
                    .outputTokens(50)
                    .build());
        }

        assertEquals(5, costTracker.getBufferSize());

        costTracker.flush(mockClient);

        assertEquals(0, costTracker.getBufferSize());
        verify(mockClient, times(5)).track(any(TrackRequest.class));
    }

    @Test
    void unknownModelReturnsZeroCost() {
        double cost = CostTracker.calculateCost("unknown-model-xyz", 1000, 500);
        assertEquals(0.0, cost);
    }

    @Test
    void calculateCostForClaudeOpus4() {
        // claude-opus-4 pricing: input=$0.015/1k, output=$0.075/1k
        // 1000 input = (1000/1000) * 0.015 = 0.015
        // 500 output = (500/1000) * 0.075 = 0.0375
        // Total = 0.0525
        double cost = CostTracker.calculateCost("claude-opus-4", 1000, 500);
        assertEquals(0.0525, cost, 0.0000001);
    }

    @Test
    void emptyBufferFlushDoesNothing() {
        costTracker.flush(mockClient);
        verify(mockClient, never()).track(any());
    }
}
