package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Receives the claims this player may see on the locator bar.
 *
 * <p>Positions come from the server's claim records rather than from marker entities, so a claim
 * renders at any distance and regardless of whether its terrain is loaded. Without GPExpansion
 * nothing arrives on this channel and no claims are drawn.
 */
public final class ClaimDataPacketHandler {
    public static final Identifier CHANNEL_ID = Identifier.of("crowbar", "claim_data");

    private ClaimDataPacketHandler() {
    }

    /** Parsing and the claim key's modes are loader-agnostic and live in {@link ClaimWaypoints}. */
    public static void handleJson(String json) {
        ClaimWaypoints.onClaimData(json);
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
