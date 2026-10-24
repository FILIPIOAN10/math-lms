package ro.mathlms.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * F11: files in a private S3 bucket, chosen by {@code file.storage.provider=s3}. Same contract as the local provider,
 * so nothing that stores, serves or deletes files changes: the object key is {@code <folder>/<name>} (the folder is the
 * configured directory, e.g. {@code uploads/quiz-photos}) and callers keep only the name. Nothing is ever public —
 * student photos are personal data — so a file is read back through the app, never through a bucket URL. Moving the
 * existing files across is therefore a plain copy of the {@code uploads} folder ({@code deploy/s3-migrate-uploads.sh}).
 */
@Service
@ConditionalOnProperty(name = "file.storage.provider", havingValue = "s3")
public class S3FileService implements FileService {

    /** A stored name: what upload/copy hand out (UUID + optional short extension). Anything else is refused. */
    private static final Pattern STORED_NAME = Pattern.compile("[A-Za-z0-9-]{1,64}(\\.[A-Za-z0-9]{1,5})?");

    private final MinioClient s3;
    private final String bucket;

    public S3FileService(MinioClient s3, @Value("${app.storage.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        String name = UUID.randomUUID() + FileServiceImpl.extensionOf(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            s3.putObject(PutObjectArgs.builder().bucket(bucket).object(key(path, name))
                    .stream(in, file.getSize(), -1L)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Upload to S3 failed: " + e.getMessage(), e);
        }
        return name;
    }

    @Override
    public void deleteImage(String path, String imageName) throws IOException {
        if (imageName == null || imageName.isBlank()
                || imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return;
        }
        try {
            // S3 answers a missing key with success, like the local provider ignoring a file already gone.
            s3.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key(path, imageName)).build());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Delete from S3 failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Resource loadImage(String path, String imageName) throws IOException {
        String key = key(path, imageName);
        try (GetObjectResponse object = s3.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes = object.readAllBytes(); // images and pages are small (a few MB at most)
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return imageName; // callers pick the media type and the download name from it
                }
            };
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new FileNotFoundException("Fișierul nu a fost găsit: " + imageName);
            }
            throw new IOException("Read from S3 failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Read from S3 failed: " + e.getMessage(), e);
        }
    }

    /** Read and write again (question images are small): no server-side copy, whose conditional headers vary by S3. */
    @Override
    public String copyImage(String path, String imageName) throws IOException {
        byte[] bytes = loadImage(path, imageName).getContentAsByteArray(); // FileNotFoundException when it is gone
        String copy = UUID.randomUUID() + FileServiceImpl.extensionOf(imageName);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            s3.putObject(PutObjectArgs.builder().bucket(bucket).object(key(path, copy))
                    .stream(in, (long) bytes.length, -1L).contentType("application/octet-stream").build());
        } catch (Exception e) {
            throw new IOException("Copy in S3 failed: " + e.getMessage(), e);
        }
        return copy;
    }

    /**
     * {@code uploads/quiz-photos/<name>}: the folder as configured (no leading "./" or "/", no trailing "/"), and a name
     * that can only be one we handed out — a crafted name never reaches another folder or another object.
     */
    static String key(String path, String name) throws FileNotFoundException {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            throw new FileNotFoundException("Invalid image name: " + name);
        }
        String folder = path == null ? "uploads" : path.replace('\\', '/');
        while (folder.startsWith("./") || folder.startsWith("/")) {
            folder = folder.substring(folder.startsWith("./") ? 2 : 1);
        }
        while (folder.endsWith("/")) {
            folder = folder.substring(0, folder.length() - 1);
        }
        return folder + "/" + name;
    }
}
