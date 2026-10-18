package codes.castled.crowbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Overlap handling for locator labels, an idea borrowed from Player Locator Plus. */
public final class LabelLayout {
    /** Horizontal gap kept between two visible labels. */
    public static final int GAP = 2;

    private LabelLayout() {
    }

    /**
     * Drops labels that would overlap a more important one.
     *
     * <p>Labels nearer the crosshair win, since that is where the player is looking.
     *
     * @param priority lower is more important, e.g. distance from the screen centre
     * @return the surviving labels, in their original order
     */
    public static <T> List<T> withoutOverlaps(List<T> labels, ToIntFunction<T> left, ToIntFunction<T> right, ToIntFunction<T> priority) {
        List<T> byPriority = new ArrayList<>(labels);
        byPriority.sort(Comparator.comparingInt(priority));

        List<T> kept = new ArrayList<>();
        Set<T> keptSet = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T label : byPriority) {
            int l = left.applyAsInt(label);
            int r = right.applyAsInt(label);
            boolean overlaps = false;
            for (T other : kept) {
                if (l - GAP < right.applyAsInt(other) && r + GAP > left.applyAsInt(other)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(label);
                keptSet.add(label);
            }
        }

        List<T> result = new ArrayList<>(kept.size());
        for (T label : labels) {
            if (keptSet.contains(label)) result.add(label);
        }
        return result;
    }
}
