package ro.mathlms.gdpr;

import java.util.List;

/**
 * The shape of a user's GDPR Art. 15 data export (serialized to {@code manifest.json} inside the
 * ZIP). Timestamps are ISO-8601 strings so the archive is stable and self-describing regardless of
 * Jackson date configuration. Uploaded rezolvare photos are added to the ZIP alongside this manifest.
 */
public final class GdprExportData {

    private GdprExportData() {
    }

    public record Profile(
            String email,
            String fullName,
            String role,
            String status,
            boolean emailVerified,
            boolean googleLinked,
            String parentEmail
    ) {}

    public record EnrolledClass(String className) {}

    public record ResponseEntry(
            int itemPosition,
            String itemType,
            String itemStatement,
            String selectedOptionText,
            String photoFile,
            Integer awardedPoints,
            Boolean correct
    ) {}

    public record Attempt(
            String quizTitle,
            String status,
            Integer score,
            String startedAt,
            String submittedAt,
            List<ResponseEntry> responses
    ) {}

    public record Archive(
            String exportedAt,
            Profile profile,
            List<EnrolledClass> enrollments,
            List<Attempt> attempts
    ) {}
}
