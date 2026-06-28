package codes.castled.crowbar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public final class CrowBarClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("crowbar", "controls"));

    private KeyBinding toggleNameTags;
    private KeyBinding toggleSkins;
    private KeyBinding toggleViewSelf;
    private KeyBinding toggleShowDistance;

    @Override
    public void onInitializeClient() {
        // Register custom payload type so the client can decode Bukkit plugin messages
        PayloadTypeRegistry.playS2C().register(
                AlliumPacketHandler.PlayerDataPayload.ID,
                AlliumPacketHandler.PlayerDataPayload.CODEC
        );

        // Register receiver for Allium player data
        ClientPlayNetworking.registerGlobalReceiver(
                AlliumPacketHandler.PlayerDataPayload.ID,
                (payload, context) -> {
                    try {
                        String json = payload.data.toString(StandardCharsets.UTF_8);
                        AlliumPacketHandler.handleJson(json);
                    } catch (Exception e) {
                        // Silent fail - packet handling errors are not critical
                    }
                }
        );

        toggleNameTags = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.toggle_nametags",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_N,
                CATEGORY
        ));
        toggleSkins = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.toggle_skins",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_B,
                CATEGORY
        ));
        toggleViewSelf = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.view_self",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_V,
                CATEGORY
        ));
        toggleShowDistance = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.show_distance",
                InputUtil.Type.KEYSYM,
                -1,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean locatorActive = CrowBarState.alliumDataReceived
                    || CrowBarState.isIntegratedServer
                    || CrowBarState.isVanillaLocatorBarVisible();
            while (toggleNameTags.wasPressed()) {
                if (hasModifiersPressed() || !locatorActive) continue;
                CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
            }
            while (toggleSkins.wasPressed()) {
                if (hasModifiersPressed() || !locatorActive) continue;
                CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                showToggle(client, "Skins", CrowBarState.skinsEnabled);
            }
            while (toggleViewSelf.wasPressed()) {
                if (hasModifiersPressed() || !locatorActive) continue;
                CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                showToggle(client, "View self", CrowBarState.viewSelfEnabled);
            }
            while (toggleShowDistance.wasPressed()) {
                if (hasModifiersPressed() || !locatorActive) continue;
                CrowBarState.showDistance = !CrowBarState.showDistance;
                showToggle(client, "Show distance", CrowBarState.showDistance);
            }
        });

        // Clear Allium data on disconnect so stale entries don't carry over
        // to LAN worlds or servers without Allium
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                CrowBarState.alliumPlayerData.clear();
                CrowBarState.alliumDataReceived = false;
                CrowBarState.isIntegratedServer = client.getServer() != null;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                CrowBarState.alliumPlayerData.clear();
                CrowBarState.alliumDataReceived = false;
                CrowBarState.isIntegratedServer = false;
        });

        // Note: Allium-restored player rendering is now injected via InGameHudMixin
        // before renderExperienceBar, so it renders behind the XP number.
    }

    private static boolean hasModifiersPressed() {
        long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
    }

    private static void showToggle(net.minecraft.client.MinecraftClient client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(label + ": " + (enabled ? "on" : "off")), true);
        }
    }
}
