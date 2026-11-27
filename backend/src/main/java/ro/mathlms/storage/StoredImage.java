package ro.mathlms.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

import java.util.Set;

/**
 * How a stored picture goes back to the browser. Its type comes from the stored name's extension, but only a picture
 * type is ever sent as such: anything else (a ".html" or ".js" stored before uploads were re-encoded, an unknown name)
 * goes as {@code application/octet-stream} with {@code Content-Disposition: attachment}. The API's CSP allows scripts
 * from the app's own address, so a file served as a page or a script would run there (audit 2026-11-27 M1).
 */
public final class StoredImage {

    private static final Set<MediaType> PICTURES = Set.of(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG,
            MediaType.IMAGE_GIF, MediaType.parseMediaType("image/webp"));

    private StoredImage() {
    }

    /** A 200 with the picture's own type shown inline, or a download for anything that is not a picture. */
    public static ResponseEntity.BodyBuilder ok(Resource file) {
        String filename = file.getFilename();
        MediaType type = filename == null ? null : MediaTypeFactory.getMediaType(filename)
                .filter(PICTURES::contains)
                .orElse(null);
        ContentDisposition.Builder disposition = type == null ? ContentDisposition.attachment() : ContentDisposition.inline();
        if (filename != null) {
            disposition.filename(filename);
        }
        return ResponseEntity.ok()
                .contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : type)
                .headers(headers -> headers.setContentDisposition(disposition.build()));
    }
}
