package codes.castled.crowbar;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

@Mod(CrowBarNeoForge.MOD_ID)
public final class CrowBarNeoForge {
    public static final String MOD_ID = "crowbar";
    public static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath(MOD_ID, "controls"));
    private static final MessageRateLimiter NO_CLAIMS_MESSAGE_LIMITER =
            new MessageRateLimiter(10_000);

    private KeyMapping toggleNameTags;
    private KeyMapping toggleSkins;
    private KeyMapping toggleViewSelf;
    private KeyMapping toggleShowDistance;
    private KeyMapping toggleClaimWaypoints;

    public CrowBarNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerKeys);
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, modListScreen) -> new CrowBarConfigScreen(modListScreen));

        // Game-bus listeners are only meaningful on the physical client.
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            CrowBarState.alliumPlayerData.clear();
            CrowBarState.clearClaimWaypoints();
            CrowBarState.alliumDataReceived = false;
            CrowBarState.isIntegratedServer = client.getSingleplayerServer() != null;
            NO_CLAIMS_MESSAGE_LIMITER.reset();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            CrowBarState.alliumPlayerData.clear();
            CrowBarState.clearClaimWaypoints();
            CrowBarState.alliumDataReceived = false;
            CrowBarState.isIntegratedServer = false;
            NO_CLAIMS_MESSAGE_LIMITER.reset();
        });
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        // Custom payload types so the client can decode Bukkit plugin messages
        registrar.playToClient(
                AlliumPacketHandler.PlayerDataPayload.TYPE,
                AlliumPacketHandler.PlayerDataPayload.CODEC,
                (payload, context) -> {
                    try {
                        String json = payload.data().toString(StandardCharsets.UTF_8);
                        AlliumPacketHandler.handleJson(json);
                    } catch (Exception e) {
                        // Silent fail - packet handling errors are not critical
                    }
                }
        );

        registrar.playToClient(
                ClaimDataPacketHandler.ClaimDataPayload.TYPE,
                ClaimDataPacketHandler.ClaimDataPayload.CODEC,
                (payload, context) -> {
                    try {
                        String json = payload.data().toString(StandardCharsets.UTF_8);
                        ClaimDataPacketHandler.handleJson(json);
                    } catch (Exception e) {
                        // Silent fail - packet handling errors are not critical
                    }
                }
        );
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);

        toggleNameTags = new KeyMapping(
                "key.crowbar.toggle_nametags",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY
        );
        toggleSkins = new KeyMapping(
                "key.crowbar.toggle_skins",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEY_CATEGORY
        );
        // Unbound by default so the key is free for the claim waypoint toggle; players who want
        // the self view can bind it themselves.
        toggleViewSelf = new KeyMapping(
                "key.crowbar.view_self",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KEY_CATEGORY
        );
        toggleShowDistance = new KeyMapping(
                "key.crowbar.show_distance",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KEY_CATEGORY
        );
        toggleClaimWaypoints = new KeyMapping(
                "key.crowbar.toggle_claim_waypoints",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KEY_CATEGORY
        );

        event.register(toggleNameTags);
        event.register(toggleSkins);
        event.register(toggleViewSelf);
        event.register(toggleShowDistance);
        event.register(toggleClaimWaypoints);
    }

    private void onClientTick() {
        var client = net.minecraft.client.Minecraft.getInstance();

        // Deliberately ungated. These toggle display preferences that persist and
        // take effect whenever the bar next appears, so requiring the bar to be live
        // right now would swallow the press — consumeClick drains it either way — and
        // would disagree with the config screen, which sets the same fields unguarded.
        // On a plain multiplayer server the old condition was only true while another
        // player was in waypoint range, so the keys silently did nothing when alone.
        while (toggleNameTags.consumeClick()) {
            if (shouldSkipKeybind()) continue;
            CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
            showToggle(client, "Name tags", CrowBarState.nameTagsEnabled);
        }
        while (toggleSkins.consumeClick()) {
            if (shouldSkipKeybind()) continue;
            CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
            showToggle(client, "Skins", CrowBarState.skinsEnabled);
        }
        while (toggleViewSelf.consumeClick()) {
            if (shouldSkipKeybind()) continue;
            CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
            showToggle(client, "View self", CrowBarState.viewSelfEnabled);
        }
        while (toggleShowDistance.consumeClick()) {
            if (shouldSkipKeybind()) continue;
            CrowBarState.showDistance = !CrowBarState.showDistance;
            showToggle(client, "Show distance", CrowBarState.showDistance);
        }
        while (toggleClaimWaypoints.consumeClick()) {
            if (shouldSkipKeybind()) continue;
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
    }

    private static boolean hasModifiersPressed() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
    }

    // Note: the AMECS key-modifier check from the Fabric port has no NeoForge equivalent,
    // so modifier detection falls back to raw keyboard state only.
    private static boolean shouldSkipKeybind() {
        return hasModifiersPressed();
    }

    private static void showMessage(net.minecraft.client.Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static void showClaimsLoading(net.minecraft.client.Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("Collecting claims... Please wait.").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void showNoClaimsFound(net.minecraft.client.Minecraft client) {
        if (client.player != null && NO_CLAIMS_MESSAGE_LIMITER.tryAcquire(System.currentTimeMillis())) {
            client.player.displayClientMessage(Component.literal("No claims found").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void showToggle(net.minecraft.client.Minecraft client, String label, boolean enabled) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(label + ": " + (enabled ? "on" : "off")), true);
        }
    }
}
