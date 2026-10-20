package ro.mathlms.ingest;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Cuts a figure out of a page photo: {@code box} is {@code [ymin, xmin, ymax, xmax]} on the 0-1000 scale the AI (and
 * the review screen) use, mapped onto the photo's own pixels. The cut is saved as a JPEG, like the pages themselves
 * (the browser already turned them upright and stripped their EXIF before upload, so the AI saw these same pixels).
 */
final class FigureCropper {

    private static final float QUALITY = 0.9f;

    private FigureCropper() {
    }

    static byte[] crop(byte[] page, List<Integer> box) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(page));
        if (image == null) {
            throw new IOException("not an image ImageIO can read");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int x0 = Math.clamp((long) box.get(1) * width / 1000, 0, width - 1);
        int y0 = Math.clamp((long) box.get(0) * height / 1000, 0, height - 1);
        int x1 = Math.clamp((long) box.get(3) * width / 1000, x0 + 1, width);
        int y1 = Math.clamp((long) box.get(2) * height / 1000, y0 + 1, height);
        // Drawn onto a plain RGB canvas: JPEG has no alpha, and a PNG page may have one.
        BufferedImage figure = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = figure.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, figure.getWidth(), figure.getHeight());
        g.drawImage(image.getSubimage(x0, y0, x1 - x0, y1 - y0), 0, 0, null);
        g.dispose();
        return jpeg(figure);
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
