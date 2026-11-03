package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Receives the claims this player may see on the locator bar.
 *
 * <p>Positions come from the server's claim records rather than from marker entities, so a claim
 * renders at any distance and regardless of whether its terrain is loaded. Without GPExpansion
 * nothing arrives on this channel and no claims are drawn.
 */
public final class ClaimDataPacketHandler {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath("crowbar", "claim_data");

    private ClaimDataPacketHandler() {
    }

    /** Parsing and the claim key's modes are loader-agnostic and live in {@link ClaimWaypoints}. */
    public static void handleJson(String json) {
        ClaimWaypoints.onClaimData(json);
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
