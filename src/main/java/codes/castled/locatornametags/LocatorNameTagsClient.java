package codes.castled.locatornametags;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class LocatorNameTagsClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("crowbar", "controls"));

    private KeyBinding toggleNameTags;
    private KeyBinding toggleSkins;
    private KeyBinding toggleViewSelf;

    @Override
    public void onInitializeClient() {
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleNameTags.wasPressed()) {
                LocatorNameTagsState.nameTagsEnabled = !LocatorNameTagsState.nameTagsEnabled;
                showToggle(client, "Name tags", LocatorNameTagsState.nameTagsEnabled);
            }
            while (toggleSkins.wasPressed()) {
                LocatorNameTagsState.skinsEnabled = !LocatorNameTagsState.skinsEnabled;
                showToggle(client, "Skins", LocatorNameTagsState.skinsEnabled);
            }
            while (toggleViewSelf.wasPressed()) {
                LocatorNameTagsState.viewSelfEnabled = !LocatorNameTagsState.viewSelfEnabled;
                showToggle(client, "View self", LocatorNameTagsState.viewSelfEnabled);
            }
        });
    }

    private static void showToggle(net.minecraft.client.MinecraftClient client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(label + ": " + (enabled ? "on" : "off")), true);
        }
    }
}
