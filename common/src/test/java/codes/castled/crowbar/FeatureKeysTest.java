package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureKeysTest {
    @Test
    void togglePressFlipsAndAnnounces() {
        CrowBarConfig config = new CrowBarConfig();
        List<String> messages = new ArrayList<>();

        FeatureKeys.tick(config, CrowBarFeature.SHOW_DISTANCE, 1, true, messages::add);

        assertTrue(config.showDistance);
        assertTrue(FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE));
        assertEquals(List.of("Show distance: on"), messages);
    }

    @Test
    void releasingAToggleKeyKeepsTheFeature() {
        CrowBarConfig config = new CrowBarConfig();

        FeatureKeys.tick(config, CrowBarFeature.SHOW_DISTANCE, 1, true, m -> { });
        FeatureKeys.tick(config, CrowBarFeature.SHOW_DISTANCE, 0, false, m -> { });

        assertTrue(FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE));
    }

    @Test
    void holdKeyShowsOnlyWhileDownAndLeavesSavedValueAlone() {
        CrowBarConfig config = new CrowBarConfig();
        config.showDistanceKey = KeyMode.HOLD;
        List<String> messages = new ArrayList<>();

        FeatureKeys.tick(config, CrowBarFeature.SHOW_DISTANCE, 1, true, messages::add);
        assertTrue(FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE));

        FeatureKeys.tick(config, CrowBarFeature.SHOW_DISTANCE, 0, false, messages::add);
        assertFalse(FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE));

        assertFalse(config.showDistance);
        assertTrue(messages.isEmpty());
    }

    @Test
    void holdKeyHidesAFeatureThatIsSavedOn() {
        CrowBarConfig config = new CrowBarConfig();
        config.nameTagsKey = KeyMode.HOLD;

        FeatureKeys.tick(config, CrowBarFeature.NAME_TAGS, 0, false, m -> { });

        assertTrue(config.nameTags);
        assertFalse(FeatureKeys.isActive(config, CrowBarFeature.NAME_TAGS));
    }

    @Test
    void switchingBackToToggleRestoresTheSavedValue() {
        CrowBarConfig config = new CrowBarConfig();
        config.nameTagsKey = KeyMode.HOLD;
        FeatureKeys.tick(config, CrowBarFeature.NAME_TAGS, 0, false, m -> { });

        config.nameTagsKey = KeyMode.TOGGLE;

        assertTrue(FeatureKeys.isActive(config, CrowBarFeature.NAME_TAGS));
    }

    @Test
    void twoPressesInOneTickCancelOut() {
        CrowBarConfig config = new CrowBarConfig();

        FeatureKeys.tick(config, CrowBarFeature.SKINS, 2, false, m -> { });

        assertTrue(config.skins);
    }
}
