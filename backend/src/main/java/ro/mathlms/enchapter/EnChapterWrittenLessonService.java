package ro.mathlms.enchapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A chapter's NEW lesson (V78): written from all its sources and checked claim by claim by the tutor service
 * (tutor.synthesize, tutor.verify_lesson, tutor.store_lesson). The teacher reads it with the colours of the checks,
 * may correct the text, and approves it; a student or a parent reads it only once it is approved AND the chapter is
 * shown ("Arată-o elevilor", en_chapters.lesson_published).
 */
@Service
public class EnChapterWrittenLessonService {

    public static final int MAX_MARKDOWN = 200_000;

    /**
     * {@code exists}: there is a lesson this reader may read. Everything but the text is for the teacher only (null
     * for a student): the review's status, the colour counts, each claim with its sources.
     */
    public record WrittenLessonDto(boolean exists, String markdown, String status, Boolean editedByTeacher,
                                   Integer redCount, Integer yellowCount, String model, OffsetDateTime approvedAt,
                                   OffsetDateTime updatedAt, JsonNode claims, JsonNode sources) {
        static WrittenLessonDto none() {
            return new WrittenLessonDto(false, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private final JdbcTemplate jdbc;
    private final EnChapterRepository chapterRepository;
    private final AuditLog auditLog;
    private final ObjectMapper objectMapper;

    public EnChapterWrittenLessonService(JdbcTemplate jdbc, EnChapterRepository chapterRepository, AuditLog auditLog,
                                         ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.chapterRepository = chapterRepository;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    private EnChapter chapter(Long id) {
        return chapterRepository.findById(id).orElseThrow(() -> new EnChapterNotFoundException("chapter", id));
    }

    private Map<String, Object> row(Long chapterId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT l.*, c.lesson_published FROM en_chapter_lessons l JOIN en_chapters c ON c.id = l.chapter_id "
                        + "WHERE l.chapter_id = ?", chapterId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static OffsetDateTime time(Object value) {
        return value instanceof java.sql.Timestamp t ? t.toInstant().atOffset(java.time.ZoneOffset.UTC)
                : value instanceof OffsetDateTime o ? o : null;
    }

    @Transactional(readOnly = true)
    public WrittenLessonDto lesson(Long chapterId, boolean teacher) {
        chapter(chapterId);
        Map<String, Object> row = row(chapterId);
        if (row == null) {
            return WrittenLessonDto.none();
        }
        if (!teacher) {
            boolean visible = "approved".equals(row.get("status")) && Boolean.TRUE.equals(row.get("lesson_published"));
            return visible ? new WrittenLessonDto(true, (String) row.get("markdown"), null, null, null, null, null,
                    null, null, null, null) : WrittenLessonDto.none();
        }
        return new WrittenLessonDto(true, (String) row.get("markdown"), (String) row.get("status"),
                (Boolean) row.get("edited_by_teacher"), (Integer) row.get("red_count"), (Integer) row.get("yellow_count"),
                (String) row.get("model"), time(row.get("approved_at")), time(row.get("updated_at")),
                objectMapper.readTree((String) row.get("claims_json")),
                objectMapper.readTree((String) row.get("sources_json")));
    }

    /**
     * The teacher's own text: never replaced by a later generation, and its approval starts over (a student must not
     * read a text nobody approved). The claim colours stay, describing the text the AI wrote.
     */
    @Transactional
    public void edit(Long chapterId, String markdown) {
        EnChapter chapter = chapter(chapterId);
        String text = markdown == null ? "" : markdown.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Lecția nu poate fi goală.");
        }
        if (text.length() > MAX_MARKDOWN) {
            throw new IllegalArgumentException("Lecția poate avea cel mult " + MAX_MARKDOWN + " de caractere.");
        }
        int changed = jdbc.update("UPDATE en_chapter_lessons SET markdown = ?, edited_by_teacher = TRUE, "
                + "status = CASE WHEN status = 'approved' THEN 'draft' ELSE status END, approved_by = NULL, "
                + "approved_at = NULL, updated_at = now() WHERE chapter_id = ?", text, chapterId);
        if (changed == 0) {
            throw new EnChapterNotFoundException("lesson", chapterId);
        }
        auditLog.record(AuditEvent.of(AuditAction.EN_LESSON_EDITED, chapterId, label(chapter)));
    }

    /**
     * Refused while a red claim describes the text (unless the teacher has rewritten it: the colours were for the
     * AI's text) and until the teacher says they read the yellow ones.
     */
    @Transactional
    public void approve(Long chapterId, boolean readYellow, Long teacherId) {
        EnChapter chapter = chapter(chapterId);
        Map<String, Object> row = row(chapterId);
        if (row == null) {
            throw new EnChapterNotFoundException("lesson", chapterId);
        }
        boolean edited = Boolean.TRUE.equals(row.get("edited_by_teacher"));
        int red = (Integer) row.get("red_count");
        int yellow = (Integer) row.get("yellow_count");
        if (red > 0 && !edited) {
            throw new EnChapterConflictException("Lecția are " + red + (red == 1 ? " afirmație roșie" : " afirmații roșii")
                    + ": corecteaz-o (sau generează-o din nou) înainte s-o aprobi.");
        }
        if (yellow > 0 && !edited && !readYellow) {
            throw new IllegalArgumentException("Bifează că ai citit afirmațiile galbene.");
        }
        jdbc.update("UPDATE en_chapter_lessons SET status = 'approved', approved_by = ?, approved_at = now() "
                + "WHERE chapter_id = ?", teacherId, chapterId);
        auditLog.record(AuditEvent.of(AuditAction.EN_LESSON_APPROVED, chapterId, label(chapter)));
    }

    private static String label(EnChapter chapter) {
        return chapter.getCode() + " · " + chapter.getTitle();
    }
}
