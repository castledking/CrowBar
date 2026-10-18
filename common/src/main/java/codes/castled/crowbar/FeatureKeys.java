package codes.castled.crowbar;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns feature keybind input into feature state, for both toggle and hold keys.
 *
 * <p>Every loader entrypoint feeds its keys through {@link #tick} once per client tick and then
 * reads the result back with {@link #isActive}. Only the client thread touches this class.
 */
public final class FeatureKeys {
    private static final Set<CrowBarFeature> HELD = EnumSet.noneOf(CrowBarFeature.class);

    private FeatureKeys() {
    }

    /**
     * Applies one client tick of input for a feature key.
     *
     * @param presses completed presses this tick, already filtered for modifier keys
     * @param held    whether the key is down right now, also filtered for modifier keys
     * @param notify  receives the action-bar message when a toggle key flips the feature
     * @return whether the saved value changed, so the caller knows to save the config
     */
    public static boolean tick(CrowBarConfig config, CrowBarFeature feature, int presses, boolean held, Consumer<String> notify) {
        if (held) {
            HELD.add(feature);
        } else {
            HELD.remove(feature);
        }

        // A hold key still drains its presses so they do not pile up, but the press itself
        // changes nothing: the feature simply follows the key.
        if (feature.keyMode(config) != KeyMode.TOGGLE || presses == 0) return false;

        boolean before = feature.isOn(config);
        boolean value = before ^ (presses % 2 == 1);
        feature.set(config, value);
        notify.accept(feature.label() + ": " + (value ? "on" : "off"));
        return value != before;
    }

    /** Whether the feature should be drawn right now. */
    public static boolean isActive(CrowBarConfig config, CrowBarFeature feature) {
        return feature.keyMode(config) == KeyMode.HOLD
                ? HELD.contains(feature)
                : feature.isOn(config);
    }
}
