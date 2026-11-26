package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import ro.mathlms.quiz.QuizItemType;

import static org.assertj.core.api.Assertions.assertThat;

class FigureTrimmerTest {

    /** A 1000 x 1000 px page, so the 0-1000 box is also in pixels. */
    private static BufferedImage page() {
        BufferedImage page = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(new Color(238, 236, 230)); // a photo's paper, not pure white
        g.fillRect(0, 0, 1000, 1000);
        g.dispose();
        return page;
    }

    private static Graphics2D pen(BufferedImage page) {
        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(30, 30, 30));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        return g;
    }

    /** The pie with its three labels, drawn around (cx, cy): the drawing spans about cx-190..cx+190, cy-110..cy+105. */
    private static void pieChart(Graphics2D g, int cx, int cy) {
        g.setStroke(new BasicStroke(2));
        g.drawOval(cx - 100, cy - 100, 200, 200);
        g.drawLine(cx, cy, cx, cy - 100);
        g.drawLine(cx, cy, cx, cy + 100);
        g.drawLine(cx, cy, cx - 70, cy - 70);
        for (int y = cy - 90; y < cy + 90; y += 8) { // hatching
            g.drawLine(cx + 5, y, cx + 60, y + 30);
        }
        g.drawString("Ioana", cx - 150, cy - 95);
        g.drawString("10% din", cx - 160, cy - 72);
        g.drawLine(cx - 95, cy - 78, cx - 50, cy - 80);
        g.drawString("Radu", cx + 120, cy - 20);
        g.drawString("50% din", cx + 110, cy + 5);
        g.drawLine(cx + 100, cy, cx + 108, cy - 2);
        g.drawString("Mihai", cx - 190, cy + 30);
        g.drawString("40% din", cx - 190, cy + 55);
        g.drawLine(cx - 120, cy + 35, cx - 100, cy + 35);
    }

    @Test
    void dropsTheFrameAndTheCutTextAndCentresTheBoxOnTheDrawing() {
        // As in the teacher's screenshot: the AI's box (y 200-560, x 150-800) holds the chart off to the left, the
        // page's slanted frame line along its top and right, and half a line of text cut by its bottom edge.
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.setStroke(new BasicStroke(2));
        g.drawLine(150, 205, 800, 240);   // frame line across the top, slanted
        g.drawLine(780, 200, 785, 560);   // frame line down the right
        g.drawString("levilor din școală", 160, 568); // cut by the bottom edge
        g.dispose();
        List<Integer> box = List.of(200, 150, 560, 800);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        // inside the AI's box, around the whole chart with its labels ...
        assertThat(tight.get(0)).isBetween(240, 285);
        assertThat(tight.get(1)).isBetween(200, 235);
        assertThat(tight.get(2)).isBetween(480, 530);
        assertThat(tight.get(3)).isBetween(600, 650);
        // ... without the frame line on the right or the cut text below
        assertThat(tight.get(3)).isLessThan(770);
        assertThat(tight.get(2)).isLessThan(550);
        // and the chart is now in the middle: its centre (x 420) is the box's centre, give or take
        assertThat((tight.get(1) + tight.get(3)) / 2).isBetween(400, 440);
    }

    @Test
    void keepsTheSidesWhereTheAiCutTheDrawingItselfAndTightensTheOthers() {
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.dispose();
        // The top of the circle is outside: the drawing runs into the top edge.
        List<Integer> box = List.of(300, 300, 560, 700);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertThat(tight.get(0)).isEqualTo(300); // the cut side stays where the AI put it
        assertThat(tight.get(1)).isBetween(300, 330); // the left still tightens onto the circle
        // The empty paper below and on the right goes, with every label kept whole.
        int[] ink = inkIn(page, box);
        assertThat(tight.get(2)).isBetween(ink[3], ink[3] + 25);
        assertThat(tight.get(3)).isBetween(ink[2], ink[2] + 25);
        assertThat(tight.get(3)).isLessThan(650);
    }

    @Test
    void barelyMovesABoxThatIsAlreadyTight() {
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.dispose();
        List<Integer> box = List.of(262, 222, 500, 620);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        for (int i = 0; i < 4; i++) {
            assertThat(tight.get(i)).isCloseTo(box.get(i), org.assertj.core.data.Offset.offset(15));
        }
    }

    /** The ink inside {@code box} (dark pixels, the shadow excluded): {left, top, right, bottom}, right/bottom exclusive. */
    private static int[] inkIn(BufferedImage page, List<Integer> box) {
        int minX = 1000, minY = 1000, maxX = -1, maxY = -1;
        for (int y = box.get(0); y < box.get(2); y++) {
            for (int x = box.get(1); x < box.get(3); x++) {
                int p = page.getRGB(x, y);
                int lum = (299 * ((p >> 16) & 0xff) + 587 * ((p >> 8) & 0xff) + 114 * (p & 0xff)) / 1000;
                if (lum < 100) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    /** The drawing inside the trimmed box has the same margin on all four sides, give or take a few pixels. */
    private static void assertCentred(BufferedImage page, List<Integer> box) {
        int[] ink = inkIn(page, box);
        assertEvenMargins(box, ink[0], ink[1], ink[2], ink[3]);
    }

    /** The same margin on all four sides, give or take a pixel or two. */
    private static void assertEvenMargins(List<Integer> box, int left, int top, int right, int bottom) {
        int l = left - box.get(1);
        int t = top - box.get(0);
        int r = box.get(3) - right;
        int b = box.get(2) - bottom;
        assertThat(List.of(l, t, r, b)).allSatisfy(m -> assertThat(m).isBetween(Math.min(Math.min(l, t), Math.min(r, b)),
                Math.min(Math.min(l, t), Math.min(r, b)) + 3));
        assertThat(Math.min(Math.min(l, t), Math.min(r, b))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void centresADrawingTheAiBoxedOffCentreEvenIfThatMeansGoingPastTheBoxOverBlankPaper() {
        // The trapezoid of the screenshot: the AI's box hugs it on the left and top and leaves paper on the right and below.
        BufferedImage page = page();
        Graphics2D g = pen(page);
        g.setStroke(new BasicStroke(2));
        int[] xs = {300, 520, 600, 300};
        int[] ys = {300, 300, 560, 560};
        g.drawPolygon(xs, ys, 4);
        g.drawLine(300, 300, 600, 560);
        g.dispose();
        List<Integer> box = List.of(296, 297, 700, 760);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertEvenMargins(tight, 299, 299, 601, 561);
        assertThat(tight.get(1)).isLessThan(297); // the left margin grew past the AI's box, over blank paper
    }

    @Test
    void cutsAwayTheShadowAlongThePageEdge() {
        // The line A-B-C-D-E with a dark gradient down the right (the page curving into the spine).
        BufferedImage page = page();
        Graphics2D shade = page.createGraphics();
        for (int x = 720; x < 800; x++) {
            int lum = 238 - (x - 720) * 2;
            shade.setColor(new Color(lum, lum, lum));
            shade.drawLine(x, 0, x, 1000);
        }
        shade.dispose();
        Graphics2D g = pen(page);
        g.setStroke(new BasicStroke(3));
        g.drawLine(200, 420, 680, 440);
        for (String letter : new String[] {"A", "B", "C", "D", "E"}) {
            int x = 200 + "ABCDE".indexOf(letter) * 120;
            g.drawString(letter, x - 6, 405);
            g.fillOval(x - 5, 415 + "ABCDE".indexOf(letter) * 4, 10, 10);
        }
        g.dispose();
        List<Integer> box = List.of(360, 180, 480, 800);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertThat(tight.get(3)).isLessThan(720); // no shadow
        assertThat(tight.get(3)).isGreaterThan(680); // the line is whole
        assertCentred(page, tight);
    }

    @Test
    void dropsASteepFrameLineAcrossTheTopEvenThoughItReachesFarIntoTheBox() {
        // The teacher's pie on the server: the page's frame runs across the top at a slant, from the left edge down to
        // the right edge, well past the strip along the edge - it is still a frame line, not the drawing.
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.setStroke(new BasicStroke(2));
        g.setStroke(new BasicStroke(5)); // a heavy line, a big share of all the ink in the box
        g.drawLine(230, 215, 820, 232); // starts inside the box, runs out on the right
        g.drawString("diagrama de mai jos.", 300, 170); // the question's text above it, cut by the box's top
        g.dispose();
        List<Integer> box = List.of(150, 150, 560, 800);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertThat(tight.get(3)).isLessThan(700); // the line's right end is not kept
        assertThat(tight.get(1)).isGreaterThan(190); // nor its left end
        assertCentred(page, List.of(tight.get(0) + 1, tight.get(1), tight.get(2), tight.get(3)));
    }

    @Test
    void neverReachesIntoTheTextNextToTheDrawingToEvenTheMargins() {
        // Text right above the chart's frame: the top margin can only be tiny, so every margin is that tiny.
        BufferedImage page = page();
        Graphics2D g = pen(page);
        g.setStroke(new BasicStroke(2));
        g.drawRect(250, 300, 400, 250);
        g.drawOval(380, 340, 150, 150);
        g.drawString("diagrama de mai jos.", 300, 292); // baseline 8 px above the frame
        g.dispose();
        List<Integer> box = List.of(285, 230, 600, 680);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertThat(tight.get(0)).isGreaterThanOrEqualTo(293); // below the text
        assertEvenMargins(tight, 249, 299, 651, 551);
    }

    @Test
    void takesNoNoticeOfSquaredPaperOrThePageShowingThrough() {
        BufferedImage page = page();
        Graphics2D g = page.createGraphics();
        g.setColor(new Color(205, 205, 205)); // faint grid lines running across the whole box
        for (int i = 0; i < 1000; i += 25) {
            g.drawLine(i, 0, i, 1000);
            g.drawLine(0, i, 1000, i);
        }
        g.dispose();
        Graphics2D ink = pen(page);
        pieChart(ink, 420, 380);
        ink.dispose();
        List<Integer> box = List.of(150, 120, 650, 850);

        List<Integer> tight = FigureTrimmer.tighten(page, box);

        assertThat(tight).isNotEqualTo(box);
        assertThat((tight.get(1) + tight.get(3)) / 2).isBetween(400, 440);
        assertThat((tight.get(0) + tight.get(2)) / 2).isBetween(360, 410);
    }

    @Test
    void tightensTheFigureBoxesOfAReadingAndLeavesTheRestAsTheyWere() throws Exception {
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.drawLine(780, 200, 785, 560);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page, "png", png);
        List<PageExtractor.PageImage> pages = List.of(new PageExtractor.PageImage(png.toByteArray(), "image/png"),
                new PageExtractor.PageImage(new byte[] {1, 2, 3}, "image/jpeg"));
        ExtractedItem chart = item(1, List.of(200, 150, 560, 800));
        ExtractedItem noFigure = item(null, List.of());
        ExtractedItem unreadablePage = item(2, List.of(200, 150, 560, 800));

        List<ExtractedItem> out = FigureTrimmer.tightenAll(List.of(chart, noFigure, unreadablePage), pages);

        assertThat(out.get(0).figureBox()).isNotEqualTo(chart.figureBox());
        assertThat(out.get(0).figureBox().get(3)).isLessThan(770); // the frame line on the right is out
        assertThat(out.get(0).statement()).isEqualTo(chart.statement());
        assertThat(out.get(1)).isSameAs(noFigure);
        assertThat(out.get(2)).isSameAs(unreadablePage);
    }

    private static ExtractedItem item(Integer figurePage, List<Integer> box) {
        return new ExtractedItem(1, null, QuizItemType.SINGLE_CHOICE, "Câte voturi a primit Radu?", List.of("a", "b"), 5,
                figurePage != null, List.of(), List.of(), null, null, figurePage, box);
    }

    @Test
    void keepsAnEmptyBox() {
        List<Integer> box = List.of(100, 100, 400, 400);
        assertThat(FigureTrimmer.tighten(page(), box)).isEqualTo(box);
    }
}
