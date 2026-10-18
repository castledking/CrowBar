package codes.castled.crowbar;

/** Distance fading for markers, an idea borrowed from Player Locator Plus. */
public final class MarkerFade {
    /**
     * Distance assumed for bearing-only waypoints. Vanilla stops sending a range past 332 blocks,
     * so that is the nearest such a target can be.
     */
    static final double UNKNOWN_DISTANCE = 332.0D;

    private MarkerFade() {
    }

    /** Opacity in {@code 0..255} for a marker {@code distance} blocks away; negative means unknown. */
    public static int alpha(CrowBarConfig config, double distance) {
        if (!config.fadeDistantMarkers) return 255;
        double d = distance < 0 ? UNKNOWN_DISTANCE : distance;
        double clamped = Math.max(config.fadeStart, Math.min(config.fadeEnd, d));
        double progress = (clamped - config.fadeStart) / (config.fadeEnd - config.fadeStart);
        double opacity = 1.0D - (1.0D - config.fadeEndOpacity) * progress;
        return (int) Math.round(opacity * 255.0D);
    }
}
