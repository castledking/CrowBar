package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

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
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath("crowbar", "claim_data");

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

    public record ClaimDataPayload(ByteBuf data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClaimDataPayload> TYPE =
                new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<ByteBuf, ClaimDataPayload> CODEC = StreamCodec.of(
                (ByteBuf buf, ClaimDataPayload value) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new ClaimDataPayload(buf.readBytes(buf.readableBytes()))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
