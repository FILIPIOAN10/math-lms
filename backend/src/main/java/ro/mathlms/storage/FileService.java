package ro.mathlms.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * The storage seam. Callers depend on this interface only; which implementation answers is chosen
 * at boot by {@code file.storage.provider} (local | s3), so switching providers is a config change,
 * never a code change. Only the local provider ships now (Faza Q6); an S3/MinIO provider can be
 * dropped in later without touching callers.
 *
 * <p>Contract note — the reference {@link #uploadImage} returns differs by provider: the local impl
 * returns a bare stored filename (served back via a resource handler / download endpoint), while an
 * S3 impl would return an absolute URL. {@link #deleteImage} accepts whatever its own upload returned.
 */
public interface FileService {

    /** Stores the file under {@code path} and returns a reference to it (filename or URL, per provider). */
    String uploadImage(String path, MultipartFile file) throws IOException;

    /** Removes the file previously stored under the given reference. No-op for external URLs. */
    void deleteImage(String path, String imageName) throws IOException;
}
