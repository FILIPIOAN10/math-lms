package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cutting a figure out of a page photo: the 0-1000 box maps onto the pixels, whatever the page's size. */
class FigureCropperTest {

    /** A 2000 x 1000 white page with a black block at x 600-1000, y 200-500 (the "drawing"). */
    private static byte[] page(String format) throws IOException {
        BufferedImage image = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 2000, 1000);
        g.setColor(Color.BLACK);
        g.fillRect(600, 200, 400, 300);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void cutsTheBoxOutOfThePageAsAJpeg() throws IOException {
        // [ymin, xmin, ymax, xmax] on 0-1000: y 200..500 of 1000 px, x 300..500 of 2000 px = the black block
        byte[] jpeg = FigureCropper.crop(page("png"), List.of(200, 300, 500, 500));

        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF); // JPEG magic number
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage figure = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(figure.getWidth()).isEqualTo(400);
        assertThat(figure.getHeight()).isEqualTo(300);
        assertThat(new Color(figure.getRGB(200, 150)).getRed()).isLessThan(40); // the inside is the drawing
    }

    @Test
    void readsJpegPagesTooAndKeepsTheCutInsideThePage() throws IOException {
        byte[] jpeg = FigureCropper.crop(page("jpg"), List.of(900, 950, 1000, 1000));

        BufferedImage figure = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(figure.getWidth()).isEqualTo(100);
        assertThat(figure.getHeight()).isEqualTo(100);
    }

    @Test
    void somethingThatIsNotAnImageIsAnError() {
        assertThatThrownBy(() -> FigureCropper.crop(new byte[]{1, 2, 3}, List.of(0, 0, 500, 500)))
                .isInstanceOf(IOException.class);
    }
}
