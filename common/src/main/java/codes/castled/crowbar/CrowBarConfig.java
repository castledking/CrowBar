package codes.castled.crowbar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Client settings, saved as {@code config/crowbar.json}.
 *
 * <p>The defaults reproduce CrowBar's original look, so every appearance option is opt-in.
 * Fields are public so Gson can bind them directly. Colours are stored as {@code #RRGGBB}
 * strings to keep the file editable by hand.
 */
public final class CrowBarConfig {
    public static final String AUTO_COLOR = "auto";

    // Feature switches. These apply while the matching key is in toggle mode.
    public boolean nameTags = true;
    public boolean skins = true;
    public boolean showDistance = false;
    /** Screenshot mode. Not saved: starting a session with the HUD hidden would look broken. */
    public transient boolean viewSelf = false;

    public KeyMode nameTagsKey = KeyMode.TOGGLE;
    public KeyMode skinsKey = KeyMode.TOGGLE;
    public KeyMode showDistanceKey = KeyMode.TOGGLE;
    public KeyMode viewSelfKey = KeyMode.TOGGLE;

    // Appearance.
    /** Draws a translucent black plate behind each label. */
    public boolean labelBackground = false;
    /** Lifts hearts, hunger, the experience level and chat above the labels. */
    public boolean raiseHud = false;
    /** Draws every label except the one nearest the crosshair at reduced opacity. */
    public boolean dimUnfocusedLabels = true;
    /** Skips labels that would overlap one nearer the crosshair. */
    public boolean hideOverlappingLabels = false;
    /** Shrinks skins inside a frame in the player's dot colour, so team colours stay visible. */
    public boolean skinBorder = false;
    public boolean heightArrows = true;
    public DotColorMode dotColorMode = DotColorMode.TEAM;
    /** Used by {@link DotColorMode#CUSTOM}. */
    public String customDotColor = "#FFFFFF";
    /** Your own dot in view-self mode: {@value #AUTO_COLOR}, or {@code #RRGGBB}. */
    public String selfDotColor = AUTO_COLOR;
    /** Fades markers between {@link #fadeStart} and {@link #fadeEnd} blocks away. */
    public boolean fadeDistantMarkers = false;
    public int fadeStart = 100;
    public int fadeEnd = 1000;
    public float fadeEndOpacity = 0.3f;

    /**
     * Lets a server running the CrowBar plugin choose appearance settings while you play there.
     * See {@link ServerSettings}.
     */
    public boolean acceptServerAppearance = true;

    /** A field-for-field copy, transient fields included. */
    public CrowBarConfig copy() {
        CrowBarConfig copy = new CrowBarConfig();
        for (Field field : CrowBarConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.set(copy, field.get(this));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return copy;
    }

    /** Repairs values a hand-edited or older file may have left invalid. */
    public void validate() {
        CrowBarConfig defaults = new CrowBarConfig();
        if (nameTagsKey == null) nameTagsKey = defaults.nameTagsKey;
        if (skinsKey == null) skinsKey = defaults.skinsKey;
        if (showDistanceKey == null) showDistanceKey = defaults.showDistanceKey;
        if (viewSelfKey == null) viewSelfKey = defaults.viewSelfKey;
        if (dotColorMode == null) dotColorMode = defaults.dotColorMode;
        if (DotColors.parseHex(customDotColor) == null) customDotColor = defaults.customDotColor;
        if (selfDotColor == null || (!AUTO_COLOR.equals(selfDotColor) && DotColors.parseHex(selfDotColor) == null)) {
            selfDotColor = defaults.selfDotColor;
        }
        if (fadeStart < 0) fadeStart = 0;
        if (fadeEnd <= fadeStart) fadeEnd = fadeStart + 1;
        if (!(fadeEndOpacity >= 0f && fadeEndOpacity <= 1f)) fadeEndOpacity = defaults.fadeEndOpacity;
    }
}
