package ro.mathlms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ro.mathlms.user.Role;

/** Payload for local (email/password) registration. */
public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String fullName,

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull
        Role requestedRole
) {
}
