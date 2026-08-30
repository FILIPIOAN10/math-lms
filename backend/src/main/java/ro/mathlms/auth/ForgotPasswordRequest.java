package ro.mathlms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload to request a password-reset email. */
public record ForgotPasswordRequest(

        @NotBlank
        @Email
        String email
) {
}
