package ro.mathlms.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
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
}
