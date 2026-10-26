package ro.mathlms.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import ro.mathlms.content.RoCount;
import ro.mathlms.quiz.InvalidQuizException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A scanned PDF as page images: each page rendered and saved as a JPEG of at most {@link #MAX_SIDE} pixels on its long
 * side - what the browser makes of a photo - so the rest of "Test din poze" (page previews, the reader, figure crops by
 * page) works on it unchanged. The page count is checked before anything is rendered.
 */
final class PdfPages {

    /** Enough for small print on an A4 scan (about 1650 x 2340 px), then scaled to {@link #MAX_SIDE}. */
    static final float DPI = 200;
    static final int MAX_SIDE = 2000;
    private static final float QUALITY = 0.85f;

    private PdfPages() {
    }

    /** The pages of {@code pdf} as JPEGs, in order; more than {@code maxPages} pages, or not a PDF, is refused. */
    static List<byte[]> render(byte[] pdf, int maxPages) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int count = document.getNumberOfPages();
            if (count == 0) {
                throw new InvalidQuizException("PDF-ul nu are nicio pagină.");
            }
            if (count > maxPages) {
                throw new InvalidQuizException("PDF-ul are " + RoCount.of(count, "pagină", "pagini") + "; poți trimite cel mult "
                        + maxPages + " odată. Păstrează în PDF doar paginile cu subiectele.");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> pages = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                pages.add(jpeg(fit(renderer.renderImageWithDPI(i, DPI, ImageType.RGB))));
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new InvalidQuizException("PDF-ul e protejat cu parolă. Salvează-l fără parolă și încearcă din nou.");
        } catch (IOException e) {
            throw new InvalidQuizException("PDF-ul nu poate fi deschis. Verifică fișierul sau trimite pozele paginilor.");
        }
    }

    /** Scaled down so the long side is {@link #MAX_SIDE} (never up). */
    private static BufferedImage fit(BufferedImage page) {
        int longSide = Math.max(page.getWidth(), page.getHeight());
        if (longSide <= MAX_SIDE) {
            return page;
        }
        double scale = (double) MAX_SIDE / longSide;
        int width = (int) Math.round(page.getWidth() * scale);
        int height = (int) Math.round(page.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(page, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
