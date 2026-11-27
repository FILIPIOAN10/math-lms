package ro.mathlms.enchapter;

import ro.mathlms.common.AfterTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.storage.FileService;
import ro.mathlms.storage.ImageUploadSanitizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * The files behind the chapter pages (theory photos, example figures), kept in step with the database: a file stored
 * for a change that rolls back is removed again, and a file is deleted only once its removal is committed.
 */
@Component
class EnChapterImages {

    private static final Logger log = LoggerFactory.getLogger(EnChapterImages.class);

    /** The browser shrinks photos before upload; this bounds what a direct call may store. */
    static final long MAX_BYTES = ImageUploadSanitizer.PAGE_IMAGE_MAX_BYTES;

    private final FileService fileService;
    private final String dir;

    EnChapterImages(FileService fileService, @Value("${app.storage.en-chapter-images-dir}") String dir) {
        this.fileService = fileService;
        this.dir = dir;
    }

    /** Checks and stores an uploaded picture; returns its stored name. Call inside the transaction that references it. */
    String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Alege o imagine.");
        }
        // Only the bytes decide (not the type or the name sent): stored re-encoded as PNG or JPEG (audit 2026-11-27 M1).
        MultipartFile clean = ImageUploadSanitizer.sanitize(file, MAX_BYTES);
        String stored;
        try {
            stored = fileService.uploadImage(dir, clean);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut salva imaginea", e);
        }
        whenRolledBack(stored);
        return stored;
    }

    /** Deletes a file once the change that dropped it is committed. Null = nothing to do. */
    void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        AfterTransaction.afterCommit(() -> delete(key));
    }

    /** The stored file, or empty when it is gone from the disk. */
    Optional<Resource> load(String key) {
        try {
            return Optional.of(fileService.loadImage(dir, key));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void whenRolledBack(String key) {
        AfterTransaction.ifRolledBack(() -> delete(key));
    }

    private void delete(String key) {
        try {
            fileService.deleteImage(dir, key);
        } catch (IOException e) {
            log.warn("Could not delete EN chapter image {}", key, e);
        }
    }
}
