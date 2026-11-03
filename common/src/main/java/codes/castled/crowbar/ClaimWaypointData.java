package codes.castled.crowbar;

import java.util.Set;
import java.util.UUID;

/**
 * One claim the server says this player may see on the locator bar.
 *
 * <p>Position comes from the server's claim records rather than from an entity, so a claim keeps
 * rendering when its terrain is unloaded and at any distance.
 *
 * @param markerId the vanilla waypoint the server also sends for this claim, or null when it sends
 *                 none (CrowBar-only mode). Used to suppress that duplicate.
 * @param owned    whether the viewer owns this claim, as opposed to being trusted on it
 * @param color    ARGB tint, or null to derive one from the claim id
 * @param modes    the server's claim modes that show this claim, or empty when the server did not
 *                 say. See {@link ClaimWaypoints}.
 */
public record ClaimWaypointData(String id, String name, double x, double y, double z,
                                boolean owned, UUID markerId, Integer color, Set<String> modes) {
    public ClaimWaypointData {
        modes = modes == null ? Set.of() : Set.copyOf(modes);
    }
}
