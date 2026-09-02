package ro.mathlms.content;

/** An enrollment as the admin roster shows it: the enrollment id plus who the student is. */
public record EnrollmentDto(Long id, Long studentId, String studentName, String studentEmail) {
    public static EnrollmentDto from(Enrollment enrollment) {
        return new EnrollmentDto(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getFullName(),
                enrollment.getStudent().getEmail());
    }
}
