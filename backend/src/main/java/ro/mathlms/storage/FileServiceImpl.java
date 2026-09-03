package ro.mathlms.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * The default provider: store files on the local filesystem under {@code path}. Active when
 * {@code file.storage.provider} is absent or {@code local}. Good for dev and single-node; for
 * multi-node or durability, add the S3 provider later.
 *
 * <p>Returns the generated filename (not a URL) — serve it back via a download endpoint / resource
 * handler mapped at the public image path.
 */
@Service
@ConditionalOnProperty(name = "file.storage.provider", havingValue = "local", matchIfMissing = true)
public class FileServiceImpl implements FileService {

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        String filePath = path + File.separator + fileName;

        // mkdirs (not mkdir) so nested paths like "uploads/quiz-photos" are created.
        File folder = new File(path);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create storage directory: " + folder.getAbsolutePath());
        }

        Files.copy(file.getInputStream(), Paths.get(filePath));
        return fileName;
    }

    @Override
    public void deleteImage(String path, String imageName) throws IOException {
        if (imageName == null || imageName.isBlank()
                || imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return;
        }
        File file = new File(path + File.separator + imageName);
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete image: " + file.getAbsolutePath());
        }
    }

    /** ".jpg" for "photo.jpg"; "" when there is no filename or no dot. */
    private static String extensionOf(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
