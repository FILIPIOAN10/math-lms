package ro.mathlms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for local (email/password) registration. The requested role is NOT
 * taken from the client — it is derived from the signed {@code inviteToken},
 * which an admin minted for a specific role.
 */
public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String fullName,

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank
        String inviteToken
) {
}
