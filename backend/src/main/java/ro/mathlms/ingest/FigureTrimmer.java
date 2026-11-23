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
 * Tightens the AI's box around the drawing itself, so a cut figure is the drawing with a small even margin - centred -
 * and not the drawing plus a strip of the box around it, the page's frame lines or half a line of the text below.
 *
 * <p>How: the box is looked at as dark marks ("ink") on the paper. Marks that run into the box's edge are what the box
 * cut through - a frame line, the neighbouring text - and go; specks go. What is left is grouped around the biggest
 * mark (the drawing), taking in the marks close to it (its labels: "Radu 50%", the letters A, B, C), and the box shrinks
 * to that group plus a margin. Faint marks (paper texture, squared paper, the other side of the page showing through)
 * are not ink: they are measured against the paper's own brightness.
 *
 * <p>Never worse than the AI: the box stays as it was when the drawing itself runs into its edge (the AI cut it too
 * tight - shrinking would keep only its labels), when there is no clear drawing, or when the gain would be tiny.
 */
final class FigureTrimmer {

    /** The region is looked at in cells of at most this many per side: thin lines survive (a cell keeps its darkest pixel). */
    private static final int GRID = 400;
    /** A mark this close to the drawing (share of the region's longer side) is one of its labels. */
    private static final double LABEL_GAP = 0.05;
    /** A mark that runs into the edge with most of itself in this band along the edges is the frame or cut text. */
    private static final double EDGE_BAND = 0.12;
    /** Margin around the tightened drawing, as a share of its longer side. */
    private static final double MARGIN = 0.04;
    /** Below this share of the old area the gain is real; above it the AI's box is kept as it is. */
    private static final double WORTH_IT = 0.92;

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
     * The box {@code [ymin, xmin, ymax, xmax]} (0-1000 of the page) tightened onto the drawing, or {@code box} itself
     * when that is not safe.
     */
    static List<Integer> tighten(BufferedImage page, List<Integer> box) {
        int width = page.getWidth();
        int height = page.getHeight();
        int x0 = (int) Math.clamp((long) box.get(1) * width / 1000, 0, width - 1);
        int y0 = (int) Math.clamp((long) box.get(0) * height / 1000, 0, height - 1);
        int x1 = (int) Math.clamp((long) box.get(3) * width / 1000, x0 + 1, width);
        int y1 = (int) Math.clamp((long) box.get(2) * height / 1000, y0 + 1, height);
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
                int rgb = page.getRGB(x, y);
                int lum = (299 * ((rgb >> 16) & 0xff) + 587 * ((rgb >> 8) & 0xff) + 114 * (rgb & 0xff)) / 1000;
                int i = ((y - y0) / cell) * gw + (x - x0) / cell;
                if (lum < dark[i]) {
                    dark[i] = lum;
                }
            }
        }
        boolean[] ink = withoutSolidAreas(inkOf(dark), gw, gh);

        List<Mark> marks = marks(ink, gw, gh);
        if (marks.isEmpty()) {
            return box;
        }
        int totalInk = marks.stream().mapToInt(m -> m.cells).sum();
        int band = (int) Math.ceil(EDGE_BAND * Math.min(gw, gh));
        List<Mark> inside = new ArrayList<>();
        for (Mark mark : marks) {
            if (mark.touchesEdge(gw, gh)) {
                // A frame line or cut text lies along the edge; a mark that also reaches well inside is the drawing
                // itself running out of the box (the AI cut it too tight): then the AI's box stays.
                long reachingIn = Math.round(mark.cells * (1 - mark.share(p -> hugging(p, band, gw, gh))));
                if (reachingIn * 7 >= totalInk) {
                    return box;
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
        for (Mark m : inside) {
            if (m != seed && m.beside(drawing, gap)) {
                group[0] = Math.min(group[0], m.minX);
                group[1] = Math.min(group[1], m.minY);
                group[2] = Math.max(group[2], m.maxX);
                group[3] = Math.max(group[3], m.maxY);
            }
        }

        // Back to pixels, with an even margin, never outside the AI's box.
        int gx0 = x0 + group[0] * cell;
        int gy0 = y0 + group[1] * cell;
        int gx1 = Math.min(x1, x0 + (group[2] + 1) * cell);
        int gy1 = Math.min(y1, y0 + (group[3] + 1) * cell);
        int margin = Math.max(4, (int) Math.round(MARGIN * Math.max(gx1 - gx0, gy1 - gy0)));
        int nx0 = Math.max(x0, gx0 - margin);
        int ny0 = Math.max(y0, gy0 - margin);
        int nx1 = Math.min(x1, gx1 + margin);
        int ny1 = Math.min(y1, gy1 + margin);
        if ((long) (nx1 - nx0) * (ny1 - ny0) > WORTH_IT * w * h) {
            return box;
        }
        List<Integer> tight = List.of(
                Math.max(box.get(0), (int) Math.floor(ny0 * 1000.0 / height)),
                Math.max(box.get(1), (int) Math.floor(nx0 * 1000.0 / width)),
                Math.min(box.get(2), (int) Math.ceil(ny1 * 1000.0 / height)),
                Math.min(box.get(3), (int) Math.ceil(nx1 * 1000.0 / width)));
        if (tight.get(2) - tight.get(0) < ExtractedItems.MIN_FIGURE || tight.get(3) - tight.get(1) < ExtractedItems.MIN_FIGURE) {
            return box;
        }
        return tight;
    }

    /**
     * Ink is clearly darker than this region's paper: the paper is the brightness most cells have (the 70th
     * percentile, as the drawing never covers most of its own box), and ink sits well below it, so the squared paper,
     * a shadow or the other side of the page showing through stay paper.
     */
    private static boolean[] inkOf(int[] dark) {
        int[] sorted = dark.clone();
        Arrays.sort(sorted);
        int paper = sorted[(int) (sorted.length * 0.7)];
        int limit = Math.min(paper - 60, (int) (paper * 0.62));
        boolean[] ink = new boolean[dark.length];
        for (int i = 0; i < dark.length; i++) {
            ink[i] = dark[i] < limit;
        }
        return ink;
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
