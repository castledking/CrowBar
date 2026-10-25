package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.AlliumPlayerData;
import codes.castled.crowbar.PlayerDataJson;
import codes.castled.crowbar.bukkit.PluginSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLocatorModuleTest {
    @Test
    void deferredSenderKeepsAlliumPlayersThroughSpawnUpdatesAndClear() {
        Fixture fixture = new Fixture();
        UUID realPlayer = UUID.randomUUID();
        Map<UUID, AlliumPlayerData> client = new ConcurrentHashMap<>();
        String allium = "{\"players\":[{\"uuid\":\"" + realPlayer
                + "\",\"x\":1,\"y\":64,\"z\":2,\"wearingPumpkin\":false,\"sneaking\":false,\"vanished\":false}]}";
        PlayerDataJson.applySnapshot(allium, client);

        fixture.spawn();
        for (int tick = 0; tick < 20; tick++) {
            fixture.locator.refresh(fixture.viewer);
            JsonObject sent = fixture.lastMessage();
            assertTrue(sent.get("fakePlayers").getAsBoolean());
            assertEquals(6, sent.getAsJsonArray("players").size());
            assertTrue(PlayerDataJson.applySnapshot(sent.toString(), client));
            assertTrue(PlayerDataJson.applySnapshot(allium, client));
            assertEquals(7, client.size());
        }

        fixture.fakes.clear(fixture.viewerId);
        fixture.locator.refresh(fixture.viewer);
        assertTrue(PlayerDataJson.applySnapshot(fixture.lastMessage().toString(), client));
        assertEquals(Set.of(realPlayer), client.keySet());
        int sent = fixture.messages.size();
        fixture.locator.refresh(fixture.viewer);
        assertEquals(sent, fixture.messages.size(), "No further fake traffic after clear");
    }

    @Test
    void deferredSenderIsSilentUntilThisViewerHasFakes() {
        Fixture fixture = new Fixture();
        fixture.locator.refresh(fixture.viewer);
        fixture.fakes.spawn(UUID.randomUUID(), fixture.worldId, 0, 64, 0, 6, 0, new Random(2));
        fixture.locator.refresh(fixture.viewer);
        assertTrue(fixture.messages.isEmpty());
    }

    @Test
    void changingWorldClearsThePreviousFakeSnapshot() {
        Fixture fixture = new Fixture();
        fixture.spawn();
        fixture.locator.refresh(fixture.viewer);
        fixture.worldId = UUID.randomUUID();
        fixture.locator.refresh(fixture.viewer);
        assertTrue(fixture.lastMessage().getAsJsonArray("players").isEmpty());
    }

    @Test
    void losingReceivePermissionClearsThePreviousFakeSnapshot() {
        Fixture fixture = new Fixture();
        fixture.spawn();
        fixture.locator.refresh(fixture.viewer);
        fixture.allowed = false;
        fixture.locator.refresh(fixture.viewer);
        assertTrue(fixture.lastMessage().getAsJsonArray("players").isEmpty());
    }

    private static final class Fixture {
        final UUID viewerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        boolean allowed = true;
        final List<String> messages = new ArrayList<>();
        final FakeMarkers fakes = new FakeMarkers();
        final World world = proxy(World.class, (p, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            default -> throw new AssertionError("Unexpected world call: " + method.getName());
        });
        final Player viewer = proxy(Player.class, (p, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> viewerId;
            case "isOnline" -> true;
            case "getListeningPluginChannels" -> Set.of(PlayerLocatorModule.CHANNEL);
            case "hasPermission" -> allowed;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "getWorld" -> world;
            case "sendPluginMessage" -> {
                assertEquals(PlayerLocatorModule.CHANNEL, args[1]);
                messages.add(new String((byte[]) args[2], StandardCharsets.UTF_8));
                yield null;
            }
            default -> throw new AssertionError("Unexpected viewer call: " + method.getName());
        });
        final Plugin plugin = proxy(Plugin.class, (p, method, args) -> {
            throw new AssertionError("Deferred refresh must not use the server: " + method.getName());
        });
        final PlayerLocatorModule locator = new PlayerLocatorModule(plugin,
                PluginSettings.from(new YamlConfiguration(), Logger.getAnonymousLogger()), null, fakes, false);

        void spawn() {
            fakes.spawn(viewerId, worldId, 0, 64, 0, 6, 0, new Random(1));
        }

        JsonObject lastMessage() {
            return JsonParser.parseString(messages.getLast()).getAsJsonObject();
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
