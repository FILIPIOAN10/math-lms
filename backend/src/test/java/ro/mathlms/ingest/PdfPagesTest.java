package ro.mathlms.ingest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import ro.mathlms.quiz.InvalidQuizException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A scanned PDF becomes one JPEG per page, at reading size, before the AI reads it like photos. */
class PdfPagesTest {

    /** A PDF like a scanner makes: each A4 page is one picture (here a grey page with a black bar). */
    static byte[] scannedPdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage scan = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scan.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, 1240, 1754);
            g.setColor(Color.BLACK);
            g.fillRect(100, 100, 800, 40);
            g.dispose();
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.drawImage(LosslessFactory.createFromImage(doc, scan), 0, 0,
                            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void rendersEveryPageAsAJpegNoLongerThan2000Pixels() throws Exception {
        List<byte[]> pages = PdfPages.render(scannedPdf(2), 5);

        assertThat(pages).hasSize(2);
        for (byte[] page : pages) {
            assertThat(page[0]).isEqualTo((byte) 0xFF); // JPEG
            assertThat(page[1]).isEqualTo((byte) 0xD8);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(page));
            assertThat(Math.max(image.getWidth(), image.getHeight())).isEqualTo(PdfPages.MAX_SIDE);
            assertThat(image.getHeight()).isGreaterThan(image.getWidth()); // portrait, as scanned
        }
    }

    @Test
    void refusesMorePagesThanOneImportTakes() {
        assertThatThrownBy(() -> PdfPages.render(scannedPdf(3), 2))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("are 3 pagini").hasMessageContaining("cel mult 2");
    }

    @Test
    void saysSoWhenTheFileIsNotAPdf() {
        assertThatThrownBy(() -> PdfPages.render("nu e un pdf".getBytes(), 5))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("nu poate fi deschis");
    }
}
