package ro.mathlms.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit 2026-11-27 M1: a stored file goes back to the browser as the picture it is, or as a download - never as a page
 * or a script the browser would run on the app's own address.
 */
class StoredImageTest {

    private static ResponseEntity<Resource> serve(String filename) {
        Resource file = new ByteArrayResource(new byte[]{1}) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return StoredImage.ok(file).body(file);
    }

    @Test
    void aPictureIsServedAsThatPictureInline() {
        ResponseEntity<Resource> png = serve("3f2a.png");
        assertThat(png.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(png.getHeaders().getContentDisposition().isInline()).isTrue();
        assertThat(png.getHeaders().getContentDisposition().getFilename()).isEqualTo("3f2a.png");

        assertThat(serve("3f2a.JPG").getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(serve("3f2a.jpeg").getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(serve("3f2a.gif").getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_GIF);
        assertThat(serve("3f2a.webp").getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/webp"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x.html", "x.htm", "x.js", "x.mjs", "x.svg", "x.xml", "x.xhtml", "x.pdf", "x.txt", "x"})
    void anythingElseIsADownloadNeverAPage(String filename) {
        ResponseEntity<Resource> response = serve(filename);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    }

    @Test
    void aResourceWithoutANameIsADownload() {
        ResponseEntity<Resource> response = serve(null);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    }
}
