package ro.mathlms.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // mkdirs (not mkdir) so nested paths like "uploads/quiz-photos" are created.
        File folder = new File(path);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create storage directory: " + folder.getAbsolutePath());
        }

        Path base = Paths.get(path).toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        // defense in depth, like loadImage: never outside the folder, and right in it (not a sub-folder)
        if (!target.startsWith(base) || !base.equals(target.getParent())) {
            throw new IOException("Invalid file name: " + fileName);
        }
        Files.copy(file.getInputStream(), target);
        return fileName;
    }

    @Override
    public void deleteImage(String path, String imageName) throws IOException {
        if (imageName == null || imageName.isBlank()
                || imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return;
        }
        Path base = Paths.get(path).toAbsolutePath().normalize();
        Path target = base.resolve(imageName).normalize();
        if (!target.startsWith(base) || target.equals(base)) { // a crafted name never deletes outside the folder
            throw new IOException("Invalid image name: " + imageName);
        }
        File file = target.toFile();
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete image: " + file.getAbsolutePath());
        }
    }

    @Override
    public String copyImage(String path, String imageName) throws IOException {
        Path base = Paths.get(path).toAbsolutePath().normalize();
        Path source = base.resolve(imageName).normalize();
        if (!source.startsWith(base) || !Files.isReadable(source)) {
            throw new FileNotFoundException("Image not found: " + imageName);
        }
        String copyName = UUID.randomUUID() + extensionOf(imageName);
        Files.copy(source, base.resolve(copyName));
        return copyName;
    }

    /** ".jpg" for "photo.jpg"; "" when there is no filename or no dot. */
    /**
     * ".jpg" from the browser-sent file name - which the uploader controls, so anything but a short alphanumeric
     * extension (".b/../../x", ".ph p") is dropped: the stored name is then just the UUID. The content type was
     * already checked by the caller; the extension only helps a human browsing the folder.
     */
    static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalFilename.substring(dot + 1);
        return SAFE_EXTENSION.matcher(extension).matches() ? "." + extension : "";
    }

    private static final java.util.regex.Pattern SAFE_EXTENSION = java.util.regex.Pattern.compile("[A-Za-z0-9]{1,5}");
    @Override
    public Resource loadImage(String path, String imageName) throws IOException {
        Path base = Paths.get(path).toAbsolutePath().normalize();
        Path filePath = base.resolve(imageName).normalize();
        // Defense in depth: never let a crafted name escape the storage directory.
        if (!filePath.startsWith(base)) {
            throw new FileNotFoundException("Invalid image name: " + imageName);
        }
        Resource resource = new UrlResource(filePath.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new FileNotFoundException("Fișierul nu a fost găsit sau nu poate fi citit: " + imageName);
        }
    }
}
