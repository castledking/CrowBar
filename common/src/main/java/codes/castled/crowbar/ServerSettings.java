package codes.castled.crowbar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What a server running the CrowBar plugin tells its CrowBar clients on
 * {@code crowbar:server_settings}:
 *
 * <pre>{"appearance":{"labelBackground":true,"dotColorMode":"team"},"playerColor":true}</pre>
 *
 * <ul>
 *   <li>{@code appearance}: settings the server picks for everyone, keyed by {@link CrowBarConfig}
 *       field name. Anything left out stays the player's choice, and a player can ignore the lot
 *       with {@link CrowBarConfig#acceptServerAppearance}.
 *   <li>{@code playerColor}: whether the server shows other players this player's chosen dot
 *       colour, so the client should send it on {@code crowbar:player_color}.
 * </ul>
 */
public final class ServerSettings {
    public static final String CHANNEL = "crowbar:server_settings";
    public static final String PLAYER_COLOR_CHANNEL = "crowbar:player_color";

    /** The settings a server may choose, and how to read each from JSON. Unknown keys are ignored. */
    private static final Map<String, Function<JsonPrimitive, Object>> APPEARANCE = new LinkedHashMap<>();

    static {
        for (String flag : new String[]{"labelBackground", "raiseHud", "dimUnfocusedLabels",
                "hideOverlappingLabels", "skinBorder", "heightArrows", "fadeDistantMarkers"}) {
            APPEARANCE.put(flag, value -> value.isBoolean() ? value.getAsBoolean() : null);
        }
        APPEARANCE.put("fadeStart", value -> value.isNumber() ? Math.max(0, value.getAsInt()) : null);
        APPEARANCE.put("fadeEnd", value -> value.isNumber() ? Math.max(1, value.getAsInt()) : null);
        APPEARANCE.put("fadeEndOpacity", value -> {
            if (!value.isNumber()) return null;
            float opacity = value.getAsFloat();
            return opacity >= 0f && opacity <= 1f ? opacity : null;
        });
        APPEARANCE.put("dotColorMode", value -> {
            if (!value.isString()) return null;
            try {
                return DotColorMode.valueOf(value.getAsString().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
        APPEARANCE.put("customDotColor", value -> {
            Integer rgb = value.isString() ? DotColors.parseHex(value.getAsString().trim()) : null;
            return rgb != null ? DotColors.toHex(rgb) : null;
        });
    }

    private final Map<String, Object> appearance;
    private final boolean playerColor;

    ServerSettings(Map<String, Object> appearance, boolean playerColor) {
        this.appearance = Collections.unmodifiableMap(new LinkedHashMap<>(appearance));
        this.playerColor = playerColor;
    }

    /** @return the settings, or {@code null} when the payload is not a JSON object */
    public static ServerSettings parse(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }

        Map<String, Object> appearance = new LinkedHashMap<>();
        JsonElement section = root.get("appearance");
        if (section != null && section.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject().entrySet()) {
                Function<JsonPrimitive, Object> reader = APPEARANCE.get(entry.getKey());
                if (reader == null || !entry.getValue().isJsonPrimitive()) continue;
                Object value = reader.apply(entry.getValue().getAsJsonPrimitive());
                if (value != null) appearance.put(entry.getKey(), value);
            }
        }
        JsonElement color = root.get("playerColor");
        boolean playerColor = color != null && color.isJsonPrimitive()
                && color.getAsJsonPrimitive().isBoolean() && color.getAsBoolean();
        return new ServerSettings(appearance, playerColor);
    }

    /** Field names of every setting a server may choose. */
    public static Set<String> appearanceFields() {
        return Collections.unmodifiableSet(APPEARANCE.keySet());
    }

    public boolean overrides(String field) {
        return appearance.containsKey(field);
    }

    public boolean hasAppearance() {
        return !appearance.isEmpty();
    }

    public boolean acceptsPlayerColor() {
        return playerColor;
    }

    /**
     * {@code own} with the server's choices applied, or {@code own} itself when there is nothing to
     * apply. Never modifies {@code own}.
     */
    public static CrowBarConfig effective(CrowBarConfig own, ServerSettings server) {
        if (server == null || !server.hasAppearance() || !own.acceptServerAppearance) return own;
        CrowBarConfig copy = own.copy();
        for (Map.Entry<String, Object> entry : server.appearance.entrySet()) {
            try {
                Field field = CrowBarConfig.class.getField(entry.getKey());
                field.set(copy, entry.getValue());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new IllegalStateException("Appearance field " + entry.getKey() + " does not match CrowBarConfig", e);
            }
        }
        // A server that sets only one end of the fade range can leave it inverted.
        copy.validate();
        return copy;
    }
}
