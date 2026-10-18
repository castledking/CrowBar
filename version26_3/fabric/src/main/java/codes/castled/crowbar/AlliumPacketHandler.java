package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;

public final class AlliumPacketHandler {
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("crowbar", "player_data");

    public static void handleJson(String json) {
        Map<UUID, AlliumPlayerData> snapshot = PlayerDataJson.parse(json);
        if (snapshot == null) return;
        CrowBarState.markAlliumDataReceived();
        CrowBarState.alliumPlayerData.clear();
        CrowBarState.alliumPlayerData.putAll(snapshot);
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
