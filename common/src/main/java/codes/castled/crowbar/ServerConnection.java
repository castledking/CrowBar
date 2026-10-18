package codes.castled.crowbar;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

/**
 * What this client knows about the server it is connected to, and the dot colour it has told it.
 *
 * <p>Each loader feeds in {@code crowbar:server_settings} payloads, resets this on join and
 * disconnect, and supplies a {@link ColorSender} that writes {@code crowbar:player_color}.
 */
public final class ServerConnection {
    /** Sends a {@code crowbar:player_color} payload; returns whether the server can receive it. */
    @FunctionalInterface
    public interface ColorSender {
        boolean send(byte[] payload);
    }

    private static volatile ColorSender colorSender = payload -> false;
    private static volatile ServerSettings settings;
    /** The colour last sent this session, so saving unrelated settings does not resend it. */
    private static String sentColor;

    private ServerConnection() {
    }

    public static void setColorSender(ColorSender sender) {
        colorSender = sender;
    }

    /** The current server's settings, or {@code null} when it sent none. */
    public static ServerSettings settings() {
        return settings;
    }

    public static void onServerSettings(String json) {
        ServerSettings parsed = ServerSettings.parse(json);
        if (parsed == null) return;
        settings = parsed;
        CrowBarConfigStore.invalidate();
        if (parsed.acceptsPlayerColor()) {
            // An automatic colour sends nothing on join, so a colour picked with /crowbar color stays.
            String color = CrowBarConfigStore.config().selfDotColor;
            sentColor = CrowBarConfig.AUTO_COLOR.equals(color) || send(color) ? color : null;
        }
    }

    /** Called after every settings save; sends the dot colour if the player just changed it. */
    public static void onConfigSaved() {
        ServerSettings current = settings;
        if (current == null || !current.acceptsPlayerColor()) return;
        String color = CrowBarConfigStore.config().selfDotColor;
        if (color.equals(sentColor)) return;
        if (send(color)) sentColor = color;
    }

    public static void reset() {
        settings = null;
        sentColor = null;
        CrowBarConfigStore.invalidate();
    }

    private static boolean send(String color) {
        try {
            return colorSender.send(colorPayload(color));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** {@code {"color":"#RRGGBB"}}, or {@code {"color":"reset"}} for the automatic colour. */
    static byte[] colorPayload(String color) {
        JsonObject root = new JsonObject();
        root.addProperty("color", CrowBarConfig.AUTO_COLOR.equals(color) ? "reset" : color);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
}
