package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * The channels beyond Allium's: server settings and dot colours shared with the CrowBar plugin, and
 * Player Locator Plus's positions. Each carries raw bytes that the common module reads and writes.
 */
final class CrowBarNetwork {
    static final CustomPayload.Id<RawPayload> SERVER_SETTINGS = id("crowbar", "server_settings");
    static final CustomPayload.Id<RawPayload> PLAYER_COLOR = id("crowbar", "player_color");
    static final CustomPayload.Id<RawPayload> PLAYER_LOCATOR_PLUS =
            id(PlayerLocatorPlusData.NAMESPACE, PlayerLocatorPlusData.PATH);

    record RawPayload(CustomPayload.Id<RawPayload> id, byte[] data) implements CustomPayload {
        @Override
        public Id<? extends CustomPayload> getId() {
            return id;
        }
    }

    private CrowBarNetwork() {
    }

    static void register() {
        PayloadTypeRegistry.playS2C().register(SERVER_SETTINGS, codec(SERVER_SETTINGS));
        ClientPlayNetworking.registerGlobalReceiver(SERVER_SETTINGS, (payload, context) ->
                ServerConnection.onServerSettings(new String(payload.data(), StandardCharsets.UTF_8)));

        PayloadTypeRegistry.playC2S().register(PLAYER_COLOR, codec(PLAYER_COLOR));
        ServerConnection.setColorSender(data -> {
            if (!ClientPlayNetworking.canSend(PLAYER_COLOR)) return false;
            ClientPlayNetworking.send(new RawPayload(PLAYER_COLOR, data));
            return true;
        });

        // Player Locator Plus registers this channel itself, and a channel can only be registered once.
        if (!FabricLoader.getInstance().isModLoaded(PlayerLocatorPlusData.MOD_ID)) {
            PayloadTypeRegistry.playS2C().register(PLAYER_LOCATOR_PLUS, codec(PLAYER_LOCATOR_PLUS));
            ClientPlayNetworking.registerGlobalReceiver(PLAYER_LOCATOR_PLUS, (payload, context) -> {
                var player = context.player();
                if (player != null && PlayerLocatorPlusData.apply(payload.data(),
                        player.getX(), player.getY(), player.getZ(), CrowBarState.alliumPlayerData)) {
                    CrowBarState.alliumDataReceived = true;
                }
            });
        }
    }

    private static CustomPayload.Id<RawPayload> id(String namespace, String path) {
        return new CustomPayload.Id<>(Identifier.of(namespace, path));
    }

    private static PacketCodec<ByteBuf, RawPayload> codec(CustomPayload.Id<RawPayload> id) {
        return PacketCodec.of(
                (RawPayload value, ByteBuf buf) -> buf.writeBytes(value.data()),
                (ByteBuf buf) -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new RawPayload(id, data);
                });
    }
}
