package ro.mathlms.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 4: an upload is judged by its bytes, re-encoded without metadata, and bounded before it is decoded. */
class ImageUploadSanitizerTest {

    private static final long LIMIT = ImageUploadSanitizer.STUDENT_PHOTO_MAX_BYTES;

    private static MultipartFile upload(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private static BufferedImage decode(MultipartFile file) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(file.getBytes()));
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    void aPhoneJpegLosesItsExifAndGpsAndIsTurnedUpright() throws Exception {
        byte[] withExif = TestImages.jpegWithExif(80, 40, 6); // 6 = the phone was held upright: rotate 90°
        assertThat(contains(withExif, "Exif")).isTrue();

        MultipartFile clean = ImageUploadSanitizer.sanitize(upload("IMG_0001.jpg", "image/jpeg", withExif), LIMIT);

        assertThat(clean.getContentType()).isEqualTo("image/jpeg");
        assertThat(clean.getOriginalFilename()).isEqualTo("upload.jpg");
        assertThat(contains(clean.getBytes(), "Exif")).isFalse();
        BufferedImage image = decode(clean);
        assertThat(image.getWidth()).isEqualTo(40);  // rotated: width and height swapped
        assertThat(image.getHeight()).isEqualTo(80);
    }

    @Test
    void aPngStaysAPng() throws Exception {
        MultipartFile clean = ImageUploadSanitizer.sanitize(upload("pagina.png", "image/png", TestImages.png(30, 20)), LIMIT);

        assertThat(clean.getContentType()).isEqualTo("image/png");
        assertThat(decode(clean).getWidth()).isEqualTo(30);
    }

    @Test
    void theNameAndTypeTheBrowserSentDoNotMatter() throws Exception {
        // A real JPEG labelled as text is still a JPEG; text labelled as a JPEG is refused.
        assertThat(ImageUploadSanitizer.sanitize(upload("x.txt", "text/plain", TestImages.jpeg(10, 10)), LIMIT)
                .getContentType()).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> ImageUploadSanitizer.sanitize(
                upload("x.jpg", "image/jpeg", "<svg onload=alert(1)>".getBytes()), LIMIT))
                .isInstanceOf(InvalidImageUploadException.class);
    }

    @Test
    void aPdfOrAnEmptyFileIsNotAnImage() {
        assertThatThrownBy(() -> ImageUploadSanitizer.sanitize(upload("x.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes()), LIMIT)).isInstanceOf(InvalidImageUploadException.class);
        assertThatThrownBy(() -> ImageUploadSanitizer.sanitize(upload("x.jpg", "image/jpeg", new byte[0]), LIMIT))
                .isInstanceOf(InvalidImageUploadException.class);
        assertThat(ImageUploadSanitizer.hasPdfSignature("%PDF-1.7".getBytes())).isTrue();
        assertThat(ImageUploadSanitizer.hasPdfSignature(TestImages.png(2, 2))).isFalse();
    }

    @Test
    void aHugeCanvasIsRefusedBeforeItIsDecoded() {
        // 13 000 px wide but a few KB: a decompression bomb in miniature.
        byte[] wide = TestImages.png(13_000, 1);

        assertThatThrownBy(() -> ImageUploadSanitizer.sanitize(upload("wide.png", "image/png", wide), LIMIT))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessageContaining("dimensiuni");
    }

    @Test
    void tooManyBytesAreRefused() {
        byte[] image = TestImages.jpeg(50, 50);

        assertThatThrownBy(() -> ImageUploadSanitizer.sanitize(upload("p.jpg", "image/jpeg", image), image.length - 1))
                .isInstanceOf(InvalidImageUploadException.class)
                .hasMessageContaining("prea mare");
    }

    @Test
    void aLargePhotoIsScaledDownToTheLongSideLimit() throws Exception {
        MultipartFile clean = ImageUploadSanitizer.sanitize(
                upload("big.jpg", "image/jpeg", TestImages.jpeg(ImageUploadSanitizer.MAX_SIDE + 400, 100)), LIMIT);

        assertThat(decode(clean).getWidth()).isEqualTo(ImageUploadSanitizer.MAX_SIDE);
    }
}
