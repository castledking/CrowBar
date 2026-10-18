package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLiftTest {
    private static final long FRAME = 16_000_000L;

    @Test
    void risesToClearTheTallestLabelWithoutOvershooting() {
        HudLift lift = new HudLift();
        long now = 0;
        lift.beginFrame(now, true);

        float previous = 0f;
        for (int i = 0; i < 60; i++) {
            // A 13px plate whose top is 45px above the bottom of a 240px screen.
            lift.requestClearance(240 - 45, 240);
            lift.requestClearance(240 - 41, 240);
            now += FRAME;
            lift.beginFrame(now, true);
            assertTrue(lift.offset() >= previous, "never moves back down while rising");
            assertTrue(lift.offset() <= 16f, "never overshoots into the labels");
            previous = lift.offset();
        }

        assertEquals(16f, lift.offset());
    }

    @Test
    void settlesBackDownWhenLabelsDisappear() {
        HudLift lift = new HudLift();
        long now = 0;
        for (int i = 0; i < 60; i++) {
            lift.requestClearance(240 - 45, 240);
            lift.beginFrame(now += FRAME, true);
        }
        for (int i = 0; i < 60; i++) {
            lift.beginFrame(now += FRAME, true);
        }

        assertEquals(0f, lift.offset());
    }

    @Test
    void staysDownWhenDisabled() {
        HudLift lift = new HudLift();
        long now = 0;
        for (int i = 0; i < 60; i++) {
            lift.requestClearance(240 - 45, 240);
            lift.beginFrame(now += FRAME, false);
        }

        assertEquals(0f, lift.offset());
    }

    @Test
    void ignoresLabelsThatAlreadyClearTheStatusBars() {
        HudLift lift = new HudLift();
        long now = 0;
        for (int i = 0; i < 60; i++) {
            lift.requestClearance(240 - 20, 240);
            lift.beginFrame(now += FRAME, true);
        }

        assertEquals(0f, lift.offset());
    }
}
