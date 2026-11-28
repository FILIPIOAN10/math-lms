package ro.mathlms.enchapter;

import jakarta.validation.constraints.NotNull;
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
import ro.mathlms.enchapter.EnChapterWrittenLessonService.WrittenLessonDto;
import ro.mathlms.user.UserRepository;

/** A chapter's new lesson (V78): every active account may ask; a student gets it once approved and shown. */
@RestController
@Validated
public class EnChapterWrittenLessonController {

    public record EditRequest(@NotNull String markdown) {
    }

    /** {@code updatedAt}: the version of the lesson the teacher read (the {@code updatedAt} their page showed). */
    public record ApproveRequest(boolean readYellow, java.time.OffsetDateTime updatedAt) {
    }

    private final EnChapterWrittenLessonService service;
    private final UserRepository userRepository;

    public EnChapterWrittenLessonController(EnChapterWrittenLessonService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    private static boolean teacher(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping("/api/en-chapters/{id}/written-lesson")
    public WrittenLessonDto lesson(@PathVariable Long id, Authentication auth) {
        return service.lesson(id, teacher(auth));
    }

    @PutMapping("/api/admin/en-chapters/{id}/written-lesson")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> edit(@PathVariable Long id, @RequestBody @Validated EditRequest request) {
        service.edit(id, request.markdown());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/en-chapters/{id}/written-lesson/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approve(@PathVariable Long id, @RequestBody ApproveRequest request, Authentication auth) {
        Long teacherId = userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
        service.approve(id, request.readYellow(), request.updatedAt(), teacherId);
        return ResponseEntity.noContent().build();
    }
}
