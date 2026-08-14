package codes.castled.crowbar;

/** Pure horizontal locator-bar projection shared by every supported Minecraft version. */
public final class LocatorProjection {
    private LocatorProjection() {
    }

    /**
     * Returns the target yaw relative to the camera.
     *
     * <p>The origin must be the rendered camera position, not the focused entity's body position.
     * They nearly coincide in first person but separate in third person, front view, and detached
     * camera modes. Vanilla waypoints project from the camera, so using the entity makes CrowBar's
     * replacement markers drift away from vanilla dots as soon as the camera detaches.
     */
    public static double relativeYaw(
            double cameraX,
            double cameraZ,
            double cameraYaw,
            double targetX,
            double targetZ
    ) {
        double targetYaw = Math.toDegrees(Math.atan2(targetZ - cameraZ, targetX - cameraX)) - 90.0D;
        return normalizeRelativeYaw(targetYaw - cameraYaw);
    }

    public static double normalizeRelativeYaw(double yaw) {
        while (yaw < -180.0D) yaw += 360.0D;
        while (yaw > 180.0D) yaw -= 360.0D;
        return yaw;
    }
}
