package ro.mathlms.review;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * "Recapitulare amestecată": the same questions, in an order where two from the same group (a test, an EN chapter)
 * follow each other as rarely as possible. Questions answered together share a due date, so without this they come
 * back in a row and the student applies one method five times instead of choosing it each time. No database in sight,
 * like {@link LeitnerEngine}; the same list always gives the same order.
 */
final class Interleaving {

    private Interleaving() {
    }

    /**
     * Each step takes the next item of the group with the most items left - never the group just used, unless it is
     * the only one left. A tie goes to the group met first in {@code items}; inside a group the order stays.
     */
    static <T> List<T> spread(List<T> items, Function<T, ?> groupOf) {
        Map<Object, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            Object group = Objects.requireNonNull(groupOf.apply(item), "group");
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(item);
        }
        List<T> result = new ArrayList<>(items.size());
        Object last = null;
        while (result.size() < items.size()) {
            Object pick = null;
            for (Map.Entry<Object, List<T>> group : groups.entrySet()) {
                if (group.getValue().isEmpty() || group.getKey().equals(last)) {
                    continue;
                }
                if (pick == null || group.getValue().size() > groups.get(pick).size()) {
                    pick = group.getKey();
                }
            }
            if (pick == null) {
                pick = last; // only the group just used has questions left
            }
            result.add(groups.get(pick).remove(0));
            last = pick;
        }
        return result;
    }
}
