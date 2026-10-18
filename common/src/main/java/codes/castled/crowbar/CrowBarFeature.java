package codes.castled.crowbar;

/** The display switches that have their own keybind. */
public enum CrowBarFeature {
    NAME_TAGS("Name tags"),
    SKINS("Skins"),
    SHOW_DISTANCE("Show distance"),
    VIEW_SELF("View self");

    private final String label;

    CrowBarFeature(String label) {
        this.label = label;
    }

    /** Action-bar label used when a toggle key flips this feature. */
    public String label() {
        return label;
    }

    /** The saved on/off value, which is what applies while the key is in toggle mode. */
    public boolean isOn(CrowBarConfig config) {
        return switch (this) {
            case NAME_TAGS -> config.nameTags;
            case SKINS -> config.skins;
            case SHOW_DISTANCE -> config.showDistance;
            case VIEW_SELF -> config.viewSelf;
        };
    }

    public void set(CrowBarConfig config, boolean value) {
        switch (this) {
            case NAME_TAGS -> config.nameTags = value;
            case SKINS -> config.skins = value;
            case SHOW_DISTANCE -> config.showDistance = value;
            case VIEW_SELF -> config.viewSelf = value;
        }
    }

    public KeyMode keyMode(CrowBarConfig config) {
        return switch (this) {
            case NAME_TAGS -> config.nameTagsKey;
            case SKINS -> config.skinsKey;
            case SHOW_DISTANCE -> config.showDistanceKey;
            case VIEW_SELF -> config.viewSelfKey;
        };
    }

    public void setKeyMode(CrowBarConfig config, KeyMode mode) {
        switch (this) {
            case NAME_TAGS -> config.nameTagsKey = mode;
            case SKINS -> config.skinsKey = mode;
            case SHOW_DISTANCE -> config.showDistanceKey = mode;
            case VIEW_SELF -> config.viewSelfKey = mode;
        }
    }
}
