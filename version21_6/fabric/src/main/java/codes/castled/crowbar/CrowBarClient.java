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
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public final class CrowBarClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.category.crowbar.controls";
    private static final MessageRateLimiter NO_CLAIMS_MESSAGE_LIMITER =
            new MessageRateLimiter(10_000);

    private KeyBinding toggleNameTags;
    private KeyBinding toggleSkins;
    private KeyBinding toggleViewSelf;
    private KeyBinding toggleShowDistance;
    private KeyBinding toggleClaimWaypoints;

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

        PayloadTypeRegistry.playS2C().register(
                ClaimDataPacketHandler.ClaimDataPayload.ID,
                ClaimDataPacketHandler.ClaimDataPayload.CODEC
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ClaimDataPacketHandler.ClaimDataPayload.ID,
                (payload, context) -> {
                    try {
                        String json = payload.data.toString(StandardCharsets.UTF_8);
                        ClaimDataPacketHandler.handleJson(json);
                    } catch (Exception e) {
                        // Silent fail - packet handling errors are not critical
                    }
                }
        );

        toggleNameTags = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.toggle_nametags",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_N,
                KEY_CATEGORY
        ));
        toggleSkins = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.toggle_skins",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_B,
                KEY_CATEGORY
        ));
        // Unbound by default so the key is free for the claim waypoint toggle; players who want
        // the self view can bind it themselves.
        toggleViewSelf = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.view_self",
                InputUtil.Type.KEYSYM,
                -1,
                KEY_CATEGORY
        ));
        toggleShowDistance = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.show_distance",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_X,
                KEY_CATEGORY
        ));
        toggleClaimWaypoints = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crowbar.toggle_claim_waypoints",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_Z,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Deliberately ungated. These toggle display preferences that persist and
            // take effect whenever the bar next appears, so requiring the bar to be live
            // right now would swallow the press — consumeClick drains it either way — and
            // would disagree with the config screen, which sets the same fields unguarded.
            // On a plain multiplayer server the old condition was only true while another
            // player was in waypoint range, so the keys silently did nothing when alone.
            while (toggleNameTags.wasPressed()) {
                if (shouldSkipKeybind(toggleNameTags)) continue;
                CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
            }
            while (toggleSkins.wasPressed()) {
                if (shouldSkipKeybind(toggleSkins)) continue;
                CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                showToggle(client, "Skins", CrowBarState.skinsEnabled);
            }
            while (toggleViewSelf.wasPressed()) {
                if (shouldSkipKeybind(toggleViewSelf)) continue;
                CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                showToggle(client, "View self", CrowBarState.viewSelfEnabled);
            }
            while (toggleShowDistance.wasPressed()) {
                if (shouldSkipKeybind(toggleShowDistance)) continue;
                CrowBarState.showDistance = !CrowBarState.showDistance;
                showToggle(client, "Show distance", CrowBarState.showDistance);
            }
            while (toggleClaimWaypoints.wasPressed()) {
                if (shouldSkipKeybind(toggleClaimWaypoints)) continue;
                if (!CrowBarState.hasClaimDataReceived()) {
                    showClaimsLoading(client);
                    continue;
                }
                if (!CrowBarState.hasAnyClaimWaypoints()) {
                    showNoClaimsFound(client);
                    continue;
                }
                CrowBarState.ClaimWaypointMode mode = CrowBarState.cycleClaimWaypointMode();
                if (mode != CrowBarState.ClaimWaypointMode.OFF
                        && !CrowBarState.hasSelectedClaimWaypoints()) {
                    showNoClaimsFound(client);
                    continue;
                }
                showMessage(client, "Claim waypoints: " + switch (mode) {
                    case OFF -> "off";
                    case OWNED -> "owned";
                    case TRUSTED -> "owned + trusted";
                });
            }
        });

        // Clear Allium data on disconnect so stale entries don't carry over
        // to LAN worlds or servers without Allium
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                CrowBarState.alliumPlayerData.clear();
                CrowBarState.clearClaimWaypoints();
                CrowBarState.alliumDataReceived = false;
                CrowBarState.isIntegratedServer = client.getServer() != null;
                NO_CLAIMS_MESSAGE_LIMITER.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                CrowBarState.alliumPlayerData.clear();
                CrowBarState.clearClaimWaypoints();
                CrowBarState.alliumDataReceived = false;
                CrowBarState.isIntegratedServer = false;
                NO_CLAIMS_MESSAGE_LIMITER.reset();
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

    private static boolean hasBoundModifiers(KeyBinding binding) {
        try {
            Class<?> apiClass = Class.forName("de.siphalor.amecs.key_modifiers.api.AmecsKeyModifiersApi");
            Object modifiers = apiClass.getMethod("getBoundModifiers", KeyBinding.class).invoke(null, binding);
            return !((Boolean) modifiers.getClass().getMethod("isUnset").invoke(modifiers));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldSkipKeybind(KeyBinding binding) {
        return !hasBoundModifiers(binding) && hasModifiersPressed();
    }

    private static void showMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static void showClaimsLoading(MinecraftClient client) {
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal("Collecting claims... Please wait.").formatted(Formatting.RED), true);
        }
    }

    private static void showNoClaimsFound(MinecraftClient client) {
        if (client.player != null && NO_CLAIMS_MESSAGE_LIMITER.tryAcquire(System.currentTimeMillis())) {
            client.player.sendMessage(Text.literal("No claims found").formatted(Formatting.RED), true);
        }
    }

    private static void showToggle(net.minecraft.client.MinecraftClient client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(label + ": " + (enabled ? "on" : "off")), true);
        }
    }
}
