package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerFadeTest {
    @Test
    void opaqueWhenDisabled() {
        assertEquals(255, MarkerFade.alpha(new CrowBarConfig(), 5000));
    }

    @Test
    void fadesLinearlyBetweenStartAndEnd() {
        CrowBarConfig config = new CrowBarConfig();
        config.fadeDistantMarkers = true;

        assertEquals(255, MarkerFade.alpha(config, 50));
        assertEquals(255, MarkerFade.alpha(config, 100));
        assertEquals(Math.round(0.65 * 255), MarkerFade.alpha(config, 550));
        assertEquals(Math.round(0.3 * 255), MarkerFade.alpha(config, 1000));
        assertEquals(Math.round(0.3 * 255), MarkerFade.alpha(config, 9000));
    }

    @Test
    void unknownDistanceCountsAsTheBearingOnlyThreshold() {
        CrowBarConfig config = new CrowBarConfig();
        config.fadeDistantMarkers = true;

        assertEquals(MarkerFade.alpha(config, MarkerFade.UNKNOWN_DISTANCE), MarkerFade.alpha(config, -1));
    }
}
