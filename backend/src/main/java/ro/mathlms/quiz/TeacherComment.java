package ro.mathlms.quiz;

/** The one rule for a teacher's free-text comment: surrounding spaces go, and a blank comment is no comment. */
final class TeacherComment {

    /** Same limit as the {@code teacher_comment} columns (V20) and the request validation. */
    static final int MAX_LENGTH = 2000;

    private TeacherComment() {
    }

    static String normalize(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.strip();
    }
}
