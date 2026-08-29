package ro.mathlms.auth;

import ro.mathlms.user.Role;
import ro.mathlms.user.User;

public record UserDto(
        String email,
        String fullName,
        Role role
) {
    public static UserDto from(User user) {
        return new UserDto(user.getEmail(), user.getFullName(), user.getRole());
    }
}
