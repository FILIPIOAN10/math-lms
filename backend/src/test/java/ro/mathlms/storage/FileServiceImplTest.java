package ro.mathlms.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local provider end to end, WITHOUT Spring or Docker: upload writes a uniquely-named file with
 * the right extension, delete removes it, and an external URL is ignored by delete.
 */
class FileServiceImplTest {

    private final FileServiceImpl fileService = new FileServiceImpl();

    @Test
    void uploadThenDelete(@TempDir Path dir) throws Exception {
        String path = dir.toString();
        MockMultipartFile file = new MockMultipartFile(
                "file", "rezolvare.jpg", "image/jpeg", "bytes".getBytes());

        String stored = fileService.uploadImage(path, file);

        assertThat(stored).endsWith(".jpg");
        assertThat(new File(path, stored)).exists();

        fileService.deleteImage(path, stored);
        assertThat(new File(path, stored)).doesNotExist();
    }

    @Test
    void createsNestedDirectory(@TempDir Path dir) throws Exception {
        String path = dir.resolve("uploads").resolve("quiz-photos").toString();
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", "bytes".getBytes());

        String stored = fileService.uploadImage(path, file);

        assertThat(new File(path, stored)).exists();
    }

    @Test
    void deleteIgnoresExternalUrls(@TempDir Path dir) throws Exception {
        // Must not touch the filesystem for an http(s) reference.
        fileService.deleteImage(dir.toString(), "https://cdn.example.com/a.jpg");
    }
}
