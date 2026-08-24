package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Receives claim names for locator-bar waypoint markers.
 *
 * <p>A vanilla waypoint packet carries only a UUID and an icon, so the client cannot label a claim
 * marker on its own. GPExpansion sends the marker-UUID to claim-name mapping on a side channel,
 * scoped to the claims each player is allowed to see. Without the server plugin nothing arrives and
 * claim waypoints simply render unlabelled.
 */
public final class ClaimDataPacketHandler {
    private static final Gson GSON = new Gson();
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("crowbar", "claim_data");

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

    public static class ClaimDataPayload implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClaimDataPayload> ID = new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<ByteBuf, ClaimDataPayload> CODEC = StreamCodec.of(
                (ByteBuf buf, ClaimDataPayload value) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new ClaimDataPayload(buf.readBytes(buf.readableBytes()))
        );

        public final ByteBuf data;

        public ClaimDataPayload(ByteBuf data) {
            this.data = data;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
