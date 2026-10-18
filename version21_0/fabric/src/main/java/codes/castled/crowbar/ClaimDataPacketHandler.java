package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Receives the claims this player may see on the locator bar.
 *
 * <p>Positions come from the server's claim records rather than from marker entities, so a claim
 * renders at any distance and regardless of whether its terrain is loaded. Without GPExpansion
 * nothing arrives on this channel and no claims are drawn.
 */
public final class ClaimDataPacketHandler {
    private static final Gson GSON = new Gson();
    public static final Identifier CHANNEL_ID = Identifier.of("crowbar", "claim_data");

    private ClaimDataPacketHandler() {
    }

    public static void handleJson(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;
            JsonArray claims = root.getAsJsonArray("claims");
            if (claims == null) return;

            List<ClaimWaypointData> snapshot = new ArrayList<>();
            Set<UUID> markerIds = new HashSet<>();
            for (int i = 0; i < claims.size(); i++) {
                JsonObject claim = claims.get(i).getAsJsonObject();
                if (!claim.has("id") || !claim.has("x")) continue;

                UUID markerId = null;
                if (claim.has("uuid")) {
                    try {
                        markerId = UUID.fromString(claim.get("uuid").getAsString());
                        markerIds.add(markerId);
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                snapshot.add(new ClaimWaypointData(
                        claim.get("id").getAsString(),
                        claim.has("name") ? claim.get("name").getAsString() : null,
                        claim.get("x").getAsDouble(),
                        claim.has("y") ? claim.get("y").getAsDouble() : 0,
                        claim.get("z").getAsDouble(),
                        claim.has("owned") && claim.get("owned").getAsBoolean(),
                        markerId,
                        claim.has("color") ? (0xFF000000 | (claim.get("color").getAsInt() & 0xFFFFFF)) : null));
            }

            // Even an empty list proves GPExpansion is present and talking, which is what
            // gates the claim waypoint keybind.
            CrowBarState.setClaimWaypoints(snapshot, markerIds);
        } catch (Exception ignored) {
        }
    }

    public static class ClaimDataPayload implements CustomPayload {
        public static final CustomPayload.Id<ClaimDataPayload> ID = new CustomPayload.Id<>(CHANNEL_ID);

        public static final PacketCodec<ByteBuf, ClaimDataPayload> CODEC = PacketCodec.of(
                (ClaimDataPayload value, ByteBuf buf) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new ClaimDataPayload(buf.readBytes(buf.readableBytes()))
        );

        public final ByteBuf data;

        public ClaimDataPayload(ByteBuf data) {
            this.data = data;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
