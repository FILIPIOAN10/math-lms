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
    void keepsTheBoxWhenTheAiCutTheDrawingItself() {
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.dispose();
        List<Integer> box = List.of(300, 300, 560, 700); // the top of the circle is outside: the circle runs into the edge

        assertThat(FigureTrimmer.tighten(page, box)).isEqualTo(box);
    }

    @Test
    void keepsABoxThatIsAlreadyTight() {
        BufferedImage page = page();
        Graphics2D g = pen(page);
        pieChart(g, 420, 380);
        g.dispose();
        List<Integer> box = List.of(262, 222, 500, 620);

        assertThat(FigureTrimmer.tighten(page, box)).isEqualTo(box);
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
