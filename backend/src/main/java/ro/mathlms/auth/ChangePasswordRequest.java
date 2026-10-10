package ro.mathlms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The current password proves it is really the owner at the keyboard; the new one follows the registration rule. */
public record ChangePasswordRequest(
        @NotBlank(message = "Scrie parola actuală")
        String currentPassword,

        // 72 = BCrypt's limit: anything longer would be silently cut, so a longer "password" would not be what is checked
        @NotBlank(message = "Scrie parola nouă")
        @Size(min = 8, max = 72, message = "Parola nouă trebuie să aibă între 8 și 72 de caractere")
        String newPassword
) {}
