package ro.mathlms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload to set a new password using a reset token. */
public record ResetPasswordRequest(

        @NotBlank
        String token,

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
) {
}
