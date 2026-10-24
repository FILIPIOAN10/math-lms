package ro.mathlms.invite;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.invite.InviteLinkDtos.CreateClassInviteRequest;
import ro.mathlms.invite.InviteLinkDtos.InviteLinkDto;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/**
 * Teacher side of the stored invite links (T1), under {@code /api/admin/...}: a TEACHER manages the links of their own
 * classes and students; the teacher-account links stay with the ADMINs.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class InviteLinkController {

    private final InviteLinkService service;
    private final TeacherScope scope;

    public InviteLinkController(InviteLinkService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/admin/classes/{classId}/invites")
    public List<InviteLinkDto> classInvites(@PathVariable Long classId) {
        scope.requireClass(classId);
        return service.activeClassInvites(classId);
    }

    /** The body is optional: no body (or no {@code maxUses}) = the default cap. */
    @PostMapping("/api/admin/classes/{classId}/invites")
    public ResponseEntity<InviteLinkDto> createClassInvite(@PathVariable Long classId,
                                                           @Valid @RequestBody(required = false) CreateClassInviteRequest request) {
        scope.requireClass(classId);
        Integer maxUses = request == null ? null : request.maxUses();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createClassInvite(classId, maxUses));
    }

    @PostMapping("/api/admin/users/{studentId}/parent-invite")
    public ResponseEntity<InviteLinkDto> createParentInvite(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createParentInvite(studentId));
    }

    @DeleteMapping("/api/admin/invite-links/{linkId}")
    public ResponseEntity<Void> revoke(@PathVariable Long linkId) {
        scope.requireInviteLink(linkId);
        service.revoke(linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/invites/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<InviteLinkDto> adminInvites() {
        return service.activeAdminInvites();
    }

    /** No body: an admin link is always single-use, valid for 24 hours, and its account waits for approval. */
    @PostMapping("/api/admin/invites/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InviteLinkDto> createAdminInvite() {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAdminInvite());
    }
}
