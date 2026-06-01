package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public final class AlliumPacketHandler {
    private static final Gson GSON = new Gson();
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("crowbar", "player_data");

    public static void handleJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;
            CrowBarState.markAlliumDataReceived();
            JsonArray players = root.getAsJsonArray("players");
            if (players == null) return;

            for (int i = 0; i < players.size(); i++) {
                JsonObject playerObj = players.get(i).getAsJsonObject();
                String uuidStr = playerObj.get("uuid").getAsString();
                double x = playerObj.get("x").getAsDouble();
                double y = playerObj.get("y").getAsDouble();
                double z = playerObj.get("z").getAsDouble();
                boolean wearingPumpkin = playerObj.get("wearingPumpkin").getAsBoolean();
                boolean sneaking = playerObj.get("sneaking").getAsBoolean();
                boolean vanished = playerObj.get("vanished").getAsBoolean();
                int teamColor = playerObj.has("teamColor") ? playerObj.get("teamColor").getAsInt() : 0xFFFFFF;

                try {
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    AlliumPlayerData data = new AlliumPlayerData(uuid, x, y, z, wearingPumpkin, sneaking, vanished, teamColor);
                    CrowBarState.alliumPlayerData.put(uuid, data);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static class PlayerDataPayload implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerDataPayload> ID = new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<ByteBuf, PlayerDataPayload> CODEC = StreamCodec.of(
                (ByteBuf buf, PlayerDataPayload value) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new PlayerDataPayload(buf.readBytes(buf.readableBytes()))
        );

        public final ByteBuf data;

        public PlayerDataPayload(ByteBuf data) {
            this.data = data;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
