package ai.modelcost.sdk.tracking;

import ai.modelcost.sdk.ModelCostClient;
import ai.modelcost.sdk.model.TrackRequest;
import ai.modelcost.sdk.model.TrackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CostTrackerTest {

    private CostTracker costTracker;
    private ModelCostClient mockClient;

    @BeforeEach
    void setUp() {
        costTracker = new CostTracker(100);
        mockClient = Mockito.mock(ModelCostClient.class);
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
