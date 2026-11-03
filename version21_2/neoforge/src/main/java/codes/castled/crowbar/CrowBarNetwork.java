package codes.castled.crowbar;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.charset.StandardCharsets;

/**
 * The channels beyond Allium's: server settings and dot colours shared with the CrowBar plugin, the
 * claim mode shared with GPExpansion, and Player Locator Plus's positions. Each carries raw bytes
 * that the common module reads and writes.
 */
final class CrowBarNetwork {
    static final CustomPacketPayload.Type<RawPayload> SERVER_SETTINGS = type("crowbar", "server_settings");
    static final CustomPacketPayload.Type<RawPayload> PLAYER_COLOR = type("crowbar", "player_color");
    static final CustomPacketPayload.Type<RawPayload> CLAIM_MODE =
            type(ClaimWaypoints.MODE_CHANNEL_NAMESPACE, ClaimWaypoints.MODE_CHANNEL_PATH);
    static final CustomPacketPayload.Type<RawPayload> PLAYER_LOCATOR_PLUS =
            type(PlayerLocatorPlusData.NAMESPACE, PlayerLocatorPlusData.PATH);

    record RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) implements CustomPacketPayload {
    }

    private CrowBarNetwork() {
    }

    /** Registers the channels with an optional registrar, so servers without them still connect. */
    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(SERVER_SETTINGS, codec(SERVER_SETTINGS), (payload, context) ->
                ServerConnection.onServerSettings(new String(payload.data(), StandardCharsets.UTF_8)));
        // Only the server reads this; the handler is for the integrated server, which ignores it.
        registrar.playToServer(PLAYER_COLOR, codec(PLAYER_COLOR), (payload, context) -> {
        });
        // Both ways: the server says which claim mode is active, and the claim key asks it to change.
        // NeoForge announces the channel to Bukkit servers, which is how GPExpansion learns that this
        // client can follow its mode. The integrated server never sends it and ignores what it gets.
        registrar.playBidirectional(CLAIM_MODE, codec(CLAIM_MODE), (payload, context) -> {
            if (context.flow() == PacketFlow.CLIENTBOUND) {
                ClaimWaypoints.onServerMode(new String(payload.data(), StandardCharsets.UTF_8));
            }
        });

        // A port of Player Locator Plus would register this channel itself, and a channel can only be
        // registered once.
        if (!ModList.get().isLoaded("player_locator_plus")) {
            registrar.playToClient(PLAYER_LOCATOR_PLUS, codec(PLAYER_LOCATOR_PLUS), (payload, context) -> {
                Player player = context.player();
                if (player != null && PlayerLocatorPlusData.apply(payload.data(),
                        player.getX(), player.getY(), player.getZ(), CrowBarState.alliumPlayerData)) {
                    CrowBarState.alliumDataReceived = true;
                }
            });
        }
    }

    /**
     * Client only: lets the common module send the dot colour once the server says it accepts one,
     * and the claim key's mode requests.
     */
    static void installSenders() {
        ServerConnection.setColorSender(data -> send(PLAYER_COLOR, data));
        ClaimWaypoints.setModeSender(data -> send(CLAIM_MODE, data));
    }

    private static boolean send(CustomPacketPayload.Type<RawPayload> type, byte[] data) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // Paper announces the channels its plugins listen on; NeoForge refuses to send on any other.
        if (connection == null || !connection.hasChannel(type)) return false;
        connection.send(new ServerboundCustomPayloadPacket(new RawPayload(type, data)));
        return true;
    }

    private static CustomPacketPayload.Type<RawPayload> type(String namespace, String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, path));
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
