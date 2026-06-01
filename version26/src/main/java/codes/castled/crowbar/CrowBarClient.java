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

        toggleNameTags = createBinding("key.crowbar.toggle_nametags", InputConstants.Type.KEYSYM, GLFW_KEY_N);
        toggleSkins = createBinding("key.crowbar.toggle_skins", InputConstants.Type.KEYSYM, GLFW_KEY_B);
        toggleViewSelf = createBinding("key.crowbar.view_self", InputConstants.Type.KEYSYM, GLFW_KEY_Z);
        toggleShowDistance = createBinding("key.crowbar.show_distance", InputConstants.Type.KEYSYM, GLFW_KEY_X);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleNameTags.consumeClick()) {
                if (hasModifiersPressed()) continue;
                CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
            }
            while (toggleSkins.consumeClick()) {
                if (hasModifiersPressed()) continue;
                CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                showToggle(client, "Skins", CrowBarState.skinsEnabled);
            }
            while (toggleViewSelf.consumeClick()) {
                if (hasModifiersPressed()) continue;
                CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                showToggle(client, "View self", CrowBarState.viewSelfEnabled);
            }
            while (toggleShowDistance.consumeClick()) {
                if (hasModifiersPressed()) continue;
                CrowBarState.showDistance = !CrowBarState.showDistance;
                showToggle(client, "Show distance", CrowBarState.showDistance);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                CrowBarState.clearAlliumData()
        );
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

    private static KeyMapping createBinding(String id, InputConstants.Type type, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, type, code, CATEGORY));
    }

    private static void showToggle(Minecraft client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.literal(label + ": " + (enabled ? "on" : "off")));
        }
    }
}
