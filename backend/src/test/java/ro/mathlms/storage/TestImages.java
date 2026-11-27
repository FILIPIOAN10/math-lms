package ro.mathlms.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Real, tiny images for upload tests (the server now decodes what it receives). */
public final class TestImages {

    private TestImages() {
    }

    /** A real JPEG: left half dark, right half light, so a rotation is visible in the pixels. */
    public static byte[] jpeg(int width, int height) {
        return encode(picture(width, height), "jpeg");
    }

    public static byte[] png(int width, int height) {
        return encode(picture(width, height), "png");
    }

    /**
     * A JPEG with an EXIF block carrying an orientation and a GPS latitude reference (the marker of a location), the
     * way a phone writes it: the sanitizer must apply the orientation and drop the whole block.
     */
    public static byte[] jpegWithExif(int width, int height, int orientation) {
        byte[] plain = jpeg(width, height);
        byte[] app1 = exifApp1(orientation);
        ByteBuffer out = ByteBuffer.allocate(plain.length + app1.length);
        out.put(plain, 0, 2);            // SOI
        out.put(app1);                   // APP1 Exif right after SOI, like a camera
        out.put(plain, 2, plain.length - 2);
        return out.array();
    }

    private static byte[] exifApp1(int orientation) {
        // TIFF (little endian): IFD0 with Orientation (0x0112) and a GPS IFD pointer (0x8825) -> GPS IFD with
        // GPSLatitudeRef (0x0001) = "N".
        ByteBuffer tiff = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);                                                    // IFD0: 2 entries
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);     // GPS IFD at offset 38
        tiff.putInt(0);                                                              // no next IFD
        tiff.putShort((short) 1);                                                    // GPS IFD: 1 entry
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        tiff.putInt(0);
        byte[] tiffBytes = new byte[tiff.position()];
        tiff.flip();
        tiff.get(tiffBytes);
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + header.length + tiffBytes.length;
        ByteBuffer app1 = ByteBuffer.allocate(2 + length).order(ByteOrder.BIG_ENDIAN);
        app1.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(header).put(tiffBytes);
        return app1.array();
    }

    private static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, width / 2, height);
            g.setColor(Color.WHITE);
            g.fillRect(width / 2, 0, width - width / 2, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
