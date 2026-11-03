package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Receives claim names for locator-bar waypoint markers.
 *
 * <p>A vanilla waypoint packet carries only a UUID and an icon, so the client cannot label a claim
 * marker on its own. GPExpansion sends the marker-UUID to claim-name mapping on a side channel,
 * scoped to the claims each player is allowed to see. Without the server plugin nothing arrives and
 * claim waypoints simply render unlabelled.
 */
public final class ClaimDataPacketHandler {
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("crowbar", "claim_data");

    private ClaimDataPacketHandler() {
    }

    /** Parsing and the claim key's modes are loader-agnostic and live in {@link ClaimWaypoints}. */
    public static void handleJson(String json) {
        ClaimWaypoints.onClaimData(json);
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
