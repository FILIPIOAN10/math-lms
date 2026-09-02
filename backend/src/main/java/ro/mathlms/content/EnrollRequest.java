package ro.mathlms.content;

import jakarta.validation.constraints.NotNull;

/** Enroll payload: the student to add to the class named in the path. */
public record EnrollRequest(@NotNull Long studentId) {
}
