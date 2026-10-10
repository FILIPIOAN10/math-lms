package ro.mathlms.auth;

import ro.mathlms.user.Role;

/**
 * What "Contul meu" shows about the signed-in account. {@code hasPassword} decides whether the page offers a password
 * change (and asks for the password before an erasure); {@code googleLinked} says Google sign-in works for it too.
 */
public record AccountDto(
        String email,
        String fullName,
        Role role,
        boolean hasPassword,
        boolean googleLinked
) {}
