package codes.castled.crowbar.bukkit.locator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pretend players that only one viewer sees, for screenshots and for trying out settings without a
 * crowd. They are fixed points in the viewer's world, so turning around moves them along the bar
 * like real players, and every precision and colour setting applies to them as it would to anyone.
 *
 * <p>Each is named after one of Minecraft's default skins, with a UUID picked so that the client
 * draws that skin's face for it.
 */
public final class FakeMarkers {
    /** Minecraft's default skins, in the order its default-skin table lists them. */
    static final String[] NAMES = {"Alex", "Ari", "Efe", "Kai", "Makena", "Noor", "Steve", "Sunny", "Zuri"};
    /** The table holds every skin twice, slim arms then wide. */
    private static final int DEFAULT_SKIN_COUNT = NAMES.length * 2;
    public static final int MAX_COUNT = 50;
    private static final double MIN_DISTANCE = 24;
    private static final double MAX_DISTANCE = 750;

    public record Marker(UUID uuid, String name, UUID world, double x, double y, double z) {
    }

    private final Map<UUID, List<Marker>> byViewer = new ConcurrentHashMap<>();

    /**
     * Replaces the viewer's fake players with {@code count} new ones scattered around a point.
     *
     * @param maxDistance the locator's distance limit, or 0 for none, so every marker stays visible
     */
    public List<Marker> spawn(UUID viewer, UUID world, double x, double y, double z, int count, double maxDistance, Random random) {
        double farthest = maxDistance > 0 ? Math.min(MAX_DISTANCE, maxDistance * 0.95) : MAX_DISTANCE;
        double nearest = Math.min(MIN_DISTANCE, farthest / 2);
        List<Marker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int skin = i % NAMES.length;
            String name = i < NAMES.length ? NAMES[skin] : NAMES[skin] + (i / NAMES.length + 1);
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = nearest + random.nextDouble() * (farthest - nearest);
            markers.add(new Marker(uuidForSkin(skin, random), name, world,
                    x + Math.cos(angle) * distance,
                    y - 24 + random.nextDouble() * 72,
                    z + Math.sin(angle) * distance));
        }
        byViewer.put(viewer, List.copyOf(markers));
        return markers;
    }

    public List<Marker> get(UUID viewer) {
        return byViewer.getOrDefault(viewer, List.of());
    }

    /** @return whether the viewer had any */
    public boolean clear(UUID viewer) {
        return byViewer.remove(viewer) != null;
    }

    public void clearAll() {
        byViewer.clear();
    }

    /** A random UUID the client's default-skin lookup maps to {@code NAMES[skin]}, slim or wide. */
    static UUID uuidForSkin(int skin, Random random) {
        while (true) {
            UUID uuid = new UUID(random.nextLong(), random.nextLong());
            if (Math.floorMod(uuid.hashCode(), DEFAULT_SKIN_COUNT) % NAMES.length == skin) {
                return uuid;
            }
        }
    }
}
