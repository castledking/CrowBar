package codes.castled.crowbar;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Team;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.resources.DefaultPlayerSkin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Draws CrowBar's markers over the experience bar.
 *
 * <p>Minecraft before 1.21.6 has no locator bar, so there are no vanilla waypoints to read or
 * restyle. Players come from Allium when the server has it, from the integrated server in
 * singleplayer and on a LAN host, and otherwise from the players this client already has loaded.
 * The last source only reaches as far as the server tracks players, and it hides players the same
 * way vanilla's locator bar does from 1.21.6 on.
 */
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

    /** CrowBar's own copy of the bowtie, since vanilla only ships one from 1.21.6. */
    private static final ResourceLocation CLAIM_BOWTIE_SPRITE = ResourceLocation.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/bowtie");

    private static final int LABEL_PADDING_X = 4;
    private static final int LABEL_PADDING_Y = 2;
    private static final int LABEL_BACKGROUND_ALPHA = 0xC0;
    private static final int UNFOCUSED_LABEL_ALPHA = 0x70;

    /** Labels from this frame's marker pass, drawn after the experience level so it cannot cover them. */
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
        context.pose().pushPose();
        context.pose().translate(0f, -offset, 0f);
        return true;
    }

    /** Undoes {@link #pushHudLift}; always returns {@code false} so callers can reset their flag. */
    public static boolean popHudLift(GuiGraphics context, boolean pushed) {
        if (pushed) {
            context.pose().popPose();
        }
        return false;
    }

    /**
     * Draws player and claim markers over the experience bar and queues their labels.
     *
     * <p>Runs after the experience bar and before the experience level, so the level number stays
     * readable on top of the dots, as it does over vanilla's locator bar.
     */
    public static void renderMarkers(GuiGraphics context, DeltaTracker tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (client.player.isSpectator()) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
        int screenWidth = context.guiWidth();
        int locatorBarY = context.guiHeight() - 29;
        int centerX = screenWidth / 2;

        List<EntryRenderData> entries = new ArrayList<>();
        if (CrowBarState.shouldRenderPlayers()) {
            addPlayerEntries(client, cameraEntity, camera, tickDelta, screenWidth, locatorBarY, entries);
        }
        addClaimWaypointEntries(camera, screenWidth, locatorBarY, entries);

        if (entries.isEmpty()) return;

        // Creative has no experience bar, so give the dots the locator bar's track to sit on.
        if (!client.gameMode.hasExperience() && client.player.jumpableVehicle() == null) {
            int barX = (screenWidth - 182) / 2;
            context.blitSprite(LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
        }

        // The entry nearest the centre is drawn last so its dot and face sit on top.
        EntryRenderData closest = entries.stream()
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
     * Adds every player from the best available source.
     *
     * <p>Allium wins outright: it sends every player on the server, already filtered by the server's
     * own visibility rules. Otherwise the integrated server's player list reaches every player in
     * singleplayer and on a LAN host, and on a plain server the loaded players are all there is.
     */
    private static void addPlayerEntries(Minecraft client, Entity cameraEntity, Camera camera, float tickDelta, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        UUID self = cameraEntity.getUUID();
        boolean hasAlliumSource = false;
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            hasAlliumSource = true;
            if (alliumData.uuid.equals(self)) continue;
            addEntryForPosition(camera, screenWidth, locatorBarY, entries, alliumData.uuid, alliumData.x, alliumData.y, alliumData.z, alliumData.bearingOnly);
        }
        if (hasAlliumSource) return;

        if (client.getSingleplayerServer() != null) {
            for (ServerPlayer player : client.getSingleplayerServer().getPlayerList().getPlayers()) {
                if (player.getUUID().equals(self)) continue;
                if (player.level().dimension() != client.level.dimension()) continue;
                if (isHiddenFromLocator(player)) continue;
                // Loaded players move smoothly between ticks; the server copy only updates per tick.
                Player loaded = client.level.getPlayerByUUID(player.getUUID());
                Vec3 pos = loaded != null ? loaded.getPosition(tickDelta) : player.position();
                addEntryForPosition(camera, screenWidth, locatorBarY, entries, player.getUUID(), pos.x, pos.y, pos.z, false);
            }
            return;
        }

        for (AbstractClientPlayer player : client.level.players()) {
            if (player.getUUID().equals(self)) continue;
            if (isHiddenFromLocator(player)) continue;
            Vec3 pos = player.getPosition(tickDelta);
            addEntryForPosition(camera, screenWidth, locatorBarY, entries, player.getUUID(), pos.x, pos.y, pos.z, false);
        }
    }

    /** Vanilla 1.21.6's locator rules: spectators, sneaking, invisible and disguised players hide. */
    private static boolean isHiddenFromLocator(Player player) {
        return LocatorBarVisibility.isHiddenFromLocator(
                player.isSpectator(),
                player.isShiftKeyDown(),
                player.isInvisible(),
                isDisguise(player.getItemBySlot(EquipmentSlot.HEAD)));
    }

    /** A carved pumpkin or any mob or player head. */
    private static boolean isDisguise(ItemStack head) {
        if (head.is(Items.CARVED_PUMPKIN)) return true;
        return head.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock;
    }

    /**
     * @param bearingOnly the position only gives a direction, so the distance is reported as unknown
     */
    private static void addEntryForPosition(Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries, UUID uuid, double x, double y, double z, boolean bearingOnly) {
        Vec3 cameraPosition = camera.getPosition();
        double relativeYaw = LocatorProjection.relativeYaw(cameraPosition.x, cameraPosition.z, camera.getYRot(), x, z);
        if (!isWithinLocatorView(relativeYaw)) return;

        double deltaX = x - cameraPosition.x;
        double deltaY = y - cameraPosition.y;
        double deltaZ = z - cameraPosition.z;
        double distance = bearingOnly ? -1.0D : Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
        int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
        int markerY = locatorBarY - 2;
        int size = calculateSizeFromDistance(distance < 0 ? Float.MAX_VALUE : (float) distance, 9) / 100;

        entries.add(new EntryRenderData(uuid, markerX, markerX + 4, markerY + 4, size, distance,
                getDotSpriteForDistance(distance < 0 ? Double.MAX_VALUE : distance), getArrowSpriteForPosition(camera, x, y, z), true, null, null));
    }

    /**
     * Adds claims the server published for this player.
     *
     * <p>Positions arrive over a plugin channel from the server's claim records, so a claim renders
     * at any distance and whether or not its terrain is loaded. The bowtie is drawn at full size
     * regardless of distance, since a claim marker that degrades into a generic dot is
     * indistinguishable from a player.
     */
    private static void addClaimWaypointEntries(Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        Vec3 cameraPosition = camera.getPosition();
        for (ClaimWaypointData claim : CrowBarState.getClaimWaypoints()) {
            double relativeYaw = LocatorProjection.relativeYaw(
                    cameraPosition.x, cameraPosition.z, camera.getYRot(), claim.x(), claim.z());
            if (!isWithinLocatorView(relativeYaw)) continue;

            double deltaX = claim.x() - cameraPosition.x;
            double deltaY = claim.y() - cameraPosition.y;
            double deltaZ = claim.z() - cameraPosition.z;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            int color = claim.color() != null
                    ? claim.color()
                    : DotColors.fromHash(claim.id().hashCode());

            entries.add(new EntryRenderData(null, markerX, markerX + 4, markerY + 4, 9, distance,
                    CLAIM_BOWTIE_SPRITE, getArrowSpriteForPosition(camera, claim.x(), claim.y(), claim.z()),
                    false, color, claim.name()));
        }
    }

    private static void renderEntryMarker(GuiGraphics context, EntryRenderData entry) {
        CrowBarConfig config = CrowBarConfigStore.effective();
        int alpha = MarkerFade.alpha(config, entry.distance);
        int dotColor = entry.overrideColor != null ? entry.overrideColor : getDotColor(entry.uuid);

        PlayerSkin skin = LocatorBarVisibility.shouldRenderPlayerSkins(entry.isPlayer, CrowBarState.skinsEnabled)
                ? getSkin(entry.uuid)
                : null;
        // A framed face stands in for the dot, so drawing the dot underneath would only show
        // through as a smudge around the frame.
        if (skin == null || !config.skinBorder) {
            drawTintedSprite(context, entry.dotSprite, entry.dotCenterX - LOCATOR_DOT_SIZE / 2, entry.dotCenterY - LOCATOR_DOT_SIZE / 2,
                    LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, DotColors.withAlpha(dotColor, alpha));
        }
        if (entry.arrowSprite != null && config.heightArrows) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite, alpha);
        }
        if (skin != null) {
            drawSkin(context, skin, entry.dotCenterX, entry.dotCenterY, entry.size, dotColor, alpha);
        }
    }

    private static void queueEntryLabel(EntryRenderData entry) {
        CrowBarConfig config = CrowBarConfigStore.effective();
        // Players resolve through the tab list; claims carry their name in the entry.
        String name = !CrowBarState.nameTagsEnabled ? null : entry.isPlayer ? getName(entry.uuid) : entry.label;
        int textY = entry.dotCenterY - entry.size / 2 - 12;
        if (LOCATOR_ARROW_UP.equals(entry.arrowSprite) && config.heightArrows) {
            textY -= LOCATOR_ARROW_HEIGHT + 1;
        }
        queueLabel(labelText(name, entry.distance), entry.dotCenterX, textY, MarkerFade.alpha(config, entry.distance));
    }

    /**
     * Builds a marker label from the enabled parts.
     *
     * @param name     the name to show, or {@code null} when name tags are off or there is none
     * @param distance blocks to the target, or negative when the server sent a bearing only
     */
    private static String labelText(String name, double distance) {
        String text = name == null ? "" : name;
        if (CrowBarState.showDistance) {
            String distanceText = distance >= 0 ? String.format("%.0fm", distance) : "?m";
            text = text.isEmpty() ? distanceText : text + " " + distanceText;
        }
        return text;
    }

    private static void queueLabel(String text, int centerX, int y, int alpha) {
        if (text == null || text.isEmpty()) return;
        int width = Minecraft.getInstance().font.width(text);
        QUEUED_LABELS.add(new QueuedLabel(text, centerX, y, width, alpha));
    }

    /** Draws every label queued this frame. Call after the experience level is drawn. */
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
     * @param uuid          {@code null} for claims, which are identified by claim id
     * @param overrideColor pre-resolved ARGB, or {@code null} to derive one from {@code uuid}
     * @param label         text to draw above the marker; players resolve their own from the tab list
     */
    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, ResourceLocation dotSprite, ResourceLocation arrowSprite, boolean isPlayer, Integer overrideColor, String label) {
    }

    private static void drawSkin(GuiGraphics context, PlayerSkin skin, int centerX, int centerY, int size, int dotColor, int alpha) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        if (CrowBarConfigStore.effective().skinBorder) {
            drawTintedSprite(context, SKIN_FRAME, x, y, size, size, DotColors.withAlpha(dotColor, alpha));
            x++;
            y++;
            size -= 2;
        }
        // Faces have no tint argument before 1.21.2, so fading goes through the shader colour.
        setShaderColor(context, DotColors.withAlpha(0xFFFFFFFF, alpha));
        PlayerFaceRenderer.draw(context, skin, x, y, size);
        context.setColor(1f, 1f, 1f, 1f);
    }

    /** Sprites take their tint from the shader colour before 1.21.2 rather than from an argument. */
    private static void drawTintedSprite(GuiGraphics context, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        setShaderColor(context, color);
        context.blitSprite(sprite, x, y, width, height);
        context.setColor(1f, 1f, 1f, 1f);
    }

    private static void setShaderColor(GuiGraphics context, int argb) {
        RenderSystem.enableBlend();
        context.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
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
        Vec3 cameraPosition = camera.getPosition();
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
        drawTintedSprite(context, sprite, x, y, LOCATOR_ARROW_WIDTH, LOCATOR_ARROW_HEIGHT, DotColors.withAlpha(0xFFFFFFFF, alpha));
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

    /** Dot and face size in hundredths of a pixel, shrinking with distance like vanilla's dots. */
    private static int calculateSizeFromDistance(float distance, int baseSize) {
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
        context.blitSprite(EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.blitSprite(EXPERIENCE_PROGRESS, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.guiWidth() / 2;
        int iconCenterY = barY + 2;
        UUID uuid = client.player.getUUID();
        int dotColor = DotColors.forSelf(CrowBarConfigStore.effective(), uuid, getTeamColor(uuid));

        PlayerSkin skin = CrowBarState.skinsEnabled ? getSkin(uuid) : null;
        if (skin == null || !CrowBarConfigStore.effective().skinBorder) {
            drawTintedSprite(context, LOCATOR_DOT_SPRITES[0], iconCenterX - LOCATOR_DOT_SIZE / 2, iconCenterY - LOCATOR_DOT_SIZE / 2,
                    LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, dotColor);
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
