package ro.mathlms.enchapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chapter pages as a tree - chapter, section, point - built from the flat list in position order. A parent that
 * does not exist is treated as no parent, so a bad row can never hide a page.
 */
final class EnChapterTree {

    /** Chapter = 0, section = 1, point = 2: nothing deeper. */
    static final int MAX_DEPTH = 2;

    private final Map<Long, EnChapter> byId = new HashMap<>();
    /** Children by parent id, in position order; the key {@code null} holds the chapters themselves. */
    private final Map<Long, List<EnChapter>> children = new LinkedHashMap<>();

    EnChapterTree(List<EnChapter> inPositionOrder) {
        for (EnChapter chapter : inPositionOrder) {
            byId.put(chapter.getId(), chapter);
        }
        for (EnChapter chapter : inPositionOrder) {
            Long parent = byId.containsKey(chapter.getParentId()) ? chapter.getParentId() : null;
            children.computeIfAbsent(parent, key -> new ArrayList<>()).add(chapter);
        }
    }

    List<EnChapter> roots() {
        return children.getOrDefault(null, List.of());
    }

    List<EnChapter> childrenOf(EnChapter chapter) {
        return children.getOrDefault(chapter.getId(), List.of());
    }

    EnChapter parentOf(EnChapter chapter) {
        return chapter.getParentId() == null ? null : byId.get(chapter.getParentId());
    }

    /** The chapter whose pages hold this one: the chapter itself when it has no parent. */
    EnChapter rootOf(EnChapter chapter) {
        EnChapter current = chapter;
        for (int step = 0; step <= MAX_DEPTH + 1 && parentOf(current) != null; step++) {
            current = parentOf(current);
        }
        return current;
    }

    int depthOf(EnChapter chapter) {
        int depth = 0;
        EnChapter current = parentOf(chapter);
        while (current != null && depth <= MAX_DEPTH + 1) {
            depth++;
            current = parentOf(current);
        }
        return depth;
    }

    /** 0 for a page with nothing under it, 1 with sections or points directly under, and so on. */
    int heightOf(EnChapter chapter) {
        return height(chapter, MAX_DEPTH + 2);
    }

    private int height(EnChapter chapter, int budget) {
        int height = 0;
        if (budget > 0) {
            for (EnChapter child : childrenOf(chapter)) {
                height = Math.max(height, 1 + height(child, budget - 1));
            }
        }
        return height;
    }

    /** Everything under {@code chapter}, in book order (a section is followed by its points). */
    List<EnChapter> descendantsOf(EnChapter chapter) {
        List<EnChapter> out = new ArrayList<>();
        collect(chapter, out, MAX_DEPTH + 2);
        return out;
    }

    private void collect(EnChapter chapter, List<EnChapter> out, int budget) {
        if (budget <= 0) {
            return;
        }
        for (EnChapter child : childrenOf(chapter)) {
            out.add(child);
            collect(child, out, budget - 1);
        }
    }

    /** Is {@code candidate} the page itself or one of the pages above it? */
    boolean isSelfOrAncestor(EnChapter candidate, EnChapter page) {
        EnChapter current = page;
        for (int step = 0; current != null && step <= MAX_DEPTH + 2; step++) {
            if (current.getId().equals(candidate.getId())) {
                return true;
            }
            current = parentOf(current);
        }
        return false;
    }
}
