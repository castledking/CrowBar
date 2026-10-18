package codes.castled.crowbar.bukkit.locator;

/**
 * Makes a reported position vaguer, an idea borrowed from Player Locator Plus.
 *
 * <p>The locator bar only needs a direction and a distance, so instead of a player's real
 * coordinates the viewer can be sent a point along a rounded direction at a rounded distance.
 * Rounding both stops a player being pinpointed, or triangulated from a few readings.
 *
 * @param directionSteps steps per axis to round the unit direction to; 0 keeps it exact
 * @param sendDistance   whether the viewer may learn the distance at all
 * @param roundBeyond    distances above this are rounded
 * @param roundTo        the multiple to round them to; 0 never rounds
 */
public record LocatorPrecision(int directionSteps, boolean sendDistance, double roundBeyond, double roundTo) {
    /**
     * How far away a bearing-only point is placed. The client ignores it and shows "?m", but it
     * still has to be far enough that the direction stays stable as the viewer moves.
     */
    public static final double BEARING_ONLY_DISTANCE = 1000.0D;

    public static final LocatorPrecision EXACT = new LocatorPrecision(0, true, 0, 0);

    /**
     * @param bearingOnly the point only gives a direction; its distance is meaningless
     */
    public record Reported(double x, double y, double z, boolean bearingOnly) {
    }

    /** Where to tell a viewer at {@code origin} that the target at {@code target} is. */
    public Reported report(double originX, double originY, double originZ, double targetX, double targetY, double targetZ) {
        double dx = targetX - originX;
        double dy = targetY - originY;
        double dz = targetZ - originZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        boolean roundsDistance = roundTo > 0 && distance > roundBeyond;
        if ((directionSteps <= 0 && sendDistance && !roundsDistance) || distance < 1.0E-6) {
            return new Reported(targetX, targetY, targetZ, false);
        }

        double ux = dx / distance;
        double uy = dy / distance;
        double uz = dz / distance;
        if (directionSteps > 0) {
            ux = Math.round(ux * directionSteps) / (double) directionSteps;
            uy = Math.round(uy * directionSteps) / (double) directionSteps;
            uz = Math.round(uz * directionSteps) / (double) directionSteps;
            double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (length < 1.0E-9) {
                return new Reported(targetX, targetY, targetZ, false);
            }
            ux /= length;
            uy /= length;
            uz /= length;
        }

        double reported;
        if (!sendDistance) {
            reported = BEARING_ONLY_DISTANCE;
        } else if (roundsDistance) {
            reported = Math.max(roundTo, Math.round(distance / roundTo) * roundTo);
        } else {
            reported = distance;
        }
        return new Reported(originX + ux * reported, originY + uy * reported, originZ + uz * reported, !sendDistance);
    }
}
