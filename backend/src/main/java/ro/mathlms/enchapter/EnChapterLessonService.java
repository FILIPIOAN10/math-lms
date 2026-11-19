package ro.mathlms.enchapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;

import java.util.List;
import java.util.Optional;

/**
 * A chapter's book pages written out (V73): each photo's text with the app's drawings of its figures. The AI wrote
 * them (tutor/scripts/publish_lessons.py), so the teacher reads them first and shows them to students chapter by
 * chapter; until then a student or a parent gets no page and no drawing.
 */
@Service
public class EnChapterLessonService {

    /** One page as it reads: the photo it was written from (to open the book page) and its Markdown. */
    public record LessonPageDto(Long photoId, int position, String markdown) {
    }

    public record LessonDto(boolean published, List<LessonPageDto> pages) {
    }

    private final JdbcTemplate jdbc;
    private final EnChapterRepository chapterRepository;
    private final AuditLog auditLog;

    public EnChapterLessonService(JdbcTemplate jdbc, EnChapterRepository chapterRepository, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.chapterRepository = chapterRepository;
        this.auditLog = auditLog;
    }

    private boolean published(Long chapterId) {
        List<Boolean> found = jdbc.queryForList("SELECT lesson_published FROM en_chapters WHERE id = ?", Boolean.class,
                chapterId);
        if (found.isEmpty()) {
            throw new EnChapterNotFoundException("chapter", chapterId);
        }
        return found.getFirst();
    }

    /** The pages in book order (a photo not written out yet is left out); empty for a student before it is shown. */
    @Transactional(readOnly = true)
    public LessonDto lesson(Long chapterId, boolean teacher) {
        boolean published = published(chapterId);
        if (!published && !teacher) {
            return new LessonDto(false, List.of());
        }
        List<LessonPageDto> pages = jdbc.query(
                "SELECT p.id, p.position, l.markdown FROM en_chapter_photos p JOIN en_photo_lessons l ON l.photo_id = p.id "
                        + "WHERE p.chapter_id = ? ORDER BY p.position, p.id",
                (rs, i) -> new LessonPageDto(rs.getLong(1), rs.getInt(2), rs.getString(3)), chapterId);
        return new LessonDto(published, pages);
    }

    /** A drawing of one of the chapter's pages, the same way its text is reachable. */
    @Transactional(readOnly = true)
    public Optional<byte[]> drawing(Long chapterId, Long drawingId, boolean teacher) {
        if (!teacher && !published(chapterId)) {
            return Optional.empty();
        }
        return jdbc.query("SELECT d.png FROM en_photo_drawings d JOIN en_chapter_photos p ON p.id = d.photo_id "
                        + "WHERE d.id = ? AND p.chapter_id = ?",
                (rs, i) -> rs.getBytes(1), drawingId, chapterId).stream().findFirst();
    }

    @Transactional
    public void show(Long chapterId, boolean published) {
        EnChapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("chapter", chapterId));
        jdbc.update("UPDATE en_chapters SET lesson_published = ? WHERE id = ?", published, chapterId);
        auditLog.record(AuditEvent.of(published ? AuditAction.EN_CHAPTER_LESSON_SHOWN : AuditAction.EN_CHAPTER_LESSON_HIDDEN,
                chapterId, chapter.getCode() + " · " + chapter.getTitle()));
    }
}
