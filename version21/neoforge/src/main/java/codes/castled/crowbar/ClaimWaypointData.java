package codes.castled.crowbar;

import java.util.UUID;

/**
 * One claim the server says this player may see on the locator bar.
 *
 * <p>Position comes from the server's claim records rather than from an entity, so a claim keeps
 * rendering when its terrain is unloaded and at any distance.
 *
 * @param markerId the marker entity backing this claim, or null when the server runs in
 *                 CrowBar-only mode. Used to suppress the duplicate vanilla waypoint.
 * @param owned    whether the viewer owns this claim, as opposed to being trusted on it
 * @param color    ARGB tint, or null to derive one from the claim id
 */
public record ClaimWaypointData(String id, String name, double x, double y, double z,
                                boolean owned, UUID markerId, Integer color) {
}
