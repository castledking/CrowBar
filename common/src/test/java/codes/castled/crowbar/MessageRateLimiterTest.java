package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRateLimiterTest {
    @Test
    void permitsOneMessagePerCooldownWindow() {
        MessageRateLimiter limiter = new MessageRateLimiter(10_000);

        assertTrue(limiter.tryAcquire(1_000));
        assertFalse(limiter.tryAcquire(10_999));
        assertTrue(limiter.tryAcquire(11_000));

        limiter.reset();
        assertTrue(limiter.tryAcquire(11_001));
    }
}
