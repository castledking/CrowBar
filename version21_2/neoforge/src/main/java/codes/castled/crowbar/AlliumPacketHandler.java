package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public final class AlliumPacketHandler {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath("crowbar", "player_data");

    public static void handleJson(String json) {
        Map<UUID, AlliumPlayerData> snapshot = PlayerDataJson.parse(json);
        if (snapshot == null) return;
        CrowBarState.alliumDataReceived = true;
        CrowBarState.alliumPlayerData.clear();
        CrowBarState.alliumPlayerData.putAll(snapshot);
    }

    public record PlayerDataPayload(ByteBuf data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerDataPayload> TYPE =
                new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<ByteBuf, PlayerDataPayload> CODEC = StreamCodec.of(
                (ByteBuf buf, PlayerDataPayload value) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new PlayerDataPayload(buf.readBytes(buf.readableBytes()))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
