package ro.mathlms.enchapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.storage.FileService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;

/**
 * The files behind the chapter pages (theory photos, example figures), kept in step with the database: a file stored
 * for a change that rolls back is removed again, and a file is deleted only once its removal is committed.
 */
@Component
class EnChapterImages {

    private static final Logger log = LoggerFactory.getLogger(EnChapterImages.class);

    /** The browser shrinks photos before upload; this bounds what a direct call may store. */
    static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

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
        if (file.getContentType() == null || !TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Imaginea trebuie să fie PNG, JPG, WEBP sau GIF.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Imaginea e prea mare (maxim 4 MB).");
        }
        String stored;
        try {
            stored = fileService.uploadImage(dir, file);
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(key);
            }
        });
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    delete(key);
                }
            }
        });
    }

    private void delete(String key) {
        try {
            fileService.deleteImage(dir, key);
        } catch (IOException e) {
            log.warn("Could not delete EN chapter image {}", key, e);
        }
    }
}
