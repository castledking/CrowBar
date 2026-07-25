package codes.castled.crowbar;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

import static org.lwjgl.glfw.GLFW.*;

public final class CrowBarClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("crowbar", "controls"));

    private KeyMapping toggleNameTags;
    private KeyMapping toggleSkins;
    private KeyMapping toggleViewSelf;
    private KeyMapping toggleShowDistance;
    private KeyMapping toggleClaimWaypoints;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(
                AlliumPacketHandler.PlayerDataPayload.ID,
                AlliumPacketHandler.PlayerDataPayload.CODEC
        );

        ClientPlayNetworking.registerGlobalReceiver(
                AlliumPacketHandler.PlayerDataPayload.ID,
                (payload, context) -> {
                    try {
                        String json = payload.data.toString(StandardCharsets.UTF_8);
                        AlliumPacketHandler.handleJson(json);
                    } catch (Exception ignored) {
                    }
                }
        );

        PayloadTypeRegistry.clientboundPlay().register(
                ClaimDataPacketHandler.ClaimDataPayload.ID,
                ClaimDataPacketHandler.ClaimDataPayload.CODEC
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ClaimDataPacketHandler.ClaimDataPayload.ID,
                (payload, context) -> {
                    try {
                        String json = payload.data.toString(StandardCharsets.UTF_8);
                        ClaimDataPacketHandler.handleJson(json);
                    } catch (Exception ignored) {
                    }
                }
        );

        toggleNameTags = createBinding("key.crowbar.toggle_nametags", InputConstants.Type.KEYSYM, GLFW_KEY_N);
        toggleSkins = createBinding("key.crowbar.toggle_skins", InputConstants.Type.KEYSYM, GLFW_KEY_B);
        // Unbound by default so Z is free for the claim waypoint toggle; players who want the
        // self view can bind it themselves.
        toggleViewSelf = createBinding("key.crowbar.view_self", InputConstants.Type.KEYSYM, GLFW_KEY_UNKNOWN);
        toggleShowDistance = createBinding("key.crowbar.show_distance", InputConstants.Type.KEYSYM, GLFW_KEY_X);
        toggleClaimWaypoints = createBinding("key.crowbar.toggle_claim_waypoints", InputConstants.Type.KEYSYM, GLFW_KEY_Z);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Deliberately ungated. These toggle display preferences that persist and
            // take effect whenever the bar next appears, so requiring the bar to be live
            // right now would swallow the press — consumeClick drains it either way — and
            // would disagree with the config screen, which sets the same fields unguarded.
            // On a plain multiplayer server the old condition was only true while another
            // player was in waypoint range, so the keys silently did nothing when alone.
            while (toggleNameTags.consumeClick()) {
                if (shouldSkipKeybind(toggleNameTags)) continue;
                CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
            }
            while (toggleSkins.consumeClick()) {
                if (shouldSkipKeybind(toggleSkins)) continue;
                CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                showToggle(client, "Skins", CrowBarState.skinsEnabled);
            }
            while (toggleViewSelf.consumeClick()) {
                if (shouldSkipKeybind(toggleViewSelf)) continue;
                CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                showToggle(client, "View self", CrowBarState.viewSelfEnabled);
            }
            while (toggleShowDistance.consumeClick()) {
                if (shouldSkipKeybind(toggleShowDistance)) continue;
                CrowBarState.showDistance = !CrowBarState.showDistance;
                showToggle(client, "Show distance", CrowBarState.showDistance);
            }
            while (toggleClaimWaypoints.consumeClick()) {
                if (shouldSkipKeybind(toggleClaimWaypoints)) continue;
                // Only meaningful with GPExpansion on the server; without it there is nothing to
                // cycle through and the key should stay free for other mods.
                if (!CrowBarState.hasClaimDataReceived()) continue;
                CrowBarState.ClaimWaypointMode mode = CrowBarState.cycleClaimWaypointMode();
                showMessage(client, "Claim waypoints: " + switch (mode) {
                    case OFF -> "off";
                    case OWNED -> "owned";
                    case TRUSTED -> "owned + trusted";
                });
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                CrowBarState.clearAlliumData();
                CrowBarState.isIntegratedServer = client.getSingleplayerServer() != null;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CrowBarState.clearAlliumData()
        );
    }

    private static boolean hasModifiersPressed() {
        long window = Minecraft.getInstance().getWindow().handle();
        return glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS;
    }

    private static boolean hasBoundModifiers(KeyMapping mapping) {
        try {
            Class<?> apiClass = Class.forName("de.siphalor.amecs.key_modifiers.api.AmecsKeyModifiersApi");
            Object modifiers = apiClass.getMethod("getBoundModifiers", KeyMapping.class).invoke(null, mapping);
            return !((Boolean) modifiers.getClass().getMethod("isUnset").invoke(modifiers));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldSkipKeybind(KeyMapping mapping) {
        if (hasBoundModifiers(mapping)) {
            return false;
        }
        return hasModifiersPressed();
    }

    private static KeyMapping createBinding(String id, InputConstants.Type type, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, type, code, CATEGORY));
    }

    private static void showMessage(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.literal(message));
        }
    }

    private static void showToggle(Minecraft client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.literal(label + ": " + (enabled ? "on" : "off")));
        }
    }
}
