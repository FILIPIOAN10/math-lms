package ro.mathlms.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import ro.mathlms.content.RoCount;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.storage.ImageUploadSanitizer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scanned PDF as page images. Page count, input size, render dimensions, concurrency and processing time are bounded
 * before any page image is stored or sent to an AI provider.
 */
final class PdfPages {

    /** A4 pages render directly at a readable size, without first allocating a larger bitmap. */
    static final float DPI = 200;
    static final int MAX_SIDE = 2000;
    static final long MAX_PROCESSING_MILLIS = 30_000;
    private static final float QUALITY = 0.85f;
    private static final long MAX_RENDER_PIXELS = (long) MAX_SIDE * MAX_SIDE;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    /** Bounded workers and queue keep pathological PDFs from consuming unbounded memory or threads. */
    private static final ThreadPoolExecutor RENDERERS = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), daemonThreads(), new ThreadPoolExecutor.AbortPolicy());

    private PdfPages() {
    }

    /** The pages of {@code pdf} as JPEGs, in order; more than {@code maxPages} pages, or not a PDF, is refused. */
    static List<byte[]> render(byte[] pdf, int maxPages) {
        if (pdf == null || pdf.length == 0 || !ImageUploadSanitizer.hasPdfSignature(pdf)) {
            throw new InvalidQuizException("PDF-ul nu poate fi deschis. Trimite un PDF valid.");
        }
        if (pdf.length > AiImportService.MAX_PDF_BYTES) {
            throw new InvalidQuizException("Trimite un PDF valid de cel mult 20 MB.");
        }
        if (maxPages < 1) {
            throw new InvalidQuizException("PDF-ul depășește numărul de pagini permise.");
        }

        Future<List<byte[]>> future;
        try {
            future = RENDERERS.submit(() -> renderNow(pdf, maxPages));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Serverul procesează alte PDF-uri. Încearcă din nou în câteva secunde.");
        }
        try {
            return future.get(MAX_PROCESSING_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancel(future);
            throw new InvalidQuizException("Procesarea PDF-ului a depășit 30 de secunde. Trimite paginile ca poze.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(future);
            throw new InvalidQuizException("Procesarea PDF-ului a fost întreruptă. Încearcă din nou.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidQuizException invalid) throw invalid;
            if (cause instanceof InvalidPasswordException) {
                throw new InvalidQuizException("PDF-ul e protejat cu parolă. Salvează-l fără parolă și încearcă din nou.");
            }
            if (cause instanceof IOException) {
                throw new InvalidQuizException("PDF-ul nu poate fi deschis. Verifică fișierul sau trimite pozele paginilor.");
            }
            throw new InvalidQuizException("Nu am putut procesa PDF-ul. Verifică fișierul și încearcă din nou.");
        }
    }

    private static void cancel(Future<List<byte[]>> future) {
        future.cancel(true);
        if (future instanceof Runnable task) RENDERERS.remove(task);
    }

    private static List<byte[]> renderNow(byte[] pdf, int maxPages) throws IOException {
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
            List<byte[]> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                PDPage page = document.getPage(i);
                double longSidePoints = Math.max(page.getCropBox().getWidth(), page.getCropBox().getHeight())
                        * page.getUserUnit();
                if (!Double.isFinite(longSidePoints) || longSidePoints <= 0 || longSidePoints > 1_000_000) {
                    throw new InvalidQuizException("PDF-ul conține o pagină cu dimensiuni neobișnuite.");
                }
                float scale = (float) Math.min(DPI / 72.0, MAX_SIDE / longSidePoints);
                BufferedImage image = renderer.renderImage(i, scale, ImageType.RGB);
                long pixels = (long) image.getWidth() * image.getHeight();
                if (image.getWidth() < 1 || image.getHeight() < 1 || pixels > MAX_RENDER_PIXELS) {
                    throw new InvalidQuizException("PDF-ul conține o pagină prea mare pentru procesare.");
                }
                pages.add(jpeg(image));
            }
            return pages;
        }
    }

    private static ThreadFactory daemonThreads() {
        return task -> {
            Thread thread = new Thread(task, "pdf-render-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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
