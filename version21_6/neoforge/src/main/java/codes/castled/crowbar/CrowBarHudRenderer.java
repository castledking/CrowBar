package codes.castled.crowbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.scores.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CrowBarHudRenderer {
    private static final ResourceLocation EXPERIENCE_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation EXPERIENCE_PROGRESS = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final ResourceLocation LOCATOR_BAR_BACKGROUND = ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_background");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_ARROW_WIDTH = 7;
    private static final int LOCATOR_ARROW_HEIGHT = 5;
    private static final double LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE = 35.0D;
    private static final double LOCATOR_VIEW_MIN_YAW = -61.0D;
    private static final double LOCATOR_VIEW_MAX_YAW = 60.0D;
    private static final ResourceLocation LOCATOR_ARROW_UP = ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_up");
    private static final ResourceLocation LOCATOR_ARROW_DOWN = ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_down");
    private static final ResourceLocation[] LOCATOR_DOT_SPRITES = {
            ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_0"),
            ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_1"),
            ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_2"),
            ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_3")
    };
    /** Square frame drawn around a face in the player's dot colour when the skin frame is on. */
    private static final ResourceLocation SKIN_FRAME = ResourceLocation.fromNamespaceAndPath("crowbar", "hud/skin_frame");

    /** Vanilla's bowtie sprite, so a server resource pack can restyle it for everyone at once. */
    private static final ResourceLocation CLAIM_BOWTIE_SPRITE = ResourceLocation.withDefaultNamespace("hud/locator_bar_dot/bowtie");

    private static final int LABEL_PADDING_X = 4;
    private static final int LABEL_PADDING_Y = 2;
    private static final int LABEL_BACKGROUND_ALPHA = 0xC0;
    private static final int UNFOCUSED_LABEL_ALPHA = 0x70;

    /**
     * Labels from this frame's marker passes. Both CrowBar's own markers and the overlay on
     * vanilla's dots queue here, so overlap culling and the focused label span every marker.
     */
    private static final List<QueuedLabel> QUEUED_LABELS = new ArrayList<>();

    private CrowBarHudRenderer() {
    }

    /** Starts a HUD frame: advances the HUD lift and drops labels a skipped frame left behind. */
    public static void beginFrame() {
        QUEUED_LABELS.clear();
        HudLift.INSTANCE.beginFrame(System.nanoTime(), CrowBarConfigStore.effective().raiseHud);
    }

    /**
     * Moves what is drawn next up by the current HUD lift.
     *
     * @return whether a matrix was pushed, to hand back to {@link #popHudLift}
     */
    public static boolean pushHudLift(GuiGraphics context) {
        float offset = HudLift.INSTANCE.offset();
        if (offset <= 0f) return false;
        context.pose().pushMatrix();
        context.pose().translate(0f, -offset);
        return true;
    }

    /** Undoes {@link #pushHudLift}; always returns {@code false} so callers can reset their flag. */
    public static boolean popHudLift(GuiGraphics context, boolean pushed) {
        if (pushed) {
            context.pose().popMatrix();
        }
        return false;
    }

    /**
     * Whether CrowBar has anything to put in the locator bar's slot this frame: its own players or
     * claims, or vanilla waypoints it redraws.
     */
    public static boolean hasLocatorContent() {
        if (CrowBarState.viewSelfEnabled) return false;
        if (CrowBarState.isExternalRenderSuppressed()) return false;
        boolean hasClaims = !CrowBarState.getClaimWaypoints().isEmpty();
        if (!CrowBarState.isIntegratedServer && !CrowBarState.alliumDataReceived && !hasClaims) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return false;

        boolean renderPlayers = CrowBarState.shouldRenderPlayers();
        boolean hasRenderablePlayers = renderPlayers
                && CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUUID());
        return LocatorBarVisibility.shouldRenderCrowBar(
                CrowBarState.isXpBarVisible(), hasRenderablePlayers, hasClaims, false, false);
    }

    /**
     * Whether vanilla's locator bar would come out blank. Vanilla picks the locator bar whenever the
     * server has sent any waypoint, but once CrowBar has taken the bar over it may hide every one of
     * them (claim markers, or players the server's locator leaves out) and then draw nothing itself,
     * which left the slot with neither markers nor the experience bar.
     */
    public static boolean shouldSkipLocatorBar() {
        return !CrowBarState.viewSelfEnabled
                && !CrowBarState.isExternalRenderSuppressed()
                && CrowBarState.shouldCancelVanillaLocatorBar()
                && !hasLocatorContent();
    }

    /** The bar vanilla shows when there are no waypoints, named as in its bar type enum. */
    public static String locatorBarFallback() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.jumpableVehicle() != null) return "JUMPABLE_VEHICLE";
        return client.gameMode != null && client.gameMode.hasExperience() ? "EXPERIENCE" : "EMPTY";
    }

    /** Draws the forced locator background after vanilla's selected contextual-bar background. */
    public static void renderLocatorBarBackground(GuiGraphics context, DeltaTracker tickCounter) {
        if (!hasLocatorContent()) return;

        int barX = (context.guiWidth() - 182) / 2;
        int locatorBarY = context.guiHeight() - 29;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
    }

    /** Draws player and claim markers after vanilla's selected contextual-bar addon layer. */
    public static void renderLocatorBarMarkers(GuiGraphics context, DeltaTracker tickCounter) {
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

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
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
                // A bearing-only position sits at a nominal distance; -1 marks it unknown.
                double distance = alliumData.bearingOnly ? -1.0D : Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                boolean markerVisible = isWithinLocatorView(relativeYaw);
                if (!markerVisible) continue;

                ResourceLocation arrowSprite = getArrowSpriteForPosition(camera, alliumData.x, alliumData.y, alliumData.z);

                int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
                int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
                int markerY = locatorBarY - 2;

                int sizeHundredths = calculateSizeFromDistance(distance < 0 ? Float.MAX_VALUE : (float) distance, 9);
                int size = sizeHundredths / 100;
                int dotCenterX = markerX + 4;
                int dotCenterY = markerY + 4;
                ResourceLocation dotSprite = getDotSpriteForDistance(distance < 0 ? Double.MAX_VALUE : distance);

                entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance, dotSprite, arrowSprite, markerVisible, true, null, null));
            }
        }

        addClaimWaypointEntries(cameraEntity, camera, screenWidth, locatorBarY, entries);

        if (entries.isEmpty()) return;

        // The entry nearest the centre is drawn last so its dot and face sit on top.
        EntryRenderData closest = entries.stream()
                .filter(EntryRenderData::markerVisible)
                .min(Comparator.comparingInt(e -> Math.abs(e.markerX - centerX)))
                .orElse(null);

        for (EntryRenderData entry : entries) {
            if (entry == closest) continue;
            renderEntryMarker(context, entry);
        }
        if (closest != null) {
            renderEntryMarker(context, closest);
        }

        for (EntryRenderData entry : entries) {
            queueEntryLabel(entry);
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

    private static void renderEntryMarker(GuiGraphics context, EntryRenderData entry) {
        if (!entry.markerVisible) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        int alpha = MarkerFade.alpha(config, entry.distance);
        int dotColor = entry.overrideColor != null ? entry.overrideColor : getDotColor(entry.uuid);

        PlayerSkin skin = LocatorBarVisibility.shouldRenderPlayerSkins(entry.isPlayer, CrowBarState.skinsEnabled)
                ? getSkin(entry.uuid)
                : null;
        // A framed face stands in for the dot, so drawing the dot underneath would only show
        // through as a smudge around the frame.
        if (skin == null || !config.skinBorder) {
            drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite, DotColors.withAlpha(dotColor, alpha));
        }
        if (entry.arrowSprite != null && config.heightArrows) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite, alpha);
        }
        if (skin != null) {
            drawSkin(context, skin, entry.dotCenterX, entry.dotCenterY, entry.size, dotColor, alpha);
        }
    }

    private static void queueEntryLabel(EntryRenderData entry) {
        if (!entry.markerVisible) return;
        // Players resolve through the tab list; claims carry their name in the entry.
        String name = !CrowBarState.nameTagsEnabled ? null : entry.isPlayer ? getName(entry.uuid) : entry.label;
        int textY = entry.dotCenterY - entry.size / 2 - 12;
        if (LOCATOR_ARROW_UP.equals(entry.arrowSprite) && CrowBarConfigStore.effective().heightArrows) {
            textY -= LOCATOR_ARROW_HEIGHT + 1;
        }
        queueLabel(labelText(name, entry.distance), entry.dotCenterX, textY,
                MarkerFade.alpha(CrowBarConfigStore.effective(), entry.distance));
    }

    /**
     * Builds a marker label from the enabled parts.
     *
     * @param name     the name to show, or {@code null} when name tags are off or there is none
     * @param distance blocks to the target, or negative when the server sent a bearing only
     */
    public static String labelText(String name, double distance) {
        String text = name == null ? "" : name;
        if (CrowBarState.showDistance) {
            // A bearing-only waypoint has no distance to report, so say so rather than dropping the
            // label entirely and leaving the toggle looking dead.
            String distanceText = distance >= 0 ? String.format("%.0fm", distance) : "?m";
            text = text.isEmpty() ? distanceText : text + " " + distanceText;
        }
        return text;
    }

    /** Queues a label whose text starts at {@code y}, centred on {@code centerX}. */
    public static void queueLabel(String text, int centerX, int y, int alpha) {
        if (text == null || text.isEmpty()) return;
        int width = Minecraft.getInstance().font.width(text);
        QUEUED_LABELS.add(new QueuedLabel(text, centerX, y, width, alpha));
    }

    /** Draws every label queued this frame, after all markers so no dot or face covers one. */
    public static void drawQueuedLabels(GuiGraphics context) {
        if (QUEUED_LABELS.isEmpty()) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        Font font = Minecraft.getInstance().font;
        int screenCenter = context.guiWidth() / 2;
        int padding = config.labelBackground ? LABEL_PADDING_X : 0;

        List<QueuedLabel> labels = config.hideOverlappingLabels
                ? LabelLayout.withoutOverlaps(QUEUED_LABELS,
                        label -> label.left() - padding,
                        label -> label.left() + label.width() + padding,
                        label -> Math.abs(label.centerX() - screenCenter))
                : List.copyOf(QUEUED_LABELS);
        QUEUED_LABELS.clear();

        QueuedLabel focused = labels.stream()
                .min(Comparator.comparingInt(label -> Math.abs(label.centerX() - screenCenter)))
                .orElse(null);
        for (QueuedLabel label : labels) {
            if (label != focused) drawLabel(context, font, label, false, config);
        }
        if (focused != null) {
            drawLabel(context, font, focused, true, config);
        }
    }

    private static void drawLabel(GuiGraphics context, Font font, QueuedLabel label, boolean focused, CrowBarConfig config) {
        int alpha = label.alpha() * (focused || !config.dimUnfocusedLabels ? 255 : UNFOCUSED_LABEL_ALPHA) / 255;
        // Text rendering treats an alpha below 4 as "no alpha given" and draws it opaque.
        if (alpha < 4) return;

        int x = label.left();
        int y = label.y();
        int color = (alpha << 24) | 0xFFFFFF;
        if (config.labelBackground) {
            context.fill(x - LABEL_PADDING_X, y - LABEL_PADDING_Y,
                    x + label.width() - 1 + LABEL_PADDING_X, y + font.lineHeight - 1 + LABEL_PADDING_Y,
                    (LABEL_BACKGROUND_ALPHA * alpha / 255) << 24);
            context.drawString(font, label.text(), x, y, color, false);
            HudLift.INSTANCE.requestClearance(y - LABEL_PADDING_Y, context.guiHeight());
        } else {
            context.drawString(font, label.text(), x, y, color);
            HudLift.INSTANCE.requestClearance(y - 1, context.guiHeight());
        }
    }

    private record QueuedLabel(String text, int centerX, int y, int width, int alpha) {
        int left() {
            return centerX - width / 2;
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
    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, ResourceLocation dotSprite, ResourceLocation arrowSprite, boolean markerVisible, boolean isPlayer, Integer overrideColor, String label) {
    }

    /**
     * Draws a player's face over a dot vanilla has already drawn, plus the frame and repainted dot
     * the appearance settings ask for.
     *
     * @param vanillaColor  the colour vanilla gave the dot, which Team colouring keeps
     * @param vanillaSprite the sprite vanilla drew, so a repainted dot covers it exactly
     */
    public static void renderVanillaPlayerOverlay(GuiGraphics context, UUID uuid, int centerX, int centerY, int size, double distance, int vanillaColor, ResourceLocation vanillaSprite) {
        CrowBarConfig config = CrowBarConfigStore.effective();
        int alpha = MarkerFade.alpha(config, distance);
        int color = config.dotColorMode == DotColorMode.TEAM
                ? vanillaColor
                : DotColors.forPlayer(config, uuid, null);
        PlayerSkin skin = CrowBarState.skinsEnabled ? getSkin(uuid) : null;

        if (config.dotColorMode != DotColorMode.TEAM && (skin == null || !config.skinBorder)) {
            drawTintedLocatorDot(context, centerX, centerY, vanillaSprite, color);
        }
        if (skin != null) {
            drawSkin(context, skin, centerX, centerY, size, color, alpha);
        }
    }

    private static void drawSkin(GuiGraphics context, PlayerSkin skin, int centerX, int centerY, int size, int dotColor, int alpha) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        int tint = DotColors.withAlpha(0xFFFFFFFF, alpha);
        if (CrowBarConfigStore.effective().skinBorder) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, SKIN_FRAME, x, y, size, size, DotColors.withAlpha(dotColor, alpha));
            PlayerFaceRenderer.draw(context, skin, x + 1, y + 1, size - 2, tint);
        } else {
            PlayerFaceRenderer.draw(context, skin, x, y, size, tint);
        }
    }

    private static void drawTintedLocatorDot(GuiGraphics context, int centerX, int centerY, ResourceLocation sprite, int color) {
        int halfSize = LOCATOR_DOT_SIZE / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, color);
    }

    private static ResourceLocation getDotSpriteForDistance(double distance) {
        if (distance >= 333.0D) return LOCATOR_DOT_SPRITES[3];
        if (distance >= 231.0D) return LOCATOR_DOT_SPRITES[2];
        if (distance >= 129.0D) return LOCATOR_DOT_SPRITES[1];
        return LOCATOR_DOT_SPRITES[0];
    }

    private static boolean isWithinLocatorView(double relativeYaw) {
        return relativeYaw > LOCATOR_VIEW_MIN_YAW && relativeYaw <= LOCATOR_VIEW_MAX_YAW;
    }

    private static ResourceLocation getArrowSpriteForPosition(Camera camera, double targetX, double targetY, double targetZ) {
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
        return targetPitch - camera.getXRot();
    }

    private static void drawVerticalArrow(GuiGraphics context, int centerX, int centerY, ResourceLocation sprite, int alpha) {
        int x = centerX - LOCATOR_ARROW_WIDTH / 2;
        int y = LOCATOR_ARROW_DOWN.equals(sprite)
                ? centerY + LOCATOR_DOT_SIZE / 2 + 1
                : centerY - LOCATOR_DOT_SIZE / 2 - LOCATOR_ARROW_HEIGHT;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_ARROW_WIDTH, LOCATOR_ARROW_HEIGHT, DotColors.withAlpha(0xFFFFFFFF, alpha));
    }

    private static int getDotColor(UUID uuid) {
        return DotColors.forPlayer(CrowBarConfigStore.effective(), uuid, getTeamColor(uuid));
    }

    /**
     * The player's team colour as ARGB, or {@code null} without one.
     *
     * <p>Allium's colour wins because the server applies its own fallback rules; otherwise a loaded
     * player's scoreboard team counts, except plain white, which is what "no colour" looks like.
     */
    private static Integer getTeamColor(UUID uuid) {
        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            return 0xFF000000 | (alliumData.teamColor & 0xFFFFFF);
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Player player = client.level.getPlayerByUUID(uuid);
            if (player != null) {
                int teamColor = getScoreboardTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    return teamColor;
                }
            }
        }
        return null;
    }

    private static int getScoreboardTeamColor(Player player) {
        Team team = player.getTeam();

        if (team == null) {
            return 0xFFFFFFFF;
        }

        ChatFormatting color = team.getColor();

        if (color == null) {
            return 0xFFFFFFFF;
        }

        Integer colorValue = color.getColor();
        if (colorValue == null) {
            return 0xFFFFFFFF;
        }

        return 0xFF000000 | colorValue;
    }

    private static int calculateSizeFromDistance(float distance, int baseSize) {
        // Shared helper for scaling locator dot/skin size based on distance
        // Uses vanilla waypoint style behavior (near/far distance interpolation)
        // Returns size in hundredths (multiply by 0.01f to get pixels)
        // Note: WaypointStyle API not available in 1.21.11, using fallback constants
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
            return entry.getProfile().getName();
        }
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(uuid)) {
            return Minecraft.getInstance().player.getName().getString();
        }
        AlliumPlayerData positioned = CrowBarState.alliumPlayerData.get(uuid);
        if (positioned != null && positioned.name != null) {
            return positioned.name;
        }
        return uuid.toString();
    }

    public static void drawSelfView(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int barX = (context.guiWidth() - 182) / 2;
        int barY = context.guiHeight() - 29;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_PROGRESS, 182, 5, 0, 0, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.guiWidth() / 2;
        int iconCenterY = barY + 2;
        UUID uuid = client.player.getUUID();
        int dotColor = DotColors.forSelf(CrowBarConfigStore.effective(), uuid, getTeamColor(uuid));

        PlayerSkin skin = CrowBarState.skinsEnabled ? getSkin(uuid) : null;
        if (skin == null || !CrowBarConfigStore.effective().skinBorder) {
            drawTintedLocatorDot(context, iconCenterX, iconCenterY, LOCATOR_DOT_SPRITES[0], dotColor);
        }
        if (skin != null) {
            drawSkin(context, skin, iconCenterX, iconCenterY, iconSize, dotColor, 255);
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = client.player.getGameProfile().getName();
            int textX = iconCenterX - client.font.width(name) / 2;
            context.drawString(client.font, name, textX, barY - 10, 0xFFFFFFFF);
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
        AlliumPlayerData positioned = CrowBarState.alliumPlayerData.get(uuid);
        if (positioned != null && positioned.name != null) {
            // Named by the server but not in the tab list, like the plugin's fake markers.
            return DefaultPlayerSkin.get(uuid);
        }
        return null;
    }
}
