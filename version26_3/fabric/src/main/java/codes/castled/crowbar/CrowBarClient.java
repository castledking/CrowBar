package codes.castled.crowbar;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public final class CrowBarClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("crowbar", "controls"));
    private static final MessageRateLimiter NO_CLAIMS_MESSAGE_LIMITER =
            new MessageRateLimiter(10_000);

    private KeyMapping toggleNameTags;
    private KeyMapping toggleSkins;
    private KeyMapping toggleViewSelf;
    private KeyMapping toggleShowDistance;
    private KeyMapping toggleClaimWaypoints;
    private KeyMapping openConfig;

    @Override
    public void onInitializeClient() {
        CrowBarConfigStore.init(FabricLoader.getInstance().getConfigDir());
        CrowBarState.syncFeatures();

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

        CrowBarNetwork.register();

        toggleNameTags = createBinding("key.crowbar.toggle_nametags", InputConstants.Type.KEYBOARD, InputConstants.KEY_N);
        toggleSkins = createBinding("key.crowbar.toggle_skins", InputConstants.Type.KEYBOARD, InputConstants.KEY_B);
        // Unbound by default so Z is free for the claim waypoint toggle; players who want the
        // self view can bind it themselves.
        toggleViewSelf = createBinding("key.crowbar.view_self", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue());
        toggleShowDistance = createBinding("key.crowbar.show_distance", InputConstants.Type.KEYBOARD, InputConstants.KEY_X);
        toggleClaimWaypoints = createBinding("key.crowbar.toggle_claim_waypoints", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z);
        openConfig = createBinding("key.crowbar.open_config", InputConstants.Type.KEYBOARD, InputConstants.KEY_BACKSLASH);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Deliberately ungated. These toggle display preferences that persist and
            // take effect whenever the bar next appears, so requiring the bar to be live
            // right now would swallow the press — consumeClick drains it either way — and
            // would disagree with the config screen, which changes the same settings unguarded.
            // On a plain multiplayer server the old condition was only true while another
            // player was in waypoint range, so the keys silently did nothing when alone.
            tickFeatureKey(client, toggleNameTags, CrowBarFeature.NAME_TAGS);
            tickFeatureKey(client, toggleSkins, CrowBarFeature.SKINS);
            tickFeatureKey(client, toggleViewSelf, CrowBarFeature.VIEW_SELF);
            tickFeatureKey(client, toggleShowDistance, CrowBarFeature.SHOW_DISTANCE);
            CrowBarState.syncFeatures();
            while (openConfig.consumeClick()) {
                if (shouldSkipKeybind(openConfig)) continue;
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new CrowBarConfigScreen(null));
                }
            }
            while (toggleClaimWaypoints.consumeClick()) {
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                CrowBarState.clearAlliumData();
                ServerConnection.reset();
                CrowBarState.isIntegratedServer = client.getSingleplayerServer() != null;
                NO_CLAIMS_MESSAGE_LIMITER.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                CrowBarState.clearAlliumData();
                ServerConnection.reset();
                NO_CLAIMS_MESSAGE_LIMITER.reset();
        });
    }

    /** Feeds one feature key into {@link FeatureKeys}, honouring its toggle or hold mode. */
    private static void tickFeatureKey(Minecraft client, KeyMapping key, CrowBarFeature feature) {
        boolean skip = shouldSkipKeybind(key);
        int presses = 0;
        while (key.consumeClick()) {
            if (!skip) presses++;
        }
        if (FeatureKeys.tick(CrowBarConfigStore.config(), feature, presses, key.isDown() && !skip,
                message -> showMessage(client, message))) {
            CrowBarConfigStore.save();
        }
    }

    private static boolean hasModifiersPressed() {
        // 26.3 replaced GLFW with SDL3; key state is read through InputConstants.
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
                || InputConstants.isKeyDown(InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(InputConstants.KEY_RALT)
                || InputConstants.isKeyDown(InputConstants.KEY_LGUI)
                || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
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

    private static void showClaimsLoading(Minecraft client) {
        if (client.player != null) {
            client.player.sendOverlayMessage(
                    Component.literal("Collecting claims... Please wait.").withStyle(ChatFormatting.RED));
        }
    }

    private static void showNoClaimsFound(Minecraft client) {
        if (client.player != null && NO_CLAIMS_MESSAGE_LIMITER.tryAcquire(System.currentTimeMillis())) {
            client.player.sendOverlayMessage(
                    Component.literal("No claims found").withStyle(ChatFormatting.RED));
        }
    }
}
