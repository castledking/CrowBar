package codes.castled.crowbar;

/** Small, clock-injectable limiter for action-bar messages that can be triggered by key spam. */
public final class MessageRateLimiter {
    private final long cooldownMillis;
    private long nextAllowedAt = Long.MIN_VALUE;

    public MessageRateLimiter(long cooldownMillis) {
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException("cooldownMillis must not be negative");
        }
        this.cooldownMillis = cooldownMillis;
    }

    public synchronized boolean tryAcquire(long nowMillis) {
        if (nowMillis < nextAllowedAt) return false;
        nextAllowedAt = nowMillis + cooldownMillis;
        return true;
    }

    public synchronized void reset() {
        nextAllowedAt = Long.MIN_VALUE;
    }
}
