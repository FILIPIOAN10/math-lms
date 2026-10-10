package ro.mathlms.quiz;

import jakarta.validation.constraints.Size;

/** The teacher's comment on a whole paper; empty or blank removes it. */
public record AttemptCommentRequest(
        @Size(max = TeacherComment.MAX_LENGTH, message = "Comentariul poate avea cel mult 2000 de caractere")
        String comment
) {}
