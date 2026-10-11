package ro.mathlms.quiz;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** The picture under a question's statement: the teacher sets it; every allowed viewer reads it. */
@RestController
public class QuestionImageController {

    private final QuestionImageService service;

    public QuestionImageController(QuestionImageService service) {
        this.service = service;
    }

    @PutMapping("/api/admin/quiz-items/{itemId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> attach(@PathVariable Long itemId, @RequestParam("file") MultipartFile file) {
        service.attach(itemId, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/quiz-items/{itemId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable Long itemId) {
        service.remove(itemId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Any active account; who may actually see it is decided in the service (404 otherwise). The URL carries a
     * version (?v=), so a replaced picture gets a new address and the browser may keep each version for a while.
     */
    @GetMapping("/api/quiz/items/{itemId}/image")
    public ResponseEntity<Resource> image(@PathVariable Long itemId, Authentication auth) {
        Resource file = service.load(itemId, auth.getName());
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file);
    }
}
