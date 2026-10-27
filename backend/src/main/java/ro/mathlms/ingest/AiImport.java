package ro.mathlms.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * One upload of textbook pages to be read by the AI (Faza 3). The worker moves it QUEUED -> RUNNING -> DONE | FAILED;
 * the teacher then turns the questions into a draft quiz. People and places are plain ids (like the journal), the
 * pages and the questions are JSON text the service reads and writes.
 */
@Entity
@Table(name = "ai_imports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiImport {

    static final int ERROR_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "school_class_id")
    private Long schoolClassId;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiImportStatus status;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "page_keys", nullable = false, columnDefinition = "TEXT")
    private String pageKeys;

    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(length = ERROR_MAX)
    private String error;

    @Column(length = 30)
    private String provider;

    @Column(length = 80)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "quiz_id")
    private Long quizId;

    /** SHA-256 of the pages as stored, in order (V45): the same pages again reuse this reading. */
    @Column(name = "pages_hash", length = 64)
    private String pagesHash;

    /** The import whose reading this one copied instead of asking the AI; null = read by the AI. */
    @Column(name = "reused_from")
    private Long reusedFrom;

    public AiImport(Long teacherId, Long schoolClassId, Long chapterId, int pageCount, String pageKeys, Instant now) {
        this.teacherId = Objects.requireNonNull(teacherId, "teacherId");
        this.schoolClassId = schoolClassId;
        this.chapterId = chapterId;
        this.pageCount = pageCount;
        this.pageKeys = Objects.requireNonNull(pageKeys, "pageKeys");
        this.status = AiImportStatus.QUEUED;
        this.createdAt = now;
    }

    /** Fingerprint of the pages, set when they are stored. */
    void fingerprint(String pagesHash) {
        this.pagesHash = pagesHash;
    }

    /**
     * Done at once with {@code source}'s reading (the same pages): no AI call, so no tokens - {@code items} is the
     * reading, in the order normalisation gives today.
     */
    void reuse(AiImport source, String items, Instant now) {
        requireStatus(AiImportStatus.QUEUED);
        this.itemsJson = Objects.requireNonNull(items, "items");
        this.provider = source.provider;
        this.model = source.model;
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.reusedFrom = source.id;
        status = AiImportStatus.DONE;
        finishedAt = now;
    }

    /** The worker took it. */
    void start(Instant now) {
        requireStatus(AiImportStatus.QUEUED);
        status = AiImportStatus.RUNNING;
        startedAt = now;
        attempts++;
    }

    void succeed(String itemsJson, String provider, String model, Integer inputTokens, Integer outputTokens, Instant now) {
        requireStatus(AiImportStatus.RUNNING);
        this.itemsJson = Objects.requireNonNull(itemsJson, "itemsJson");
        this.provider = provider;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.error = null;
        status = AiImportStatus.DONE;
        finishedAt = now;
    }

    /** Failed with a reason the teacher reads (in Romanian, no stack traces). */
    void fail(String reason, Instant now) {
        status = AiImportStatus.FAILED;
        error = reason == null ? null : (reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX - 1) + "…");
        finishedAt = now;
    }

    /**
     * "Încearcă din nou" on a failed import whose pages are still there, or "Citește din nou cu AI" on a reused one:
     * back in the queue for a fresh reading.
     */
    void requeue() {
        if (!(status == AiImportStatus.FAILED || (status == AiImportStatus.DONE && reusedFrom != null))) {
            throw new IllegalStateException("AI import " + id + " is " + status + ", expected FAILED or a reused DONE");
        }
        status = AiImportStatus.QUEUED;
        itemsJson = null;
        reusedFrom = null;
        error = null;
        startedAt = null;
        finishedAt = null;
    }

    void linkQuiz(Long quizId) {
        this.quizId = quizId;
    }

    /** The page files are gone (quiz made, or expired): nothing left to show or to read again. */
    void forgetPages() {
        pageKeys = "[]";
    }

    boolean hasPages() {
        return !"[]".equals(pageKeys);
    }

    private void requireStatus(AiImportStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("AI import " + id + " is " + status + ", expected " + expected);
        }
    }
}
