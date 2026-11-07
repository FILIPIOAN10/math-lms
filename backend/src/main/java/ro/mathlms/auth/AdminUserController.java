package ro.mathlms.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.teaching.TeacherScope;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

/**
 * Admin onboarding endpoints. Access is enforced twice on purpose: the URL rule
 * {@code /api/admin/**} in {@link SecurityConfig} and the method-level
 * {@code @PreAuthorize} here — belt and suspenders, with the intent visible at the method.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    private final TeacherScope scope;

    public AdminUserController(AdminUserService adminUserService, TeacherScope scope) {
        this.adminUserService = adminUserService;
        this.scope = scope;
    }

    /**
     * Active accounts of a given role, for the linking screen: {@code ?role=STUDENT} for
     * the rows to link, {@code ?role=PARENT} for the parent options. A TEACHER may only ask for
     * students, and gets the ones enrolled in their own classes.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<AdminUserSummaryDto>> byRole(@RequestParam Role role) {
        if (scope.restricted() && role != Role.STUDENT) {
            throw new AccessDeniedException("A teacher lists only their own students");
        }
        List<AdminUserSummaryDto> users = adminUserService.listActiveByRole(role).stream()
                .map(AdminUserSummaryDto::from)
                .toList();
        return ResponseEntity.ok(scope.keepOwnedStudents(users, AdminUserSummaryDto::id));
    }

    /** Accounts awaiting an admin decision, for the review screen. */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingUserDto>> pending() {
        List<PendingUserDto> pending = adminUserService.listPending().stream()
                .map(PendingUserDto::from)
                .toList();
        return ResponseEntity.ok(pending);
    }

    /** Approves a pending account, assigning the real role the admin confirms. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<UserDto> approve(@PathVariable Long id,
                                           @Valid @RequestBody ApproveRequest request) {
        User approved = adminUserService.approve(id, request.role());
        return ResponseEntity.ok(UserDto.from(approved));
    }

    /** Rejects a pending account without deleting it. */
    @PostMapping("/{id}/reject")
    public ResponseEntity<UserDto> reject(@PathVariable Long id) {
        User rejected = adminUserService.reject(id);
        return ResponseEntity.ok(UserDto.from(rejected));
    }

    /** Links the student in the path to the parent account named in the body. */
    @PostMapping("/{id}/link-parent")
    public ResponseEntity<UserDto> linkParent(@PathVariable Long id,
                                              @Valid @RequestBody LinkParentRequest request) {
        User student = adminUserService.linkParent(id, request.parentId());
        return ResponseEntity.ok(UserDto.from(student));
    }

    /** Extra time on timed tests for one student (0, 25, 50 or 100 %); a TEACHER only for their own students. */
    @PutMapping("/{id}/extra-time")
    public ResponseEntity<String> changeExtraTime(@PathVariable Long id, @Valid @RequestBody ExtraTimeRequest request) {
        scope.requireStudent(id);
        try {
            adminUserService.changeExtraTime(id, request.percent());
        } catch (IllegalArgumentException e) { // a step that is not allowed, or an account that is not a student
            return ResponseEntity.badRequest().body("Timpul suplimentar poate fi doar 0, 25, 50 sau 100 % și doar pentru un elev.");
        }
        return ResponseEntity.noContent().build();
    }

    public record ExtraTimeRequest(@NotNull Integer percent) {
    }
}
