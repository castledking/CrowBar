package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * The channels beyond Allium's: server settings and dot colours shared with the CrowBar plugin, and
 * Player Locator Plus's positions. Each carries raw bytes that the common module reads and writes.
 */
final class CrowBarNetwork {
    static final CustomPacketPayload.Type<RawPayload> SERVER_SETTINGS = type("crowbar", "server_settings");
    static final CustomPacketPayload.Type<RawPayload> PLAYER_COLOR = type("crowbar", "player_color");
    static final CustomPacketPayload.Type<RawPayload> PLAYER_LOCATOR_PLUS =
            type(PlayerLocatorPlusData.NAMESPACE, PlayerLocatorPlusData.PATH);

    record RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) implements CustomPacketPayload {
    }

    private CrowBarNetwork() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SERVER_SETTINGS, codec(SERVER_SETTINGS));
        ClientPlayNetworking.registerGlobalReceiver(SERVER_SETTINGS, (payload, context) ->
                ServerConnection.onServerSettings(new String(payload.data(), StandardCharsets.UTF_8)));

        PayloadTypeRegistry.serverboundPlay().register(PLAYER_COLOR, codec(PLAYER_COLOR));
        ServerConnection.setColorSender(data -> {
            if (!ClientPlayNetworking.canSend(PLAYER_COLOR)) return false;
            ClientPlayNetworking.send(new RawPayload(PLAYER_COLOR, data));
            return true;
        });

        // Player Locator Plus registers this channel itself, and a channel can only be registered once.
        if (!FabricLoader.getInstance().isModLoaded(PlayerLocatorPlusData.MOD_ID)) {
            PayloadTypeRegistry.clientboundPlay().register(PLAYER_LOCATOR_PLUS, codec(PLAYER_LOCATOR_PLUS));
            ClientPlayNetworking.registerGlobalReceiver(PLAYER_LOCATOR_PLUS, (payload, context) -> {
                var player = context.player();
                if (player != null && PlayerLocatorPlusData.apply(payload.data(),
                        player.getX(), player.getY(), player.getZ(), CrowBarState.alliumPlayerData)) {
                    CrowBarState.markAlliumDataReceived();
                }
            });
        }
    }

    private static CustomPacketPayload.Type<RawPayload> type(String namespace, String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static StreamCodec<ByteBuf, RawPayload> codec(CustomPacketPayload.Type<RawPayload> type) {
        return StreamCodec.of(
                (ByteBuf buf, RawPayload value) -> buf.writeBytes(value.data()),
                (ByteBuf buf) -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new RawPayload(type, data);
                });
    }
}
