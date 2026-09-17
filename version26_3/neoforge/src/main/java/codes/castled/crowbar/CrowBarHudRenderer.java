package codes.castled.crowbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.scores.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CrowBarHudRenderer {
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");
    private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_background");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_ARROW_WIDTH = 7;
    private static final int LOCATOR_ARROW_HEIGHT = 5;
    private static final double LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE = 35.0D;
    private static final double LOCATOR_VIEW_MIN_YAW = -61.0D;
    private static final double LOCATOR_VIEW_MAX_YAW = 60.0D;
    private static final Identifier LOCATOR_ARROW_UP = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_ARROW_DOWN = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_down");
    private static final Identifier[] LOCATOR_DOT_SPRITES = {
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_0"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_1"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_2"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_3")
    };

    // Golden angle (≈137.508°) — matches Allium server's fallback color generator.
    // Ensures a player gets the same hue from both server and client fallback paths.
    /** Vanilla's bowtie sprite, so a server resource pack can restyle it for everyone at once. */
    private static final Identifier CLAIM_BOWTIE_SPRITE = Identifier.withDefaultNamespace("hud/locator_bar_dot/bowtie");

    private static final float GOLDEN_ANGLE = 137.508f;

    private CrowBarHudRenderer() {
    }

    /** Draws the forced locator background after vanilla's selected contextual-bar background. */
    public static void renderLocatorBarBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;
        boolean hasClaims = !CrowBarState.getClaimWaypoints().isEmpty();
        if (!CrowBarState.isIntegratedServer && !CrowBarState.alliumDataReceived && !hasClaims) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        boolean renderPlayers = CrowBarState.shouldRenderPlayers();
        boolean hasRenderablePlayers = renderPlayers
                && CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUUID());
        if (!LocatorBarVisibility.shouldRenderCrowBar(
                CrowBarState.isXpBarVisible(), hasRenderablePlayers, hasClaims, false, false)) return;

        int barX = (context.guiWidth() - 182) / 2;
        int locatorBarY = context.guiHeight() - 29;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
    }

    /** Draws player and claim markers after vanilla's selected contextual-bar layer. */
    public static void renderLocatorBarMarkers(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;
        boolean hasClaims = !CrowBarState.getClaimWaypoints().isEmpty();
        if (!CrowBarState.isIntegratedServer && !CrowBarState.alliumDataReceived && !hasClaims) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        boolean renderPlayers = CrowBarState.shouldRenderPlayers();
        boolean hasRenderablePlayers = renderPlayers
                && CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUUID());
        if (!LocatorBarVisibility.shouldRenderCrowBar(
                CrowBarState.isXpBarVisible(), hasRenderablePlayers, hasClaims, false, false)) return;

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPosition = camera.position();
        Font textRenderer = client.font;
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int locatorBarY = screenHeight - 29;
        int centerX = screenWidth / 2;

        // First pass: collect all renderable entries with computed positions
        List<EntryRenderData> entries = new ArrayList<>();
        if (renderPlayers) {
            for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
                if (alliumData.isExpired()) continue;
                UUID uuid = alliumData.uuid;
                if (uuid.equals(cameraEntity.getUUID())) continue;

                double deltaX = alliumData.x - cameraPosition.x;
                double deltaZ = alliumData.z - cameraPosition.z;
                double relativeYaw = LocatorProjection.relativeYaw(
                        cameraPosition.x, cameraPosition.z, camera.yaw(), alliumData.x, alliumData.z);

                double deltaY = alliumData.y - cameraPosition.y;
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                boolean markerVisible = isWithinLocatorView(relativeYaw);
                if (!markerVisible) continue;

                Identifier arrowSprite = getArrowSpriteForPosition(camera, alliumData.x, alliumData.y, alliumData.z);

                int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
                int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
                int markerY = locatorBarY - 2;

                int sizeHundredths = calculateSizeFromDistance((float) distance, 9);
                int size = sizeHundredths / 100;
                int dotCenterX = markerX + 4;
                int dotCenterY = markerY + 4;
                Identifier dotSprite = getDotSpriteForDistance(distance);

                entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance, dotSprite, arrowSprite, markerVisible, true, null, null));
            }
        }

        addClaimWaypointEntries(cameraEntity, camera, screenWidth, locatorBarY, entries);

        if (entries.isEmpty()) return;

        // Find the entry closest to screen center
        EntryRenderData closest = entries.stream()
                .filter(EntryRenderData::markerVisible)
                .min(Comparator.comparingInt(e -> Math.abs(e.markerX - centerX)))
                .orElse(null);

        // Draw non-closest entries first with faded text
        for (EntryRenderData entry : entries) {
            if (entry == closest) continue;
            renderAlliumEntry(context, textRenderer, entry, false);
        }

        // Draw closest entry last with full opacity text
        if (closest != null) {
            renderAlliumEntry(context, textRenderer, closest, true);
        }
    }

    /**
     * Adds claims the server published for this player.
     *
     * <p>Independent of the vanilla waypoint system: positions arrive over a plugin channel from the
     * server's claim records, so a claim renders at any distance and whether or not its terrain is
     * loaded. The bowtie is drawn at full size regardless of distance, since a claim marker that
     * degrades into a generic dot is indistinguishable from a player.
     */
    private static void addClaimWaypointEntries(Entity cameraEntity, Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        Vec3 cameraPosition = camera.position();
        for (ClaimWaypointData claim : CrowBarState.getClaimWaypoints()) {
            double deltaX = claim.x() - cameraPosition.x;
            double deltaZ = claim.z() - cameraPosition.z;
            double relativeYaw = LocatorProjection.relativeYaw(
                    cameraPosition.x, cameraPosition.z, camera.yaw(), claim.x(), claim.z());
            if (!isWithinLocatorView(relativeYaw)) continue;

            double deltaY = claim.y() - cameraPosition.y;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            int color = claim.color() != null
                    ? claim.color()
                    : ARGB.setBrightness(ARGB.color(255, claim.id().hashCode()), 0.9F);

            entries.add(new EntryRenderData(null, markerX, markerX + 4, markerY + 4, 9, distance,
                    CLAIM_BOWTIE_SPRITE, getArrowSpriteForPosition(camera, claim.x(), claim.y(), claim.z()),
                    true, false, color, claim.name()));
        }
    }

    private static void renderAlliumEntry(GuiGraphicsExtractor context, Font textRenderer, EntryRenderData entry, boolean isClosest) {
        if (entry.markerVisible) {
            int dotColor = entry.overrideColor != null ? entry.overrideColor : getDotColor(entry.uuid);
            drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite, dotColor);
        }
        if (entry.arrowSprite != null) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite);
        }

        if (entry.markerVisible && LocatorBarVisibility.shouldRenderPlayerSkins(
                entry.isPlayer, CrowBarState.skinsEnabled)) {
            PlayerSkin skin = getSkin(entry.uuid);
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(context, skin, entry.dotCenterX - entry.size / 2, entry.dotCenterY - entry.size / 2, entry.size);
            }
        }

        if (entry.markerVisible && LocatorBarVisibility.shouldRenderLabels(
                CrowBarState.nameTagsEnabled, CrowBarState.showDistance)) {
            String text = "";
            if (CrowBarState.nameTagsEnabled) {
                // Players resolve through the tab list; claims carry their name in the entry.
                String name = entry.isPlayer ? getName(entry.uuid) : entry.label;
                text = name == null ? "" : name;
            }
            if (CrowBarState.showDistance && entry.distance > 0) {
                String distanceText = String.format("%.0fm", entry.distance);
                if (CrowBarState.nameTagsEnabled) {
                    text = text + " " + distanceText;
                } else {
                    text = distanceText;
                }
            }

            int textColor = isClosest ? 0xFFFFFFFF : 0x70FFFFFF;
            int textWidth = textRenderer.width(text);
            int textX = entry.dotCenterX - textWidth / 2;
            int textY = entry.dotCenterY - entry.size / 2 - 12;
            if (LOCATOR_ARROW_UP.equals(entry.arrowSprite)) {
                textY -= LOCATOR_ARROW_HEIGHT + 1;
            }
            context.text(textRenderer, text, textX, textY, textColor);
        }
    }

    /**
     * A single marker on the bar.
     *
     * <p>{@code isPlayer} entries come from a player position source and support skin and name-tag
     * overlays. Non-player entries are claims or vanilla waypoints; they carry their own colour and
     * label and have no skin to look up.
     *
     * @param uuid          {@code null} for claims, which are identified by claim id
     * @param overrideColor pre-resolved ARGB, or {@code null} to derive one from {@code uuid}
     * @param label         text to draw above the marker; players resolve their own from the tab list
     */
    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, Identifier dotSprite, Identifier arrowSprite, boolean markerVisible, boolean isPlayer, Integer overrideColor, String label) {
    }

    private static void drawTintedLocatorDot(GuiGraphicsExtractor context, int centerX, int centerY, Identifier sprite, int color) {
        int halfSize = LOCATOR_DOT_SIZE / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, color);
    }

    private static Identifier getDotSpriteForDistance(double distance) {
        if (distance >= 333.0D) return LOCATOR_DOT_SPRITES[3];
        if (distance >= 231.0D) return LOCATOR_DOT_SPRITES[2];
        if (distance >= 129.0D) return LOCATOR_DOT_SPRITES[1];
        return LOCATOR_DOT_SPRITES[0];
    }

    private static boolean isWithinLocatorView(double relativeYaw) {
        return relativeYaw > LOCATOR_VIEW_MIN_YAW && relativeYaw <= LOCATOR_VIEW_MAX_YAW;
    }

    private static Identifier getArrowSpriteForPosition(Camera camera, double targetX, double targetY, double targetZ) {
        double pitchDelta = getTargetPitchDelta(camera, targetX, targetY, targetZ);
        if (Math.abs(pitchDelta) <= LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE) return null;
        return pitchDelta < 0.0D ? LOCATOR_ARROW_UP : LOCATOR_ARROW_DOWN;
    }

    private static double getTargetPitchDelta(Camera camera, double targetX, double targetY, double targetZ) {
        Vec3 cameraPosition = camera.position();
        double deltaX = targetX - cameraPosition.x;
        double deltaZ = targetZ - cameraPosition.z;
        double targetCenterY = targetY + 1.0D;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(targetCenterY - cameraPosition.y, horizontalDistance));
        return targetPitch - camera.xRot();
    }

    private static void drawVerticalArrow(GuiGraphicsExtractor context, int centerX, int centerY, Identifier sprite) {
        int x = centerX - LOCATOR_ARROW_WIDTH / 2;
        int y = LOCATOR_ARROW_DOWN.equals(sprite)
                ? centerY + LOCATOR_DOT_SIZE / 2 + 1
                : centerY - LOCATOR_DOT_SIZE / 2 - LOCATOR_ARROW_HEIGHT;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_ARROW_WIDTH, LOCATOR_ARROW_HEIGHT, 0xFFFFFFFF);
    }

    private static int getDotColor(UUID uuid) {
        Minecraft client = Minecraft.getInstance();

        // Priority 1: Allium-provided team color (includes fallback logic on server side)
        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            int rgb = alliumData.teamColor & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            return argb;
        }

        // Priority 2: Client-side loaded PlayerEntity team color
        if (client.level != null) {
            Player player = client.level.getPlayerByUUID(uuid);
            if (player != null) {
                int teamColor = getTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    return teamColor;
                }
            }
        }

        // Priority 3: Mojang-ish generated fallback color
        int fallback = getMojangishGeneratedColor(uuid);
        return fallback;
    }

    private static int getMojangishGeneratedColor(UUID uuid) {
        long hash = uuid.hashCode() & 0xffffffffL;
        float hue = (hash * GOLDEN_ANGLE) % 360f;
        float saturation = 0.75f + ((hash >> 8) & 0xFF) / 255f * 0.20f;
        float brightness = 0.85f + ((hash >> 16) & 0xFF) / 255f * 0.15f;
        int rgb = java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness) & 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    private static int getTeamColor(Player player) {
        Team team = player.getTeam();

        if (team == null) {
            return 0xFFFFFFFF;
        }

        return team.getColor()
                .map(TeamColor::rgb)
                .map(rgb -> 0xFF000000 | rgb)
                .orElse(0xFFFFFFFF);
    }

    private static int calculateSizeFromDistance(float distance, int baseSize) {
        // Shared helper for scaling locator dot/skin size based on distance
        // Uses vanilla waypoint style behavior (near/far distance interpolation)
        // Returns size in hundredths (multiply by 0.01f to get pixels)
        if (distance <= 0) return baseSize * 100;

        float nearDistance = 128.0F;
        float farDistance = 256.0F;

        float progress = 1.0F - Mth.clamp(
                (distance - nearDistance) /
                (farDistance - nearDistance),
                0.0F,
                1.0F
        );

        return (int) Mth.lerp(
                progress,
                500,
                baseSize * 100
        );
    }

    private static String getName(UUID uuid) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        PlayerInfo entry = networkHandler == null ? null : networkHandler.getPlayerInfo(uuid);
        if (entry != null) {
            return entry.getProfile().name();
        }
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(uuid)) {
            return Minecraft.getInstance().player.getName().getString();
        }
        return uuid.toString();
    }

    public static void drawSelfView(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int barX = (context.guiWidth() - 182) / 2;
        int barY = context.guiHeight() - 29;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_PROGRESS, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.guiWidth() / 2;
        int iconCenterY = barY + 2;

        // Draw tinted dot for self
        int dotColor = getDotColor(client.player.getUUID());
        drawTintedLocatorDot(context, iconCenterX, iconCenterY, LOCATOR_DOT_SPRITES[0], dotColor);

        // Draw skin on top if enabled
        if (CrowBarState.skinsEnabled) {
            PlayerSkin skin = getSkin(client.player.getUUID());
            if (skin != null) {
                PlayerFaceExtractor.extractRenderState(context, skin, iconCenterX - iconSize / 2, iconCenterY - iconSize / 2, iconSize);
            }
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = client.player.getGameProfile().name();
            int textX = iconCenterX - client.font.width(name) / 2;
            context.text(client.font, name, textX, barY - 10, 0xFFFFFFFF);
        }
    }

    private static PlayerSkin getSkin(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener networkHandler = client.getConnection();
        PlayerInfo entry = networkHandler == null ? null : networkHandler.getPlayerInfo(uuid);
        if (entry != null) {
            return entry.getSkin();
        }
        if (client.player != null && client.player.getUUID().equals(uuid)) {
            return client.player.getSkin();
        }
        return null;
    }
}
