package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Contul meu", under {@code /api/me/...}: any approved account (STATUS_ACTIVE, in {@code SecurityConfig}), always the
 * caller's own - the account comes from the principal, never from the request. The data export and erasure live in
 * {@code GdprController}; the device sessions in {@code AuthController}.
 */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;

    public AccountController(AccountService accountService, RefreshTokenService refreshTokenService) {
        this.accountService = accountService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/account")
    public AccountDto account(Authentication auth) {
        return accountService.account(auth.getName());
    }

    /** 204 on success; a wrong current password is a 400 with the reason (a 401 would make the SPA refresh and replay). */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication auth, HttpServletRequest http) {
        accountService.changePassword(auth.getName(), request.currentPassword(), request.newPassword(),
                refreshTokenService.readRefreshCookie(http));
        return ResponseEntity.noContent().build();
    }
}
