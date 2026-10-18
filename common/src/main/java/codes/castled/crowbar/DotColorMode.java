package codes.castled.crowbar;

/** How CrowBar colours the player dots it draws. */
public enum DotColorMode {
    /** Team colour when the player has one, otherwise a colour derived from their UUID. */
    TEAM,
    /** A colour derived from each player's UUID, ignoring teams. */
    UNIQUE,
    /** One colour, {@link CrowBarConfig#customDotColor}, for every player. */
    CUSTOM;

    public DotColorMode next() {
        DotColorMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
