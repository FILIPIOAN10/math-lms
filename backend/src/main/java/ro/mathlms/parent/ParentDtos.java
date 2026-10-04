package ro.mathlms.parent;

import ro.mathlms.user.User;

/** Response DTOs of the parent API. */
public final class ParentDtos {

    private ParentDtos() {
    }

    /** A child of the logged-in parent. */
    public record ChildDto(Long id, String fullName, String email) {
        public static ChildDto from(User student) {
            return new ChildDto(student.getId(), student.getFullName(), student.getEmail());
        }
    }
}
