package codes.castled.crowbar;

/** How a feature keybind behaves. */
public enum KeyMode {
    /** Each press flips the feature and the choice is saved. */
    TOGGLE,
    /** The feature is shown only while the key is held down. */
    HOLD;

    public KeyMode next() {
        return this == TOGGLE ? HOLD : TOGGLE;
    }
}
