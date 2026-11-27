package ro.mathlms.storage;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** Validates by file signature, limits decoded dimensions, applies JPEG orientation, and re-encodes without metadata. */
public final class ImageUploadSanitizer {

    public static final long STUDENT_PHOTO_MAX_BYTES = 8L * 1024 * 1024;
    public static final long PAGE_IMAGE_MAX_BYTES = 4L * 1024 * 1024;
    public static final int MAX_SIDE = 4000;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int MAX_DIMENSION = 12_000;
    private static final float JPEG_QUALITY = 0.92f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    private ImageUploadSanitizer() {
    }

    public static MultipartFile sanitize(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageUploadException("Alege o imagine JPEG sau PNG.");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge(maxBytes);
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new InvalidImageUploadException("Alege o imagine JPEG sau PNG.");
            }
            if (bytes.length > maxBytes) {
                throw tooLarge(maxBytes);
            }
            String format = signatureFormat(bytes);
            if (format == null) {
                throw new InvalidImageUploadException("Fișierul nu este o imagine JPEG sau PNG validă.");
            }

            BufferedImage decoded;
            int orientation = format.equals("jpeg") ? jpegOrientation(bytes) : 1;
            try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new InvalidImageUploadException("Imaginea nu poate fi citită.");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String readerFormat = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if ((format.equals("jpeg") && !readerFormat.equals("jpeg") && !readerFormat.equals("jpg"))
                            || (format.equals("png") && !readerFormat.equals("png"))) {
                        throw new InvalidImageUploadException("Formatul imaginii nu este valid.");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                            || (long) width * height > MAX_PIXELS) {
                        throw new InvalidImageUploadException("Imaginea are dimensiuni prea mari.");
                    }
                    decoded = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
            if (decoded == null) {
                throw new InvalidImageUploadException("Imaginea nu poate fi citită.");
            }

            BufferedImage normalized = orientation == 1 ? decoded : orient(decoded, orientation);
            normalized = resize(normalized);
            String outputFormat = format;
            byte[] clean = encode(normalized, outputFormat);
            if (clean.length > maxBytes && outputFormat.equals("png")) {
                outputFormat = "jpeg";
                clean = encode(toRgb(normalized), outputFormat);
            }
            if (clean.length > maxBytes) {
                throw tooLarge(maxBytes);
            }
            String type = outputFormat.equals("png") ? "image/png" : "image/jpeg";
            String filename = outputFormat.equals("png") ? "upload.png" : "upload.jpg";
            return new BytesMultipartFile(file.getName(), filename, type, clean);
        } catch (InvalidImageUploadException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageUploadException("Imaginea nu poate fi citită. Trimite un JPG sau PNG valid.", e);
        }
    }

    /** Strict PDF signature check. MIME type and filename are deliberately ignored. */
    public static boolean hasPdfSignature(byte[] bytes) {
        return bytes != null && bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-';
    }

    private static String signatureFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpeg";
        }
        if (bytes.length >= PNG_SIGNATURE.length) {
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (bytes[i] != PNG_SIGNATURE[i]) return null;
            }
            return "png";
        }
        return null;
    }

    private static BufferedImage resize(BufferedImage source) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        if (longSide <= MAX_SIDE) return source;
        double scale = (double) MAX_SIDE / longSide;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static byte[] encode(BufferedImage source, String format) throws IOException {
        BufferedImage image = format.equals("jpeg") ? toRgb(source) : source;
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("No image writer for " + format);
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            if (format.equals("jpeg")) {
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
                writer.write(null, new IIOImage(image, null, null), parameters);
            } else {
                writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
            }
            output.flush();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** Applies the EXIF orientation before metadata is discarded by re-encoding. */
    private static BufferedImage orient(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = width - 1 - x; dy = y; }
                    case 3 -> { dx = width - 1 - x; dy = height - 1 - y; }
                    case 4 -> { dx = x; dy = height - 1 - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = height - 1 - y; dy = x; }
                    case 7 -> { dx = height - 1 - y; dy = width - 1 - x; }
                    case 8 -> { dx = y; dy = width - 1 - x; }
                    default -> { dx = x; dy = y; }
                }
                target.setRGB(dx, dy, source.getRGB(x, y));
            }
        }
        return target;
    }

    /** Reads the JPEG APP1/TIFF orientation without retaining any source metadata. */
    private static int jpegOrientation(byte[] bytes) {
        int offset = 2;
        while (offset + 4 <= bytes.length && (bytes[offset] & 0xff) == 0xff) {
            while (offset < bytes.length && (bytes[offset] & 0xff) == 0xff) offset++;
            if (offset >= bytes.length) return 1;
            int marker = bytes[offset++] & 0xff;
            if (marker == 0xda || marker == 0xd9) return 1;
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            if (offset + 2 > bytes.length) return 1;
            int length = u16(bytes, offset, false);
            if (length < 2 || offset + length > bytes.length) return 1;
            int data = offset + 2;
            int end = offset + length;
            if (marker == 0xe1 && data + 14 <= end && bytes[data] == 'E' && bytes[data + 1] == 'x'
                    && bytes[data + 2] == 'i' && bytes[data + 3] == 'f' && bytes[data + 4] == 0 && bytes[data + 5] == 0) {
                int tiff = data + 6;
                boolean little = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
                if (!little && !(bytes[tiff] == 'M' && bytes[tiff + 1] == 'M')) return 1;
                if (u16(bytes, tiff + 2, little) != 42) return 1;
                long ifdLong = u32(bytes, tiff + 4, little);
                if (ifdLong > end - tiff - 2) return 1;
                int ifd = tiff + (int) ifdLong;
                int count = u16(bytes, ifd, little);
                for (int i = 0, entry = ifd + 2; i < count && entry + 12 <= end; i++, entry += 12) {
                    if (u16(bytes, entry, little) == 0x0112 && u16(bytes, entry + 2, little) == 3
                            && u32(bytes, entry + 4, little) == 1) {
                        int value = u16(bytes, entry + 8, little);
                        return value >= 1 && value <= 8 ? value : 1;
                    }
                }
                return 1;
            }
            offset = end;
        }
        return 1;
    }

    private static int u16(byte[] bytes, int offset, boolean little) {
        if (offset < 0 || offset + 2 > bytes.length) return 0;
        return little ? (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                : ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static long u32(byte[] bytes, int offset, boolean little) {
        if (offset < 0 || offset + 4 > bytes.length) return Long.MAX_VALUE;
        return little ? (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24)
                : ((bytes[offset] & 0xffL) << 24) | ((bytes[offset + 1] & 0xffL) << 16)
                | ((bytes[offset + 2] & 0xffL) << 8) | (bytes[offset + 3] & 0xffL);
    }

    private static InvalidImageUploadException tooLarge(long maxBytes) {
        long maxMb = Math.max(1, (maxBytes + (1024 * 1024 - 1)) / (1024 * 1024));
        return new InvalidImageUploadException("Imaginea e prea mare (maxim " + maxMb + " MB).");
    }

    private record BytesMultipartFile(String name, String originalFilename, String contentType, byte[] bytes)
            implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        @Override public void transferTo(Path dest) throws IOException { Files.write(dest, bytes); }
    }
}
