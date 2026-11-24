package ro.mathlms.enchapter;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.enchapter.EnChapterLessonService.LessonDto;

import java.time.Duration;

/**
 * A chapter's book pages written out, with the app's drawings (V73). Every active account may ask; a student or a
 * parent gets them only once the teacher has shown them.
 */
@RestController
@Validated
public class EnChapterLessonController {

    public record ShowRequest(@NotNull Boolean published) {
    }

    private final EnChapterLessonService service;

    public EnChapterLessonController(EnChapterLessonService service) {
        this.service = service;
    }

    private static boolean teacher(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping("/api/en-chapters/{id}/lesson")
    public LessonDto lesson(@PathVariable Long id, Authentication auth) {
        return service.lesson(id, teacher(auth));
    }

    /** A drawing is replaced by a new one with a new id, so the browser may keep it a while. */
    @GetMapping("/api/en-chapters/{id}/drawings/{drawingId}")
    public ResponseEntity<byte[]> drawing(@PathVariable Long id, @PathVariable Long drawingId, Authentication auth) {
        return service.drawing(id, drawingId, teacher(auth))
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/admin/en-chapters/{id}/lesson")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> show(@PathVariable Long id, @RequestBody @Validated ShowRequest request) {
        service.show(id, request.published());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/en-chapters/{id}/lesson/pages/{photoId}/reviewed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> pageReviewed(@PathVariable Long id, @PathVariable Long photoId) {
        service.pageReviewed(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
