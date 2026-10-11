package ro.mathlms.audit;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small formatters for journal labels and details, so every service words them the same way. */
public final class AuditText {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(AuditLog.ZONE);

    private AuditText() {
    }

    /** The opening words of a longer text (an exercise's statement), on one line. */
    public static String snippet(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1).stripTrailing() + "…";
    }

    /** "Teză · subiectul I.3": a question as the teacher sees it numbered on screen. */
    public static String question(String quizTitle, String number) {
        return quizTitle + " · subiectul " + number;
    }

    /** A deadline or other moment, in Romania's time: "12.10.2026 18:00". */
    public static String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    /** "fost „Clasa a 9-a A”" when a name changed, else null (nothing worth saying beyond "edited"). */
    public static String renamedFrom(String oldName, String newName) {
        return Objects.equals(oldName, newName) ? null : "fost „" + oldName + "”";
    }

    /** Collects what changed in an edit: {@code changes.add("punctaj 3 → 5")}, then {@link #joined()}. */
    public static final class Changes {
        private final List<String> parts = new ArrayList<>();

        public Changes addIf(boolean changed, String what) {
            if (changed) {
                parts.add(what);
            }
            return this;
        }

        public Changes addIfDifferent(Object before, Object after, String what) {
            return addIf(!Objects.equals(before, after), what);
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        /** "enunț, punctaj 3 → 5", or null when nothing changed. */
        public String joined() {
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
    }
}
