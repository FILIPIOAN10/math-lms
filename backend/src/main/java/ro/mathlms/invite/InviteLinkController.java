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

import java.util.List;

/** Teacher side of the stored invite links (T1). All routes are under {@code /api/admin/...} (ADMIN). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class InviteLinkController {

    private final InviteLinkService service;

    public InviteLinkController(InviteLinkService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/classes/{classId}/invites")
    public List<InviteLinkDto> classInvites(@PathVariable Long classId) {
        return service.activeClassInvites(classId);
    }

    /** The body is optional: no body (or no {@code maxUses}) = the default cap. */
    @PostMapping("/api/admin/classes/{classId}/invites")
    public ResponseEntity<InviteLinkDto> createClassInvite(@PathVariable Long classId,
                                                           @Valid @RequestBody(required = false) CreateClassInviteRequest request) {
        Integer maxUses = request == null ? null : request.maxUses();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createClassInvite(classId, maxUses));
    }

    @PostMapping("/api/admin/users/{studentId}/parent-invite")
    public ResponseEntity<InviteLinkDto> createParentInvite(@PathVariable Long studentId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createParentInvite(studentId));
    }

    @DeleteMapping("/api/admin/invite-links/{linkId}")
    public ResponseEntity<Void> revoke(@PathVariable Long linkId) {
        service.revoke(linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/invites/admin")
    public List<InviteLinkDto> adminInvites() {
        return service.activeAdminInvites();
    }

    @PostMapping("/api/admin/invites/admin")
    public ResponseEntity<InviteLinkDto> createAdminInvite(
            @Valid @RequestBody(required = false) InviteLinkDtos.CreateAdminInviteRequest request) {
        Integer maxUses = request == null ? null : request.maxUses();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAdminInvite(maxUses));
    }
}
