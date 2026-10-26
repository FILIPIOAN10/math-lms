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
 * One reading of a test's official barem from photos (V44). The worker moves it QUEUED -> RUNNING -> DONE | FAILED,
 * like an {@link AiImport}; the photos are deleted once read, what the AI read stays as JSON until the teacher applies it.
 */
@Entity
@Table(name = "ai_barem_reads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaremRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiImportStatus status;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "page_keys", nullable = false, columnDefinition = "TEXT")
    private String pageKeys;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = AiImport.ERROR_MAX)
    private String error;

    @Column(length = 80)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public BaremRead(Long quizId, Long teacherId, int pageCount, String pageKeys, Instant now) {
        this.quizId = Objects.requireNonNull(quizId, "quizId");
        this.teacherId = Objects.requireNonNull(teacherId, "teacherId");
        this.pageCount = pageCount;
        this.pageKeys = Objects.requireNonNull(pageKeys, "pageKeys");
        this.status = AiImportStatus.QUEUED;
        this.createdAt = now;
    }

    void start(Instant now) {
        requireStatus(AiImportStatus.QUEUED);
        status = AiImportStatus.RUNNING;
        startedAt = now;
    }

    void succeed(String resultJson, String model, Integer inputTokens, Integer outputTokens, Instant now) {
        requireStatus(AiImportStatus.RUNNING);
        this.resultJson = Objects.requireNonNull(resultJson, "resultJson");
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
        error = reason == null ? null
                : (reason.length() <= AiImport.ERROR_MAX ? reason : reason.substring(0, AiImport.ERROR_MAX - 1) + "…");
        finishedAt = now;
    }

    /** The photos are deleted once read: only what the AI read is kept. */
    void forgetPages() {
        pageKeys = "[]";
    }

    private void requireStatus(AiImportStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Barem read " + id + " is " + status + ", expected " + expected);
        }
    }
}
