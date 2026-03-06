package ai.modelcost.sdk.ratelimit;

import ai.modelcost.sdk.exception.RateLimitedException;

/**
 * Token bucket rate limiter for controlling request throughput.
 * Thread-safe via synchronized blocks — eliminates the autoboxing CAS bug
 * that occurs with AtomicReference&lt;Double&gt; (Double.valueOf() never caches,
 * so compareAndSet always fails due to reference inequality).
 */
public class TokenBucketRateLimiter {

    private final double rate;       // tokens per second
    private final int burst;         // max token capacity
    private double tokens;
    private long lastRefillNanos;

    /**
     * Creates a new token bucket rate limiter.
     *
     * @param rate  the refill rate in tokens per second
     * @param burst the maximum number of tokens (burst capacity)
     */
    public TokenBucketRateLimiter(double rate, int burst) {
        this.rate = rate;
        this.burst = burst;
        this.tokens = burst;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Attempts to acquire a single token without blocking.
     *
     * @return true if a token was acquired, false if the bucket is empty
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens < 1.0) {
            return false;
        }
        tokens -= 1.0;
        return true;
    }

    /**
     * Acquires a token, blocking until one is available.
     */
    public void acquire() {
        while (!tryAcquire()) {
            try {
                long sleepMs = (long) (1000.0 / rate);
                if (sleepMs < 1) sleepMs = 1;
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitedException(
                        "Interrupted while waiting for rate limit token",
                        1.0 / rate,
                        "token_bucket"
                );
            }
        }
    }

    /**
     * Acquires a token, blocking up to the specified timeout.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @throws RateLimitedException if the timeout expires before a token is available
     */
    public void acquire(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!tryAcquire()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new RateLimitedException(
                        "Rate limit exceeded, timed out waiting for token",
                        1.0 / rate,
                        "token_bucket"
                );
            }
            try {
                long sleepMs = Math.min((long) (1000.0 / rate), remaining);
                if (sleepMs < 1) sleepMs = 1;
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitedException(
                        "Interrupted while waiting for rate limit token",
                        1.0 / rate,
                        "token_bucket"
                );
            }
        }
    }

    /**
     * Refills tokens based on elapsed time since last refill.
     * Must be called while holding the monitor lock (from synchronized methods).
     */
    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;

        if (elapsedSeconds <= 0) {
            return;
        }

        lastRefillNanos = now;
        tokens = Math.min(burst, tokens + elapsedSeconds * rate);
    }

    /**
     * Returns the current number of available tokens (approximate).
     */
    public synchronized double getAvailableTokens() {
        refill();
        return tokens;
    }
}
