package codes.castled.crowbar;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import net.minecraft.client.resources.DefaultPlayerSkin;

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

    /** Vanilla's bowtie sprite, so a server resource pack can restyle it for everyone at once. */
    private static final Identifier CLAIM_BOWTIE_SPRITE =
            Identifier.withDefaultNamespace("hud/locator_bar_dot/bowtie");

    /** Square frame drawn around a face in the player's dot colour when the skin frame is on. */
    private static final Identifier SKIN_FRAME = Identifier.fromNamespaceAndPath("crowbar", "hud/skin_frame");

    private static final int LABEL_PADDING_X = 4;
    private static final int LABEL_PADDING_Y = 2;
    private static final int LABEL_BACKGROUND_ALPHA = 0xC0;
    private static final int UNFOCUSED_LABEL_ALPHA = 0x70;

    /** Labels from this frame's marker pass, drawn together so overlap culling spans every marker. */
    private static final List<QueuedLabel> QUEUED_LABELS = new ArrayList<>();

    private CrowBarHudRenderer() {
    }

    /** Starts a HUD frame: advances the HUD lift and drops labels a skipped frame left behind. */
    public static void beginFrame() {
        QUEUED_LABELS.clear();
        HudLift.INSTANCE.beginFrame(System.nanoTime(), CrowBarConfigStore.effective().raiseHud);
    }

    /**
     * Moves what is extracted next up by the current HUD lift.
     *
     * @return whether a matrix was pushed, to hand back to {@link #popHudLift}
     */
    public static boolean pushHudLift(GuiGraphicsExtractor context) {
        float offset = HudLift.INSTANCE.offset();
        if (offset <= 0f) return false;
        context.pose().pushMatrix();
        context.pose().translate(0f, -offset);
        return true;
    }

    /** Undoes {@link #pushHudLift}; always returns {@code false} so callers can reset their flag. */
    public static boolean popHudLift(GuiGraphicsExtractor context, boolean pushed) {
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
        if (CrowBarState.isXpBarVisible()) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        boolean hasSelectedClaims = !CrowBarState.getClaimWaypoints().isEmpty();
        if (!LocatorBarVisibility.hasRenderSource(
                CrowBarState.hasAlliumDataReceived(),
                CrowBarState.isIntegratedServer,
                false,
                hasSelectedClaims)) return false;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return false;
        boolean crowBarOwnsVanillaBar = CrowBarState.shouldCancelVanillaLocatorBar();
        boolean hasRenderablePlayers = CrowBarState.shouldRenderPlayers()
                && CrowBarState.hasRenderablePlayers(client.player.getUUID());
        return LocatorBarVisibility.shouldRenderCrowBar(
                CrowBarState.isXpBarVisible(),
                hasRenderablePlayers,
                hasSelectedClaims,
                crowBarOwnsVanillaBar,
                crowBarOwnsVanillaBar && hasNonClaimVanillaWaypoints(client, cameraEntity));
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

    /**
     * Draws CrowBar's replacement bar background.
     *
     * <p>Separate from the markers because the two belong on opposite sides of the experience
     * level in vanilla's extraction order: the background must go under the level number, while
     * the markers must go over vanilla's own dots, which are extracted after it.
     */
    public static void renderLocatorBarBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!hasLocatorContent()) return;

        int barX = (context.guiWidth() - 182) / 2;
        int locatorBarY = context.guiHeight() - 29;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
    }

    public static void renderLocatorBarPlayers(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int locatorBarY = screenHeight - 29;
        int centerX = screenWidth / 2;

        List<EntryRenderData> entries = new ArrayList<>();
        boolean hasSelectedClaims = !CrowBarState.getClaimWaypoints().isEmpty();
        if (CrowBarState.shouldRenderPlayers()) {
            boolean hasAlliumSource = false;
            for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
                if (alliumData.isExpired()) continue;
                hasAlliumSource = true;
                UUID uuid = alliumData.uuid;
                addEntryForPosition(cameraEntity, camera, screenWidth, locatorBarY, entries, uuid, alliumData.x, alliumData.y, alliumData.z, true, alliumData.bearingOnly);
            }

            boolean hasIntegratedServerSource = client.getSingleplayerServer() != null;
            if (!hasAlliumSource && hasIntegratedServerSource) {
                for (ServerPlayer player : client.getSingleplayerServer().getPlayerList().getPlayers()) {
                    UUID uuid = player.getUUID();
                    if (uuid.equals(cameraEntity.getUUID())) continue;
                    addEntryForPosition(cameraEntity, camera, screenWidth, locatorBarY, entries, uuid, player.getX(), player.getY(), player.getZ(), false, false);
                }
            }

            if (!hasAlliumSource && !hasIntegratedServerSource) {
                boolean vanillaLocatorVisible = CrowBarState.isVanillaLocatorBarVisible();
                if (!LocatorBarVisibility.hasRenderSource(
                        false, false, vanillaLocatorVisible, hasSelectedClaims)) return;
                if (vanillaLocatorVisible) {
                    addVanillaPlayerWaypointEntries(client, cameraEntity, camera, tickCounter, screenWidth, locatorBarY, entries);
                }
            }
        }

        // Vanilla waypoints are ours to draw only when we cancelled the vanilla bar.
        boolean crowbarOwnsBar = CrowBarState.shouldCancelVanillaLocatorBar();
        if (crowbarOwnsBar) {
            addVanillaWaypointEntries(client, cameraEntity, camera, tickCounter, screenWidth, locatorBarY, entries);
        }
        addClaimWaypointEntries(cameraEntity, camera, screenWidth, locatorBarY, entries);

        if (CrowBarState.isXpBarVisible()) return;

        if (entries.isEmpty()) return;

        EntryRenderData closest = entries.stream()
                .filter(EntryRenderData::markerVisible)
                .min(Comparator.comparingInt(e -> Math.abs(e.markerX - centerX)))
                .orElse(null);

        for (EntryRenderData entry : entries) {
            if (entry == closest) continue;
            renderLocatorDot(context, entry);
        }

        if (closest != null) {
            renderLocatorDot(context, closest);
        }

        if (LocatorBarVisibility.shouldRenderPlayerSkins(
                CrowBarState.shouldRenderPlayers(), CrowBarState.skinsEnabled)) {
            for (EntryRenderData entry : entries) {
                if (!entry.markerVisible) continue;
                if (entry == closest) continue;
                renderLocatorSkin(context, entry);
            }

            if (closest != null) {
                renderLocatorSkin(context, closest);
            }
        }

        for (EntryRenderData entry : entries) {
            queueEntryLabel(entry);
        }
        drawQueuedLabels(context);
    }

    /**
     * @param bearingOnly the position only gives a direction, so the distance is reported as unknown
     */
    private static void addEntryForPosition(Entity cameraEntity, Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries, UUID uuid, double x, double y, double z, boolean allowVerticalArrow, boolean bearingOnly) {
        if (uuid.equals(cameraEntity.getUUID())) return;

        Vec3 cameraPosition = camera.position();
        double deltaX = x - cameraPosition.x();
        double deltaZ = z - cameraPosition.z();
        double relativeYaw = LocatorProjection.relativeYaw(
                cameraPosition.x(), cameraPosition.z(), camera.yRot(), x, z);

        double deltaY = y - cameraPosition.y();
        double distance = bearingOnly ? -1.0D : Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        boolean markerVisible = isWithinLocatorView(relativeYaw);
        if (!markerVisible) return;

        Identifier arrowSprite = allowVerticalArrow
                ? getArrowSpriteForPosition(camera, x, y, z)
                : null;

        int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
        int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
        int markerY = locatorBarY - 2;

        int sizeHundredths = calculateSizeFromDistance(distance < 0 ? Float.MAX_VALUE : (float) distance, 9);
        int size = sizeHundredths / 100;
        int dotCenterX = markerX + 4;
        int dotCenterY = markerY + 4;
        Identifier dotSprite = getDotSpriteForDistance(distance < 0 ? Double.MAX_VALUE : distance);

        entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance, dotSprite, arrowSprite, markerVisible, true, null, null, false));
    }

    /**
     * Adds the server's vanilla waypoints to the bar.
     *
     * <p>Only called when CrowBar has cancelled the vanilla locator bar. Vanilla would otherwise
     * have drawn these itself, so calling it while vanilla still renders would double-draw every
     * dot. Mirrors {@code LocatorBarRenderer#extractRenderState} so styles, colours and
     * distance-based sprite selection stay identical to vanilla, including datapack-defined
     * waypoint styles.
     *
     * <p>Players are skipped: they already arrive through a position source that supports skins and
     * name tags, and rendering both would stack two dots on the same player.
     */
    private static void addVanillaWaypointEntries(Minecraft client, Entity cameraEntity, Camera camera, DeltaTracker tickCounter, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) return;

        Level level = cameraEntity.level();
        TickRateManager tickRateManager = level.tickRateManager();
        PartialTickSupplier partialTickSupplier = entity -> tickCounter.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));

        int[] seen = {0, 0};
        connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            seen[0]++;
            UUID uuid = waypoint.id().left().orElse(null);
            if (uuid != null && uuid.equals(cameraEntity.getUUID())) return;
            if (uuid != null && getPlayerInfo(uuid) != null) return;
            // Claim markers are drawn from claim data instead, which carries the name and works at
            // any distance; rendering the entity's waypoint too would stack two dots.
            if (CrowBarState.isClaimMarker(uuid)) return;
            seen[1]++;
            logWaypointDiagnostics(waypoint, cameraEntity);

            double relativeYaw = waypoint.yawAngleToCamera(level, camera, partialTickSupplier);
            if (!isWithinLocatorView(relativeYaw)) return;

            // Beyond 332 blocks the server sends bearing only and distanceSquared() is infinite.
            // WaypointStyle#sprite handles that by falling through to its farthest sprite, but the
            // size ramp and the distance label would both produce nonsense, so clamp them instead.
            double distanceSquared = waypoint.distanceSquared(cameraEntity);
            float spriteDistance = Mth.sqrt((float) distanceSquared);
            double distance = Double.isFinite(distanceSquared) ? Math.sqrt(distanceSquared) : -1.0D;

            // A waypoint with an explicitly chosen style — a claim bowtie, or any datapack style —
            // keeps that icon at every distance. Vanilla ramps it down into a plain dot past the
            // style's near_distance (64 blocks for bowtie), which throws away the only thing that
            // distinguishes it from a player. The stock "default" style still ramps as normal.
            Waypoint.Icon icon = waypoint.icon();
            WaypointStyle style = client.getWaypointStyles().get(icon.style);
            boolean pinStyle = !icon.style.equals(WaypointStyleAssets.DEFAULT);
            Identifier dotSprite = style.sprite(pinStyle ? 0.0F : spriteDistance);
            int color = icon.color.orElseGet(() -> waypoint.id().map(
                    id -> ARGB.setBrightness(ARGB.color(255, id.hashCode()), 0.9F),
                    name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)));

            Identifier arrowSprite = switch (waypoint.pitchDirectionToCamera(level, client.gameRenderer, partialTickSupplier)) {
                case UP -> LOCATOR_ARROW_UP;
                case DOWN -> LOCATOR_ARROW_DOWN;
                case NONE -> null;
            };

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            int size = calculateSizeFromDistance(distance < 0 ? Float.MAX_VALUE : (float) distance, 9) / 100;

            entries.add(new EntryRenderData(uuid, markerX, markerX + 4, markerY + 4, size, distance, dotSprite, arrowSprite, true, false, color, null, false));
        });
        logWaypointCounts(seen[0], seen[1]);
    }

    /**
     * Adds the players vanilla is already tracking on its own locator bar.
     *
     * <p>Used on a plain server, where nothing feeds CrowBar positions directly. The waypoint
     * manager is the only source that reaches past the client's entity tracking range — a Paper
     * server stops sending player entities at 48 blocks while the locator bar keeps tracking them
     * to 332 and beyond — so reading loaded entities instead left almost every marker on the bar
     * without a skin, name tag or distance.
     *
     * <p>Vanilla still owns the bar in this case and has drawn the dot and its vertical arrow
     * already, so these entries contribute only the skin and label layers on top.
     */
    private static void addVanillaPlayerWaypointEntries(Minecraft client, Entity cameraEntity, Camera camera, DeltaTracker tickCounter, int screenWidth, int locatorBarY, List<EntryRenderData> entries) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) return;

        Level level = cameraEntity.level();
        TickRateManager tickRateManager = level.tickRateManager();
        PartialTickSupplier partialTickSupplier = entity -> tickCounter.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));

        connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            UUID uuid = waypoint.id().left().orElse(null);
            if (uuid == null || uuid.equals(cameraEntity.getUUID())) return;
            if (CrowBarState.isClaimMarker(uuid)) return;
            // A waypoint with no tab list entry is a marker entity or a datapack waypoint, not a
            // player, so it has no skin or name to overlay and vanilla's own dot is enough.
            if (getPlayerInfo(uuid) == null) return;

            double relativeYaw = waypoint.yawAngleToCamera(level, camera, partialTickSupplier);
            if (!isWithinLocatorView(relativeYaw)) return;

            // Beyond 332 blocks the server sends bearing only and distanceSquared() is infinite.
            // Carry that through as -1 so the label prints "?m" instead of a nonsense number.
            double distanceSquared = waypoint.distanceSquared(cameraEntity);
            double distance = Double.isFinite(distanceSquared) ? Math.sqrt(distanceSquared) : -1.0D;

            Identifier arrowSprite = switch (waypoint.pitchDirectionToCamera(level, client.gameRenderer, partialTickSupplier)) {
                case UP -> LOCATOR_ARROW_UP;
                case DOWN -> LOCATOR_ARROW_DOWN;
                case NONE -> null;
            };

            // Vanilla's own sprite and colour, so a repainted dot covers vanilla's exactly and Team
            // colouring keeps the colour vanilla chose.
            Waypoint.Icon icon = waypoint.icon();
            Identifier vanillaSprite = client.getWaypointStyles().get(icon.style).sprite(Mth.sqrt((float) distanceSquared));
            int vanillaColor = icon.color.orElseGet(() -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F));

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            int size = calculateSizeFromDistance(distance < 0 ? Float.MAX_VALUE : (float) distance, 9) / 100;

            entries.add(new EntryRenderData(uuid, markerX, markerX + 4, markerY + 4, size, distance,
                    vanillaSprite, arrowSprite, true, true, vanillaColor, null, true));
        });
    }

    /** True when vanilla contributes something besides a player or GPX claim marker. */
    private static boolean hasNonClaimVanillaWaypoints(Minecraft client, Entity cameraEntity) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) return false;

        boolean[] found = {false};
        connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            if (found[0]) return;
            UUID uuid = waypoint.id().left().orElse(null);
            if (uuid != null && uuid.equals(cameraEntity.getUUID())) return;
            if (uuid != null && getPlayerInfo(uuid) != null) return;
            if (CrowBarState.isClaimMarker(uuid)) return;
            found[0] = true;
        });
        return found[0];
    }

    private static int lastWaypointTotal = -1;
    private static int lastWaypointNonPlayer = -1;

    /**
     * Logs the waypoint set once whenever it changes.
     *
     * <p>Diagnostic for the case where a claim marker is expected but nothing appears: it separates
     * "the server never sent the waypoint" (total stays 0) from "it arrived but was filtered or
     * drawn off-bar" (non-player count rises but no bowtie is visible).
     */
    private static void logWaypointCounts(int total, int nonPlayer) {
        if (total == lastWaypointTotal && nonPlayer == lastWaypointNonPlayer) return;
        lastWaypointTotal = total;
        lastWaypointNonPlayer = nonPlayer;
        System.out.println("[CrowBar] vanilla waypoints: total=" + total + " nonPlayer=" + nonPlayer);
    }

    private static java.util.Set<UUID> loggedWaypoints = new java.util.HashSet<>();

    /** Logs each non-player waypoint once, so a mis-set style or colour is visible in the log. */
    private static void logWaypointDiagnostics(TrackedWaypoint waypoint, Entity cameraEntity) {
        UUID uuid = waypoint.id().left().orElse(null);
        if (uuid == null || !loggedWaypoints.add(uuid)) return;
        System.out.println("[CrowBar] waypoint " + uuid
                + " style=" + waypoint.icon().style
                + " color=" + waypoint.icon().color
                + " distSq=" + waypoint.distanceSquared(cameraEntity));
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
            double deltaX = claim.x() - cameraPosition.x();
            double deltaZ = claim.z() - cameraPosition.z();
            double relativeYaw = LocatorProjection.relativeYaw(
                    cameraPosition.x(), cameraPosition.z(), camera.yRot(), claim.x(), claim.z());
            if (!isWithinLocatorView(relativeYaw)) continue;

            double deltaY = claim.y() - cameraPosition.y();
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            int color = claim.color() != null
                    ? claim.color()
                    : ARGB.setBrightness(ARGB.color(255, claim.id().hashCode()), 0.9F);

            entries.add(new EntryRenderData(null, markerX, markerX + 4, markerY + 4, 9, distance,
                    CLAIM_BOWTIE_SPRITE, getArrowSpriteForPosition(camera, claim.x(), claim.y(), claim.z()),
                    true, false, color, claim.name(), false));
        }
    }

    private static void renderLocatorDot(GuiGraphicsExtractor context, EntryRenderData entry) {
        if (!entry.markerVisible) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        // A framed face stands in for the dot, so drawing the dot underneath would only show
        // through as a smudge around the frame.
        boolean framed = config.skinBorder && entry.isPlayer && CrowBarState.skinsEnabled && hasSkin(entry.uuid);

        if (entry.vanillaDrawsMarker) {
            // Vanilla still owns the bar for this entry and drew the dot and arrow itself. Team
            // colouring leaves that dot alone; any other colour repaints it with vanilla's own
            // sprite, so the new dot covers the old one exactly.
            if (config.dotColorMode != DotColorMode.TEAM && !framed && entry.dotSprite != null) {
                drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite,
                        DotColors.forPlayer(config, entry.uuid, null));
            }
            return;
        }

        int alpha = MarkerFade.alpha(config, entry.distance);
        if (!framed) {
            int dotColor = entry.overrideColor != null ? entry.overrideColor : getDotColor(entry.uuid);
            drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite, DotColors.withAlpha(dotColor, alpha));
        }
        if (entry.arrowSprite != null && config.heightArrows) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite, alpha);
        }
    }

    private static void renderLocatorSkin(GuiGraphicsExtractor context, EntryRenderData entry) {
        if (!entry.markerVisible || !entry.isPlayer) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        int frameColor;
        if (!entry.vanillaDrawsMarker) {
            frameColor = getDotColor(entry.uuid);
        } else if (config.dotColorMode == DotColorMode.TEAM && entry.overrideColor != null) {
            frameColor = entry.overrideColor;
        } else {
            frameColor = DotColors.forPlayer(config, entry.uuid, null);
        }
        renderPlayerFace(context, entry.uuid, entry.dotCenterX, entry.dotCenterY, entry.size, frameColor,
                MarkerFade.alpha(config, entry.distance));
    }

    private static void queueEntryLabel(EntryRenderData entry) {
        if (!entry.markerVisible) return;
        CrowBarConfig config = CrowBarConfigStore.effective();
        // Players resolve through the tab list; claim markers only have a name if the server
        // sent one, and stay unlabelled otherwise rather than showing a raw UUID.
        String name = !CrowBarState.nameTagsEnabled ? null : entry.isPlayer ? getName(entry.uuid) : entry.label;
        int textY = entry.dotCenterY - entry.size / 2 - 12;
        // Vanilla draws its own arrows regardless of the height arrow setting.
        if (LOCATOR_ARROW_UP.equals(entry.arrowSprite) && (entry.vanillaDrawsMarker || config.heightArrows)) {
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
            // A bearing-only waypoint has no distance to report, so say so rather than dropping the
            // label entirely and leaving the toggle looking dead.
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

    /** Draws every label queued this frame, after all markers so no dot or face covers one. */
    private static void drawQueuedLabels(GuiGraphicsExtractor context) {
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

    private static void drawLabel(GuiGraphicsExtractor context, Font font, QueuedLabel label, boolean focused, CrowBarConfig config) {
        int alpha = label.alpha() * (focused || !config.dimUnfocusedLabels ? 255 : UNFOCUSED_LABEL_ALPHA) / 255;
        // Text extraction treats an alpha below 4 as "no alpha given" and draws it opaque.
        if (alpha < 4) return;

        int x = label.left();
        int y = label.y();
        int color = (alpha << 24) | 0xFFFFFF;
        if (config.labelBackground) {
            context.fill(x - LABEL_PADDING_X, y - LABEL_PADDING_Y,
                    x + label.width() - 1 + LABEL_PADDING_X, y + font.lineHeight - 1 + LABEL_PADDING_Y,
                    (LABEL_BACKGROUND_ALPHA * alpha / 255) << 24);
            context.text(font, label.text(), x, y, color, false);
            HudLift.INSTANCE.requestClearance(y - LABEL_PADDING_Y, context.guiHeight());
        } else {
            context.text(font, label.text(), x, y, color);
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
     * overlays. Non-player entries are vanilla waypoints relayed from the server (claim markers,
     * datapack waypoints); they carry their own {@code overrideColor} from the waypoint icon and
     * have no skin or name to look up.
     *
     * @param uuid               {@code null} for waypoints identified by a string rather than a UUID
     * @param distance           {@code -1} when the server sends bearing only and the range is unknown
     * @param overrideColor      pre-resolved ARGB, or {@code null} to derive one from {@code uuid};
     *                           for a vanilla-drawn player, the colour vanilla gave the dot
     * @param label              text to draw above the marker; players resolve their own from the tab list
     * @param vanillaDrawsMarker vanilla still owns the bar and drew this dot, so only the skin and
     *                           label layers are ours; {@code dotSprite} is then vanilla's sprite
     */
    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, Identifier dotSprite, Identifier arrowSprite, boolean markerVisible, boolean isPlayer, Integer overrideColor, String label, boolean vanillaDrawsMarker) {
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
        double deltaX = targetX - cameraPosition.x();
        double deltaZ = targetZ - cameraPosition.z();
        double targetCenterY = targetY + 1.0D;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(targetCenterY - cameraPosition.y(), horizontalDistance));
        return targetPitch - camera.xRot();
    }

    private static void drawVerticalArrow(GuiGraphicsExtractor context, int centerX, int centerY, Identifier sprite, int alpha) {
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
            Player player = client.level.getPlayerInAnyDimension(uuid);
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
        PlayerTeam team = player.getTeam();

        if (team == null) {
            return 0xFFFFFFFF;
        }

        net.minecraft.ChatFormatting color = team.getColor();

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
        PlayerInfo entry = getPlayerInfo(uuid);
        if (entry != null) {
            return VersionCompat.getGameProfileName(entry.getProfile());
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

    private static boolean hasSkin(UUID uuid) {
        return getPlayerInfo(uuid) != null || getLoadedPlayerSkin(uuid) != null;
    }

    /** Draws a face centred on the marker, framed in {@code frameColor} when the skin frame is on. */
    private static void renderPlayerFace(GuiGraphicsExtractor context, UUID uuid, int centerX, int centerY, int size, int frameColor, int alpha) {
        PlayerInfo entry = getPlayerInfo(uuid);
        PlayerSkin skin = entry != null ? entry.getSkin() : getLoadedPlayerSkin(uuid);
        if (skin == null) {
            return;
        }

        boolean showHat = entry == null || entry.showHat();
        boolean upsideDown = isPlayerUpsideDown(uuid);
        Identifier texture = skin.body().texturePath();
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        int tint = DotColors.withAlpha(0xFFFFFFFF, alpha);
        if (CrowBarConfigStore.effective().skinBorder) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, SKIN_FRAME, x, y, size, size, DotColors.withAlpha(frameColor, alpha));
            PlayerFaceExtractor.extractRenderState(context, texture, x + 1, y + 1, size - 2, showHat, upsideDown, tint);
        } else {
            PlayerFaceExtractor.extractRenderState(context, texture, x, y, size, showHat, upsideDown, tint);
        }
    }

    private static PlayerInfo getPlayerInfo(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        return connection == null ? null : connection.getPlayerInfo(uuid);
    }

    private static PlayerSkin getLoadedPlayerSkin(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getUUID().equals(uuid)) {
            return client.player.getSkin();
        }

        Player player = getLoadedPlayer(uuid);
        if (player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin();
        }
        AlliumPlayerData positioned = CrowBarState.alliumPlayerData.get(uuid);
        if (positioned != null && positioned.name != null) {
            // Named by the server but not in the tab list, like the plugin's fake markers.
            return DefaultPlayerSkin.get(uuid);
        }
        return null;
    }

    private static boolean isPlayerUpsideDown(UUID uuid) {
        Player player = getLoadedPlayer(uuid);
        return player != null && AvatarRenderer.isPlayerUpsideDown(player);
    }

    private static Player getLoadedPlayer(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? null : client.level.getPlayerInAnyDimension(uuid);
    }

    public static void drawSelfView(GuiGraphicsExtractor context) {
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
        boolean framed = CrowBarState.skinsEnabled && CrowBarConfigStore.effective().skinBorder && hasSkin(uuid);
        if (!framed) {
            drawTintedLocatorDot(context, iconCenterX, iconCenterY, LOCATOR_DOT_SPRITES[0], dotColor);
        }

        if (CrowBarState.skinsEnabled) {
            renderPlayerFace(context, uuid, iconCenterX, iconCenterY, iconSize, dotColor, 255);
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = VersionCompat.getGameProfileName(client.player.getGameProfile());
            int textX = iconCenterX - client.font.width(name) / 2;
            context.text(client.font, name, textX, barY - 10, 0xFFFFFFFF);
        }
    }
}
