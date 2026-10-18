package codes.castled.crowbar;

/**
 * Raises vanilla HUD elements above the locator labels, an idea borrowed from Player Locator Plus.
 *
 * <p>The label pass reports how high its labels reach with {@link #requestClearance}, and the
 * next frame's {@link #beginFrame} eases the hearts, hunger, experience level and chat towards
 * that height. Vanilla draws the status bars before the locator bar, so running one frame behind
 * is what lets both sides agree on the offset.
 */
public final class HudLift {
    public static final HudLift INSTANCE = new HudLift();

    /** The hearts row starts 39px above the bottom of the screen and is 9px tall. */
    static final int STATUS_BARS_BOTTOM = 30;
    /** Natural frequency of the critically damped spring, in rad/s; settles in about 250ms. */
    private static final float STIFFNESS = 20f;
    private static final float MAX_FRAME_SECONDS = 0.1f;

    private int requested;
    private float target;
    private float current;
    private float velocity;
    private long lastFrameNanos = -1L;

    /**
     * Records a label whose top edge is at {@code labelTop}, so the status bars must clear it.
     */
    public void requestClearance(int labelTop, int screenHeight) {
        int needed = (screenHeight - STATUS_BARS_BOTTOM) - labelTop + 1;
        if (needed > requested) {
            requested = needed;
        }
    }

    /** Advances the animation. Call once per frame, before any lifted element is drawn. */
    public void beginFrame(long nowNanos, boolean enabled) {
        target = enabled ? requested : 0;
        requested = 0;

        float dt = lastFrameNanos < 0 ? 0f : Math.min(MAX_FRAME_SECONDS, (nowNanos - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = nowNanos;
        if (dt <= 0f) return;

        // Closed-form step of a critically damped spring, so it never overshoots into the labels.
        float displacement = current - target;
        float b = velocity + STIFFNESS * displacement;
        float decay = (float) Math.exp(-STIFFNESS * dt);
        float nextDisplacement = (displacement + b * dt) * decay;
        velocity = (b - STIFFNESS * (displacement + b * dt)) * decay;
        current = target + nextDisplacement;

        if (Math.abs(current - target) < 0.05f && Math.abs(velocity) < 0.5f) {
            current = target;
            velocity = 0f;
        }
    }

    /** Pixels to move lifted elements up by this frame. */
    public float offset() {
        return current;
    }
}
