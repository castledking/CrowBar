package codes.castled.crowbar;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.DefaultSkinHelper;

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
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");
    private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.of("crowbar", "hud/locator_bar_background");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_ARROW_WIDTH = 7;
    private static final int LOCATOR_ARROW_HEIGHT = 5;
    private static final double LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE = 35.0D;
    private static final double LOCATOR_VIEW_MIN_YAW = -61.0D;
    private static final double LOCATOR_VIEW_MAX_YAW = 60.0D;
    private static final Identifier LOCATOR_ARROW_UP = Identifier.of("crowbar", "hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_ARROW_DOWN = Identifier.of("crowbar", "hud/locator_bar_arrow_down");
    private static final Identifier[] LOCATOR_DOT_SPRITES = {
            Identifier.of("crowbar", "hud/locator_bar_dot/default_0"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_1"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_2"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_3")
    };
    /** Square frame drawn around a face in the player's dot colour when the skin frame is on. */
    private static final Identifier SKIN_FRAME = Identifier.of("crowbar", "hud/skin_frame");

    /** CrowBar's own copy of the bowtie, since vanilla only ships one from 1.21.6. */
    private static final Identifier CLAIM_BOWTIE_SPRITE = Identifier.of("crowbar", "hud/locator_bar_dot/bowtie");

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
    public static boolean pushHudLift(DrawContext context) {
        float offset = HudLift.INSTANCE.offset();
        if (offset <= 0f) return false;
        context.getMatrices().push();
        context.getMatrices().translate(0f, -offset, 0f);
        return true;
    }

    /** Undoes {@link #pushHudLift}; always returns {@code false} so callers can reset their flag. */
    public static boolean popHudLift(DrawContext context, boolean pushed) {
        if (pushed) {
            context.getMatrices().pop();
        }
        return false;
    }

    /**
     * Draws player and claim markers over the experience bar and queues their labels.
     *
     * <p>Runs after the experience bar and before the experience level, so the level number stays
     * readable on top of the dots, as it does over vanilla's locator bar.
     */
    public static void renderMarkers(DrawContext context, RenderTickCounter tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (client.player.isSpectator()) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        Camera camera = client.gameRenderer.getCamera();
        float tickDelta = tickCounter.getTickProgress(true);
        int screenWidth = context.getScaledWindowWidth();
        int locatorBarY = context.getScaledWindowHeight() - 29;
        int centerX = screenWidth / 2;

        List<EntryRenderData> entries = new ArrayList<>();
        if (CrowBarState.shouldRenderPlayers()) {
            addPlayerEntries(client, cameraEntity, camera, tickDelta, screenWidth, locatorBarY, entries);
        }
        addClaimWaypointEntries(camera, screenWidth, locatorBarY, entries);

        if (entries.isEmpty()) return;

        // Creative has no experience bar, so give the dots the locator bar's track to sit on.
        if (!client.interactionManager.hasExperienceBar() && client.player.getJumpingMount() == null) {
            int barX = (screenWidth - 182) / 2;
            context.drawGuiTexture(RenderLayer::getGuiTextured, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
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
    private static void addPlayerEntries(MinecraftClient client, Entity cameraEntity, Camera camera, float tickDelta, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        UUID self = cameraEntity.getUuid();
        boolean hasAlliumSource = false;
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            hasAlliumSource = true;
            if (alliumData.uuid.equals(self)) continue;
            addEntryForPosition(camera, screenWidth, locatorBarY, entries, alliumData.uuid, alliumData.x, alliumData.y, alliumData.z, alliumData.bearingOnly);
        }
        if (hasAlliumSource) return;

        if (client.getServer() != null) {
            for (ServerPlayerEntity player : client.getServer().getPlayerManager().getPlayerList()) {
                if (player.getUuid().equals(self)) continue;
                if (player.getWorld().getRegistryKey() != client.world.getRegistryKey()) continue;
                if (isHiddenFromLocator(player)) continue;
                // Loaded players move smoothly between ticks; the server copy only updates per tick.
                PlayerEntity loaded = client.world.getPlayerByUuid(player.getUuid());
                Vec3d pos = loaded != null ? loaded.getLerpedPos(tickDelta) : player.getPos();
                addEntryForPosition(camera, screenWidth, locatorBarY, entries, player.getUuid(), pos.x, pos.y, pos.z, false);
            }
            return;
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(self)) continue;
            if (isHiddenFromLocator(player)) continue;
            Vec3d pos = player.getLerpedPos(tickDelta);
            addEntryForPosition(camera, screenWidth, locatorBarY, entries, player.getUuid(), pos.x, pos.y, pos.z, false);
        }
    }

    /** Vanilla 1.21.6's locator rules: spectators, sneaking, invisible and disguised players hide. */
    private static boolean isHiddenFromLocator(PlayerEntity player) {
        return LocatorBarVisibility.isHiddenFromLocator(
                player.isSpectator(),
                player.isSneaking(),
                player.isInvisible(),
                isDisguise(player.getEquippedStack(EquipmentSlot.HEAD)));
    }

    /** A carved pumpkin or any mob or player head. */
    private static boolean isDisguise(ItemStack head) {
        if (head.isOf(Items.CARVED_PUMPKIN)) return true;
        return head.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock;
    }

    /**
     * @param bearingOnly the position only gives a direction, so the distance is reported as unknown
     */
    private static void addEntryForPosition(Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries, UUID uuid, double x, double y, double z, boolean bearingOnly) {
        Vec3d cameraPosition = camera.getPos();
        double relativeYaw = LocatorProjection.relativeYaw(cameraPosition.x, cameraPosition.z, camera.getYaw(), x, z);
        if (!isWithinLocatorView(relativeYaw)) return;

        double deltaX = x - cameraPosition.x;
        double deltaY = y - cameraPosition.y;
        double deltaZ = z - cameraPosition.z;
        double distance = bearingOnly ? -1.0D : Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
        int markerX = baseX + MathHelper.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
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
        Vec3d cameraPosition = camera.getPos();
        for (ClaimWaypointData claim : CrowBarState.getClaimWaypoints()) {
            double relativeYaw = LocatorProjection.relativeYaw(
                    cameraPosition.x, cameraPosition.z, camera.getYaw(), claim.x(), claim.z());
            if (!isWithinLocatorView(relativeYaw)) continue;

            double deltaX = claim.x() - cameraPosition.x;
            double deltaY = claim.y() - cameraPosition.y;
            double deltaZ = claim.z() - cameraPosition.z;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            int color = claim.color() != null
                    ? claim.color()
                    : DotColors.fromHash(claim.id().hashCode());

            entries.add(new EntryRenderData(null, markerX, markerX + 4, markerY + 4, 9, distance,
                    CLAIM_BOWTIE_SPRITE, getArrowSpriteForPosition(camera, claim.x(), claim.y(), claim.z()),
                    false, color, claim.name()));
        }
    }

    private static void renderEntryMarker(DrawContext context, EntryRenderData entry) {
        CrowBarConfig config = CrowBarConfigStore.effective();
        int alpha = MarkerFade.alpha(config, entry.distance);
        int dotColor = entry.overrideColor != null ? entry.overrideColor : getDotColor(entry.uuid);

        SkinTextures skin = LocatorBarVisibility.shouldRenderPlayerSkins(entry.isPlayer, CrowBarState.skinsEnabled)
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
        int width = MinecraftClient.getInstance().textRenderer.getWidth(text);
        QUEUED_LABELS.add(new QueuedLabel(text, centerX, y, width, alpha));
    }

    /** Draws every label queued this frame. Call after the experience level is drawn. */
    public static void drawQueuedLabels(DrawContext context) {
        if (QUEUED_LABELS.isEmpty()) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int screenCenter = context.getScaledWindowWidth() / 2;
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

    private static void drawLabel(DrawContext context, TextRenderer font, QueuedLabel label, boolean focused, CrowBarConfig config) {
        int alpha = label.alpha() * (focused || !config.dimUnfocusedLabels ? 255 : UNFOCUSED_LABEL_ALPHA) / 255;
        // Text rendering treats an alpha below 4 as "no alpha given" and draws it opaque.
        if (alpha < 4) return;

        int x = label.left();
        int y = label.y();
        int color = (alpha << 24) | 0xFFFFFF;
        if (config.labelBackground) {
            context.fill(x - LABEL_PADDING_X, y - LABEL_PADDING_Y,
                    x + label.width() - 1 + LABEL_PADDING_X, y + font.fontHeight - 1 + LABEL_PADDING_Y,
                    (LABEL_BACKGROUND_ALPHA * alpha / 255) << 24);
            context.drawText(font, label.text(), x, y, color, false);
            HudLift.INSTANCE.requestClearance(y - LABEL_PADDING_Y, context.getScaledWindowHeight());
        } else {
            context.drawTextWithShadow(font, label.text(), x, y, color);
            HudLift.INSTANCE.requestClearance(y - 1, context.getScaledWindowHeight());
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
    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, Identifier dotSprite, Identifier arrowSprite, boolean isPlayer, Integer overrideColor, String label) {
    }

    private static void drawSkin(DrawContext context, SkinTextures skin, int centerX, int centerY, int size, int dotColor, int alpha) {
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        int tint = DotColors.withAlpha(0xFFFFFFFF, alpha);
        if (CrowBarConfigStore.effective().skinBorder) {
            drawTintedSprite(context, SKIN_FRAME, x, y, size, size, DotColors.withAlpha(dotColor, alpha));
            PlayerSkinDrawer.draw(context, skin, x + 1, y + 1, size - 2, tint);
        } else {
            PlayerSkinDrawer.draw(context, skin, x, y, size, tint);
        }
    }

    private static void drawTintedSprite(DrawContext context, Identifier sprite, int x, int y, int width, int height, int color) {
        context.drawGuiTexture(RenderLayer::getGuiTextured, sprite, x, y, width, height, color);
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
        Vec3d cameraPosition = camera.getPos();
        double deltaX = targetX - cameraPosition.x;
        double deltaZ = targetZ - cameraPosition.z;
        double targetCenterY = targetY + 1.0D;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(targetCenterY - cameraPosition.y, horizontalDistance));
        return targetPitch - camera.getPitch();
    }

    private static void drawVerticalArrow(DrawContext context, int centerX, int centerY, Identifier sprite, int alpha) {
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                int teamColor = getScoreboardTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    return teamColor;
                }
            }
        }
        return null;
    }

    private static int getScoreboardTeamColor(PlayerEntity player) {
        Team team = player.getScoreboardTeam();
        if (team == null) {
            return 0xFFFFFFFF;
        }

        Formatting color = team.getColor();
        if (color == null) {
            return 0xFFFFFFFF;
        }

        Integer colorValue = color.getColorValue();
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

        float progress = 1.0F - MathHelper.clamp(
                (distance - nearDistance) /
                (farDistance - nearDistance),
                0.0F,
                1.0F
        );

        return (int) MathHelper.lerp(
                progress,
                500,
                baseSize * 100
        );
    }

    private static String getName(UUID uuid) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = networkHandler == null ? null : networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            return entry.getProfile().getName();
        }
        AlliumPlayerData positioned = CrowBarState.alliumPlayerData.get(uuid);
        if (positioned != null && positioned.name != null) {
            return positioned.name;
        }
        return uuid.toString();
    }

    public static void drawSelfView(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        int barX = (context.getScaledWindowWidth() - 182) / 2;
        int barY = context.getScaledWindowHeight() - 29;
        context.drawGuiTexture(RenderLayer::getGuiTextured, EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.drawGuiTexture(RenderLayer::getGuiTextured, EXPERIENCE_PROGRESS, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.getScaledWindowWidth() / 2;
        int iconCenterY = barY + 2;
        UUID uuid = client.player.getUuid();
        int dotColor = DotColors.forSelf(CrowBarConfigStore.effective(), uuid, getTeamColor(uuid));

        SkinTextures skin = CrowBarState.skinsEnabled ? getSkin(uuid) : null;
        if (skin == null || !CrowBarConfigStore.effective().skinBorder) {
            drawTintedSprite(context, LOCATOR_DOT_SPRITES[0], iconCenterX - LOCATOR_DOT_SIZE / 2, iconCenterY - LOCATOR_DOT_SIZE / 2,
                    LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, dotColor);
        }
        if (skin != null) {
            drawSkin(context, skin, iconCenterX, iconCenterY, iconSize, dotColor, 255);
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = client.player.getGameProfile().getName();
            int textX = iconCenterX - client.textRenderer.getWidth(name) / 2;
            context.drawTextWithShadow(client.textRenderer, name, textX, barY - 10, 0xFFFFFFFF);
        }
    }

    private static SkinTextures getSkin(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        PlayerListEntry entry = networkHandler == null ? null : networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            return entry.getSkinTextures();
        }
        if (client.player != null && client.player.getUuid().equals(uuid)) {
            return client.player.getSkinTextures();
        }
        AlliumPlayerData positioned = CrowBarState.alliumPlayerData.get(uuid);
        if (positioned != null && positioned.name != null) {
            // Named by the server but not in the tab list, like the plugin's fake markers.
            return DefaultSkinHelper.getSkinTextures(uuid);
        }
        return null;
    }
}
