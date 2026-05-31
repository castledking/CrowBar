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

import java.nio.charset.StandardCharsets;

public final class CrowBarClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("crowbar", "controls"));

    private KeyBinding toggleNameTags;
    private KeyBinding toggleSkins;
    private KeyBinding toggleViewSelf;
    private KeyBinding toggleForceShowAll;
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
        toggleForceShowAll = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.force_show_all",
                InputUtil.Type.KEYSYM,
                -1,
                CATEGORY
        ));
        toggleShowDistance = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.show_distance",
                InputUtil.Type.KEYSYM,
                -1,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleNameTags.wasPressed()) {
                CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
            }
            while (toggleSkins.wasPressed()) {
                CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                showToggle(client, "Skins", CrowBarState.skinsEnabled);
            }
            while (toggleViewSelf.wasPressed()) {
                CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                showToggle(client, "View self", CrowBarState.viewSelfEnabled);
            }
            while (toggleForceShowAll.wasPressed()) {
                CrowBarState.forceShowAll = !CrowBarState.forceShowAll;
                showToggle(client, "Force show all", CrowBarState.forceShowAll);
            }
            while (toggleShowDistance.wasPressed()) {
                CrowBarState.showDistance = !CrowBarState.showDistance;
                showToggle(client, "Show distance", CrowBarState.showDistance);
            }
        });

        // Clear Allium data on disconnect so stale entries don't carry over
        // to LAN worlds or servers without Allium
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CrowBarState.alliumPlayerData.clear()
        );

        // Note: Allium-restored player rendering is now injected via InGameHudMixin
        // before renderExperienceBar, so it renders behind the XP number.
    }

    private static void showToggle(net.minecraft.client.MinecraftClient client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(label + ": " + (enabled ? "on" : "off")), true);
        }
    }
}
