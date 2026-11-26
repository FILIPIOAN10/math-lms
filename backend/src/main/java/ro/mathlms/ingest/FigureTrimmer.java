package ro.mathlms.ingest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tightens the AI's box around the drawing itself, so a cut figure is the drawing with the same margin on every side -
 * centred - and not the drawing plus a strip of the box around it, the page's frame lines, the shadow where the page
 * curves into the spine, or half a line of the text below.
 *
 * <p>How: first the shadow along the box's edges goes (columns or rows clearly darker than the paper all the way
 * through). Then the box is looked at as dark marks ("ink") on the paper. Marks that run into the box's edge and stay
 * along it are what the box cut through - a frame line, the neighbouring text - and go; specks go. What is left is
 * grouped around the biggest mark (the drawing), taking in the marks close to it (its labels: "Radu 50%", the letters
 * A, B, C). Faint marks (paper texture, squared paper, the other side of the page showing through) are not ink: they are
 * measured against the paper's own brightness.
 *
 * <p>The margin is then the same on every side: as wide as {@link #MARGIN} allows, but never reaching ink (the text
 * beside the drawing) - so where text sits close on one side, every side gets that smaller margin. It may go past the
 * AI's box, over blank paper only, which is what centres a drawing the AI boxed off to one side.
 *
 * <p>Where the drawing itself runs into an edge (the AI cut it, or a graph's lines run off), that side stays where the
 * AI put it and the other sides are still tightened.
 */
final class FigureTrimmer {

    /** The region is looked at in cells of at most this many per side: thin lines survive (a cell keeps its darkest pixel). */
    private static final int GRID = 400;
    /** A mark this close to the drawing (share of the region's longer side) is one of its labels. */
    private static final double LABEL_GAP = 0.05;
    /** A mark that runs into the edge with most of itself in this band along the edges is the frame or cut text. */
    private static final double EDGE_BAND = 0.12;
    /** The widest margin around the drawing, as a share of its longer side. */
    private static final double MARGIN = 0.04;
    /** A column (row) at the box's edge this much darker than the paper, on average, is shadow. */
    private static final int SHADOW = 30;

    private FigureTrimmer() {
    }

    /**
     * Every figure box of a reading tightened on its page, each page decoded once. Best effort: a page that cannot be
     * read, or any surprise, leaves that box as the AI gave it - the teacher can still adjust it.
     */
    static List<ExtractedItem> tightenAll(List<ExtractedItem> items, List<PageExtractor.PageImage> pages) {
        Map<Integer, BufferedImage> decoded = new HashMap<>();
        List<ExtractedItem> out = new ArrayList<>(items.size());
        for (ExtractedItem item : items) {
            Integer number = item.figurePage();
            List<Integer> box = item.figureBox();
            if (number == null || box == null || box.size() != 4 || number < 1 || number > pages.size()) {
                out.add(item);
                continue;
            }
            try {
                BufferedImage page = decoded.computeIfAbsent(number, n -> read(pages.get(n - 1).bytes()));
                List<Integer> tight = page == null ? box : tighten(page, box);
                out.add(tight.equals(box) ? item : new ExtractedItem(item.page(), item.section(), item.type(),
                        item.statement(), item.options(), item.points(), item.hasFigure(), item.warnings(), item.parts(),
                        item.enChapter(), item.enChapterReason(), number, tight));
            } catch (RuntimeException e) {
                out.add(item);
            }
        }
        return out;
    }

    private static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * The box {@code [ymin, xmin, ymax, xmax]} (0-1000 of the page) tightened onto the drawing and centred, or
     * {@code box} itself when that is not safe.
     */
    static List<Integer> tighten(BufferedImage page, List<Integer> box) {
        int width = page.getWidth();
        int height = page.getHeight();
        int bx0 = (int) Math.clamp((long) box.get(1) * width / 1000, 0, width - 1);
        int by0 = (int) Math.clamp((long) box.get(0) * height / 1000, 0, height - 1);
        int bx1 = (int) Math.clamp((long) box.get(3) * width / 1000, bx0 + 1, width);
        int by1 = (int) Math.clamp((long) box.get(2) * height / 1000, by0 + 1, height);
        if (bx1 - bx0 < 20 || by1 - by0 < 20) {
            return box;
        }
        int[] lum = luminance(page);

        // The shadow along the edges first: it is neither drawing nor paper, and no margin may reach into it.
        int[] region = withoutShadow(lum, width, bx0, by0, bx1, by1);
        int x0 = region[0], y0 = region[1], x1 = region[2], y1 = region[3];
        int w = x1 - x0;
        int h = y1 - y0;
        if (w < 20 || h < 20) {
            return box;
        }
        int cell = Math.max(1, (int) Math.ceil(Math.max(w, h) / (double) GRID));
        int gw = (w + cell - 1) / cell;
        int gh = (h + cell - 1) / cell;

        // Each cell's darkest pixel: a one-pixel line stays visible at any scale.
        int[] dark = new int[gw * gh];
        Arrays.fill(dark, 255);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int i = ((y - y0) / cell) * gw + (x - x0) / cell;
                dark[i] = Math.min(dark[i], lum[y * width + x]);
            }
        }
        int limit = inkLimit(dark);
        boolean[] ink = new boolean[dark.length];
        for (int i = 0; i < dark.length; i++) {
            ink[i] = dark[i] < limit;
        }
        ink = withoutSolidAreas(ink, gw, gh);

        List<Mark> marks = marks(ink, gw, gh);
        if (marks.isEmpty()) {
            return box;
        }
        int totalInk = marks.stream().mapToInt(m -> m.cells).sum();
        int band = (int) Math.ceil(EDGE_BAND * Math.min(gw, gh));
        List<Mark> inside = new ArrayList<>();
        for (Mark mark : marks) {
            if (mark.touchesEdge(gw, gh)) {
                // A frame line or cut text lies along the edge and goes; a mark that also reaches well inside is the
                // drawing itself running out of the box (the AI cut it, or a graph's line runs off): it stays.
                long reachingIn = Math.round(mark.cells * (1 - mark.share(p -> hugging(p, band, gw, gh))));
                if (reachingIn * 7 >= totalInk) {
                    inside.add(mark);
                }
            } else if (mark.cells * 400 >= totalInk) { // specks go
                inside.add(mark);
            }
        }
        if (inside.isEmpty()) {
            return box;
        }

        // The drawing and the marks close to it (labels, letters at the corners).
        Mark seed = inside.stream().max((a, b) -> Integer.compare(a.cells, b.cells)).orElseThrow();
        int gap = Math.max(3, (int) Math.round(LABEL_GAP * Math.max(gw, gh)));
        // Near the drawing itself, not near one of its labels: a chain of words would walk out into the question's text.
        int[] drawing = {seed.minX, seed.minY, seed.maxX, seed.maxY};
        int[] group = drawing.clone();
        List<Mark> labels = new ArrayList<>();
        List<Mark> rest = new ArrayList<>();
        for (Mark m : inside) {
            if (m == seed) {
                continue;
            }
            (m.beside(drawing, gap) ? labels : rest).add(m);
        }
        // A label is whole words: the letters of "Mihai" or "40% din" further from the drawing than the first ones
        // join them - only along the same line, letter by letter, so the question's text above or below stays out.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < rest.size(); i++) {
                Mark m = rest.get(i);
                if (labels.stream().anyMatch(l -> l.sameLineNextTo(m))) {
                    labels.add(rest.remove(i));
                    grew = true;
                    break;
                }
            }
        }
        for (Mark m : labels) {
            group[0] = Math.min(group[0], m.minX);
            group[1] = Math.min(group[1], m.minY);
            group[2] = Math.max(group[2], m.maxX);
            group[3] = Math.max(group[3], m.maxY);
        }
        // A side the drawing runs into stays where it is (the box's edge, or where the shadow began).
        boolean lockLeft = group[0] <= 0;
        boolean lockTop = group[1] <= 0;
        boolean lockRight = group[2] >= gw - 1;
        boolean lockBottom = group[3] >= gh - 1;
        if (lockLeft && lockTop && lockRight && lockBottom) {
            return box;
        }

        // Back to pixels: the drawing's own extent, measured on the page itself so the edges are exact.
        int gx0 = x0 + group[0] * cell;
        int gy0 = y0 + group[1] * cell;
        int gx1 = Math.min(x1, x0 + (group[2] + 1) * cell);
        int gy1 = Math.min(y1, y0 + (group[3] + 1) * cell);
        int[] exact = exactInk(lum, width, gx0, gy0, gx1, gy1, limit);
        if (exact != null) {
            if (!lockLeft) gx0 = exact[0];
            if (!lockTop) gy0 = exact[1];
            if (!lockRight) gx1 = exact[2];
            if (!lockBottom) gy1 = exact[3];
        }

        // One margin for every free side: as wide as MARGIN allows, but over blank paper only - never into the text
        // next to the drawing, never into the shadow, never off the page. Beyond the AI's box is fine.
        int want = Math.max(4, (int) Math.round(MARGIN * Math.max(gx1 - gx0, gy1 - gy0)));
        int[] reach = {
                region[0] > bx0 ? region[0] : 0, region[1] > by0 ? region[1] : 0,
                region[2] < bx1 ? region[2] : width, region[3] < by1 ? region[3] : height};
        int margin = want;
        if (!lockLeft) margin = Math.min(margin, blankCols(lum, width, gx0 - 1, -1, gy0, gy1, reach[0], margin, limit));
        if (!lockRight) margin = Math.min(margin, blankCols(lum, width, gx1, 1, gy0, gy1, reach[2] - 1, margin, limit));
        if (!lockTop) margin = Math.min(margin, blankRows(lum, width, gy0 - 1, -1, gx0, gx1, reach[1], margin, limit));
        if (!lockBottom) margin = Math.min(margin, blankRows(lum, width, gy1, 1, gx0, gx1, reach[3] - 1, margin, limit));

        int nx0 = lockLeft ? x0 : gx0 - margin;
        int ny0 = lockTop ? y0 : gy0 - margin;
        int nx1 = lockRight ? x1 : gx1 + margin;
        int ny1 = lockBottom ? y1 : gy1 + margin;
        List<Integer> tight = List.of(
                Math.clamp((long) Math.floor(ny0 * 1000.0 / height), 0, 1000),
                Math.clamp((long) Math.floor(nx0 * 1000.0 / width), 0, 1000),
                Math.clamp((long) Math.ceil(ny1 * 1000.0 / height), 0, 1000),
                Math.clamp((long) Math.ceil(nx1 * 1000.0 / width), 0, 1000));
        if (tight.get(2) - tight.get(0) < ExtractedItems.MIN_FIGURE || tight.get(3) - tight.get(1) < ExtractedItems.MIN_FIGURE) {
            return box;
        }
        return tight;
    }

    private static int[] luminance(BufferedImage page) {
        int width = page.getWidth();
        int height = page.getHeight();
        int[] rgb = page.getRGB(0, 0, width, height, null, 0, width);
        int[] lum = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int p = rgb[i];
            lum[i] = (299 * ((p >> 16) & 0xff) + 587 * ((p >> 8) & 0xff) + 114 * (p & 0xff)) / 1000;
        }
        return lum;
    }

    /**
     * The region without the shadow along its edges: from each edge inwards, columns (rows) whose median is clearly
     * below the paper's are cut, at most a quarter of the side. The median, not the average: a column of label text or
     * a line along the edge darkens part of the column, the grey band where the page curves into the spine darkens
     * all of it.
     */
    private static int[] withoutShadow(int[] lum, int width, int x0, int y0, int x1, int y1) {
        int[] colMedian = new int[x1 - x0];
        int[] histogram = new int[256];
        for (int x = x0; x < x1; x++) {
            Arrays.fill(histogram, 0);
            for (int y = y0; y < y1; y++) {
                histogram[lum[y * width + x]]++;
            }
            colMedian[x - x0] = median(histogram, y1 - y0);
        }
        int[] rowMedian = new int[y1 - y0];
        for (int y = y0; y < y1; y++) {
            Arrays.fill(histogram, 0);
            for (int x = x0; x < x1; x++) {
                histogram[lum[y * width + x]]++;
            }
            rowMedian[y - y0] = median(histogram, x1 - x0);
        }
        int paper = Math.max(percentile(colMedian, 0.7), percentile(rowMedian, 0.7));
        int shadow = paper - SHADOW;
        int maxX = (x1 - x0) / 4;
        int maxY = (y1 - y0) / 4;
        int left = 0;
        while (left < maxX && colMedian[left] < shadow) left++;
        int right = 0;
        while (right < maxX && colMedian[colMedian.length - 1 - right] < shadow) right++;
        int top = 0;
        while (top < maxY && rowMedian[top] < shadow) top++;
        int bottom = 0;
        while (bottom < maxY && rowMedian[rowMedian.length - 1 - bottom] < shadow) bottom++;
        return new int[] {x0 + left, y0 + top, x1 - right, y1 - bottom};
    }

    private static int median(int[] histogram, int count) {
        int seen = 0;
        for (int v = 0; v < histogram.length; v++) {
            seen += histogram[v];
            if (seen * 2 >= count) {
                return v;
            }
        }
        return 255;
    }

    private static int percentile(int[] values, double share) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.min(sorted.length - 1, sorted.length * share)];
    }

    /**
     * Ink is clearly darker than this region's paper: the paper is the brightness most cells have (the 70th
     * percentile, as the drawing never covers most of its own box), and ink sits well below it, so the squared paper,
     * a shadow or the other side of the page showing through stay paper.
     */
    private static int inkLimit(int[] dark) {
        int paper = percentile(dark, 0.7);
        return Math.min(paper - 60, (int) (paper * 0.62));
    }

    /** The exact pixel rectangle of the ink inside the given rectangle, or null when there is none. */
    private static int[] exactInk(int[] lum, int width, int x0, int y0, int x1, int y1, int limit) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (lum[y * width + x] < limit) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    /** How many blank columns there are from {@code start} going {@code step}, over rows y0..y1, up to {@code max}. */
    private static int blankCols(int[] lum, int width, int start, int step, int y0, int y1, int stop, int max, int limit) {
        int n = 0;
        for (int x = start; n < max && (step < 0 ? x >= stop : x <= stop); x += step, n++) {
            for (int y = y0; y < y1; y++) {
                if (lum[y * width + x] < limit) {
                    return n;
                }
            }
        }
        return n;
    }

    /** How many blank rows there are from {@code start} going {@code step}, over columns x0..x1, up to {@code max}. */
    private static int blankRows(int[] lum, int width, int start, int step, int x0, int x1, int stop, int max, int limit) {
        int n = 0;
        for (int y = start; n < max && (step < 0 ? y >= stop : y <= stop); y += step, n++) {
            for (int x = x0; x < x1; x++) {
                if (lum[y * width + x] < limit) {
                    return n;
                }
            }
        }
        return n;
    }

    /**
     * A solid dark area is not a drawing's line: the shadow beside the page, the desk past its edge, a thumb. Cells
     * in a window that is nearly all ink are dropped (an outline stays: its window is half paper).
     */
    private static boolean[] withoutSolidAreas(boolean[] ink, int gw, int gh) {
        int[] sum = new int[(gw + 1) * (gh + 1)]; // summed-area table
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                sum[(y + 1) * (gw + 1) + x + 1] = (ink[y * gw + x] ? 1 : 0) + sum[y * (gw + 1) + x + 1]
                        + sum[(y + 1) * (gw + 1) + x] - sum[y * (gw + 1) + x];
            }
        }
        int r = 3;
        boolean[] kept = ink.clone();
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (!ink[y * gw + x]) {
                    continue;
                }
                int ax = Math.max(0, x - r), ay = Math.max(0, y - r), bx = Math.min(gw, x + r + 1), by = Math.min(gh, y + r + 1);
                int area = (bx - ax) * (by - ay);
                int filled = sum[by * (gw + 1) + bx] - sum[ay * (gw + 1) + bx] - sum[by * (gw + 1) + ax] + sum[ay * (gw + 1) + ax];
                if (filled * 10 >= area * 8) {
                    kept[y * gw + x] = false;
                }
            }
        }
        return kept;
    }

    /** The connected marks (cells touching, diagonals too). */
    private static List<Mark> marks(boolean[] ink, int gw, int gh) {
        int[] label = new int[ink.length];
        List<Mark> marks = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < ink.length; start++) {
            if (!ink[start] || label[start] != 0) {
                continue;
            }
            Mark mark = new Mark();
            label[start] = marks.size() + 1;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                int x = i % gw;
                int y = i / gw;
                mark.add(x, y);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) {
                            continue;
                        }
                        int n = ny * gw + nx;
                        if (ink[n] && label[n] == 0) {
                            label[n] = marks.size() + 1;
                            queue.add(n);
                        }
                    }
                }
            }
            marks.add(mark);
        }
        return marks;
    }

    /** One connected mark: its cells and the rectangle around them. */
    private static final class Mark {
        int cells;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        private final List<int[]> points = new ArrayList<>();

        void add(int x, int y) {
            cells++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            points.add(new int[] {x, y});
        }

        boolean touchesEdge(int gw, int gh) {
            return minX <= 0 || minY <= 0 || maxX >= gw - 1 || maxY >= gh - 1;
        }

        /** The share of its cells for which {@code test} holds. */
        double share(java.util.function.Predicate<int[]> test) {
            return points.stream().filter(test).count() / (double) cells;
        }

        /**
         * A label of the drawing: right next to it ({@code gap}), or level with it - beside it or above/below it - no
         * further than 30 % of its size ("Mihai 40%" at the end of a leader line). Words off at a corner are the
         * question's text.
         */
        /**
         * The next letter or word on the same line as this one: both small (letter-sized), mostly level with each
         * other, and no more than about a letter's height apart.
         */
        boolean sameLineNextTo(Mark other) {
            int height = Math.max(maxY - minY, other.maxY - other.minY) + 1;
            int overlap = Math.min(maxY, other.maxY) - Math.max(minY, other.minY) + 1;
            int dx = Math.max(0, Math.max(minX - other.maxX, other.minX - maxX));
            boolean letterSized = maxY - minY < 60 && other.maxY - other.minY < 60;
            return letterSized && overlap * 2 >= Math.min(maxY - minY, other.maxY - other.minY) + 1 && dx <= height;
        }

        boolean beside(int[] drawing, int gap) {
            int dx = Math.max(0, Math.max(drawing[0] - maxX, minX - drawing[2]));
            int dy = Math.max(0, Math.max(drawing[1] - maxY, minY - drawing[3]));
            if (dx <= gap && dy <= gap) {
                return true;
            }
            int reach = (int) Math.round(0.3 * Math.max(drawing[2] - drawing[0], drawing[3] - drawing[1]));
            return (dy == 0 && dx <= reach) || (dx == 0 && dy <= reach);
        }
    }

    /** Whether a cell lies in the band along the region's edges. */
    private static boolean hugging(int[] p, int band, int gw, int gh) {
        return p[0] < band || p[1] < band || p[0] >= gw - band || p[1] >= gh - band;
    }
}
