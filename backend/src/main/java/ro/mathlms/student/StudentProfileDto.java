package ro.mathlms.student;

import ro.mathlms.user.AccountStatus;

import java.util.List;

/** Who the student is, for the head of the teacher's student page: classes and the linked parent. */
public record StudentProfileDto(
        Long id,
        String fullName,
        String email,
        AccountStatus status,
        List<ClassRef> classes,
        Long parentId,       // null = no parent linked
        String parentName,
        String parentEmail
) {
    public record ClassRef(Long id, String name) {
    }
}
