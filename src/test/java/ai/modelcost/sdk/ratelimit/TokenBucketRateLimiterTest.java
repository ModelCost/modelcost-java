package ai.modelcost.sdk.ratelimit;

import ai.modelcost.sdk.exception.RateLimitedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void tryAcquireAllowsWithinBurst() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, 5);

        // Should be able to acquire up to burst (5) tokens immediately
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "Token " + (i + 1) + " should be available");
        }
    }

    @Test
    void tryAcquireRejectsWhenExhausted() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, 3);

        // Exhaust all 3 tokens
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());

        // Next attempt should fail
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void tokensRefillOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100.0, 5); // 100 tokens/sec

        // Exhaust all tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());

        // Wait for refill (100 tokens/sec = 1 token per 10ms, wait 150ms for safety)
        Thread.sleep(150);

        // Should have tokens available again
        assertTrue(limiter.tryAcquire(), "Tokens should have refilled after waiting");
    }

    @Test
    void acquireWithTimeoutThrowsWhenExhausted() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(0.1, 1); // very slow refill

        // Exhaust the single token
        assertTrue(limiter.tryAcquire());

        // Acquire with short timeout should throw
        assertThrows(RateLimitedException.class, () -> limiter.acquire(50));
    }

    @Test
    void getAvailableTokensReflectsUsage() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10.0, 10);

        double initial = limiter.getAvailableTokens();
        assertTrue(initial >= 9.5); // approximately 10, allowing for tiny elapsed time refill rounding

        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        double remaining = limiter.getAvailableTokens();
        assertTrue(remaining < 8.0, "Should have fewer tokens after acquiring 3");
    }
}
