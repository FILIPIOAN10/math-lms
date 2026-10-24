package ro.mathlms.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F8 on "Contul meu": a staff member sets up, checks or turns off two-step sign-in for their own account. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TwoFactorController {

    public record CodeRequest(@NotBlank String code) {
    }

    public record RecoveryCodesDto(List<String> recoveryCodes) {
    }

    private final TwoFactorService service;

    public TwoFactorController(TwoFactorService service) {
        this.service = service;
    }

    @GetMapping("/api/me/2fa")
    public TwoFactorService.Status status(Authentication auth) {
        return service.status(auth.getName());
    }

    /** A new secret for the app (QR + text); nothing changes until {@code /enable} proves the app has it. */
    @PostMapping("/api/me/2fa/setup")
    public TwoFactorService.Setup setup(Authentication auth) {
        return service.setup(auth.getName());
    }

    /** Turns it on with the app's first code; the recovery codes, shown this once. */
    @PostMapping("/api/me/2fa/enable")
    public RecoveryCodesDto enable(@RequestBody @jakarta.validation.Valid CodeRequest request, Authentication auth) {
        return new RecoveryCodesDto(service.enable(auth.getName(), request.code()));
    }

    @PostMapping("/api/me/2fa/recovery-codes")
    public RecoveryCodesDto recoveryCodes(@RequestBody @jakarta.validation.Valid CodeRequest request, Authentication auth) {
        return new RecoveryCodesDto(service.regenerateRecoveryCodes(auth.getName(), request.code()));
    }

    @PostMapping("/api/me/2fa/disable")
    public ResponseEntity<Void> disable(@RequestBody @jakarta.validation.Valid CodeRequest request, Authentication auth) {
        service.disable(auth.getName(), request.code());
        return ResponseEntity.noContent().build();
    }
}
