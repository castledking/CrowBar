package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

public final class AlliumPacketHandler {
    public static final Identifier CHANNEL_ID = Identifier.of("crowbar", "player_data");

    public static void handleJson(String json) {
        Map<UUID, AlliumPlayerData> snapshot = PlayerDataJson.parse(json);
        if (snapshot == null) return;
        CrowBarState.alliumDataReceived = true;
        CrowBarState.alliumPlayerData.clear();
        CrowBarState.alliumPlayerData.putAll(snapshot);
    }

    public static class PlayerDataPayload implements CustomPayload {
        public static final CustomPayload.Id<PlayerDataPayload> ID = new CustomPayload.Id<>(CHANNEL_ID);

        public static final PacketCodec<ByteBuf, PlayerDataPayload> CODEC = PacketCodec.of(
                (PlayerDataPayload value, ByteBuf buf) -> buf.writeBytes(value.data),
                (ByteBuf buf) -> new PlayerDataPayload(buf.readBytes(buf.readableBytes()))
        );

        public final ByteBuf data;

        public PlayerDataPayload(ByteBuf data) {
            this.data = data;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
