package codes.castled.crowbar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the {@code crowbar:player_data} payload that Allium and the CrowBar server plugin send.
 *
 * <pre>{"players":[{"uuid":"…","x":0,"y":64,"z":0,"wearingPumpkin":false,"sneaking":false,
 *   "vanished":false,"teamColor":16777215,"bearingOnly":false,"name":"Steve"}]}</pre>
 *
 * {@code teamColor}, {@code bearingOnly} and {@code name} are optional; older senders leave them out.
 */
public final class PlayerDataJson {
    public static final String CHANNEL = "crowbar:player_data";

    private PlayerDataJson() {
    }

    /**
     * @return every well-formed player entry by UUID, or {@code null} when the payload is not a
     *         JSON object at all. Malformed entries are skipped rather than failing the whole payload.
     */
    public static Map<UUID, AlliumPlayerData> parse(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }

        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        JsonElement list = root.get("players");
        if (list == null || !list.isJsonArray()) return players;
        JsonArray array = list.getAsJsonArray();
        for (JsonElement element : array) {
            try {
                JsonObject player = element.getAsJsonObject();
                UUID uuid = UUID.fromString(player.get("uuid").getAsString());
                int teamColor = player.has("teamColor") ? player.get("teamColor").getAsInt() : 0xFFFFFF;
                boolean bearingOnly = player.has("bearingOnly") && player.get("bearingOnly").getAsBoolean();
                String name = player.has("name") && player.get("name").isJsonPrimitive()
                        ? player.get("name").getAsString()
                        : null;
                players.put(uuid, new AlliumPlayerData(uuid,
                        player.get("x").getAsDouble(),
                        player.get("y").getAsDouble(),
                        player.get("z").getAsDouble(),
                        player.get("wearingPumpkin").getAsBoolean(),
                        player.get("sneaking").getAsBoolean(),
                        player.get("vanished").getAsBoolean(),
                        teamColor, bearingOnly, name == null || name.isBlank() ? null : name, true));
            } catch (RuntimeException ignored) {
                // A missing field, wrong type or bad UUID drops just this entry.
            }
        }
        return players;
    }
}
