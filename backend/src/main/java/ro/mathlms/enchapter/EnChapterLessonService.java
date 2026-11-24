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

    /**
     * One page as it reads: the photo it was written from (to open the book page), the page printed in the book (V74;
     * null when the transcript did not see one: a link to "pagina N" finds the page by it) and its Markdown.
     */
    public record LessonPageDto(Long photoId, int position, Integer bookPage, String markdown, boolean needsReview,
                                String reviewNote) {
    }

    /**
     * {@code photoCount}: the chapter's photos, written out or not, so the teacher sees what is missing (a student only
     * gets the pages); {@code needsReview}: pages the teacher has still to look at (V77). Both 0 for a student.
     */
    public record LessonDto(boolean published, int photoCount, int needsReview, List<LessonPageDto> pages) {
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

    /**
     * The pages in book order (a photo not written out yet is left out); empty for a student before it is shown, and a
     * page flagged for review after it was shown is left out for them too.
     */
    @Transactional(readOnly = true)
    public LessonDto lesson(Long chapterId, boolean teacher) {
        boolean published = published(chapterId);
        if (!published && !teacher) {
            return new LessonDto(false, 0, 0, List.of());
        }
        List<LessonPageDto> pages = jdbc.query(
                "SELECT p.id, p.position, l.book_page, l.markdown, l.needs_review, l.review_note FROM en_chapter_photos p "
                        + "JOIN en_photo_lessons l ON l.photo_id = p.id WHERE p.chapter_id = ? "
                        + (teacher ? "" : "AND NOT l.needs_review ") + "ORDER BY p.position, p.id",
                (rs, i) -> new LessonPageDto(rs.getLong(1), rs.getInt(2), (Integer) rs.getObject(3), rs.getString(4),
                        rs.getBoolean(5), rs.getString(6)),
                chapterId);
        if (!teacher) {
            return new LessonDto(true, 0, 0, pages);
        }
        int photos = photoCount(chapterId);
        return new LessonDto(published, photos, (int) pages.stream().filter(LessonPageDto::needsReview).count(), pages);
    }

    private int photoCount(Long chapterId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM en_chapter_photos WHERE chapter_id = ?", Integer.class,
                chapterId);
        return count == null ? 0 : count;
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

    /**
     * Shown only whole and checked: every photo written out and no page waiting for the teacher's look (V77). Hiding
     * is always allowed.
     */
    @Transactional
    public void show(Long chapterId, boolean published) {
        EnChapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("chapter", chapterId));
        if (published) {
            Integer missing = jdbc.queryForObject("SELECT count(*) FROM en_chapter_photos p WHERE p.chapter_id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM en_photo_lessons l WHERE l.photo_id = p.id)", Integer.class, chapterId);
            Integer flagged = jdbc.queryForObject("SELECT count(*) FROM en_photo_lessons l JOIN en_chapter_photos p "
                    + "ON p.id = l.photo_id WHERE p.chapter_id = ? AND l.needs_review", Integer.class, chapterId);
            if (missing != null && missing > 0) {
                throw new EnChapterConflictException("Lecția nu e completă: " + missing
                        + (missing == 1 ? " pagină nu e scrisă încă." : " pagini nu sunt scrise încă."));
            }
            if (flagged != null && flagged > 0) {
                throw new EnChapterConflictException("Mai ai de revizuit " + flagged
                        + (flagged == 1 ? " pagină: citește-o" : " pagini: citește-le") + " și apasă „Am verificat pagina”.");
            }
        }
        jdbc.update("UPDATE en_chapters SET lesson_published = ? WHERE id = ?", published, chapterId);
        auditLog.record(AuditEvent.of(published ? AuditAction.EN_CHAPTER_LESSON_SHOWN : AuditAction.EN_CHAPTER_LESSON_HIDDEN,
                chapterId, chapter.getCode() + " · " + chapter.getTitle()));
    }

    /** The teacher has read a flagged page against the book: it may go to students with the rest of the chapter. */
    @Transactional
    public void pageReviewed(Long chapterId, Long photoId) {
        EnChapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("chapter", chapterId));
        int changed = jdbc.update("UPDATE en_photo_lessons l SET needs_review = FALSE, review_note = NULL "
                + "FROM en_chapter_photos p WHERE p.id = l.photo_id AND l.photo_id = ? AND p.chapter_id = ?", photoId, chapterId);
        if (changed == 0) {
            throw new EnChapterNotFoundException("lesson page", photoId);
        }
        auditLog.record(AuditEvent.of(AuditAction.EN_CHAPTER_LESSON_PAGE_REVIEWED, chapterId,
                chapter.getCode() + " · " + chapter.getTitle() + " · poza " + photoId));
    }
}
