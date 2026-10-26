package ro.mathlms.aigrading;

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
 * The AI's proposed points for one open answer of one attempt (V44): for the whole answer and, as JSON, per requirement
 * with a short reason. Only a proposal - the teacher's grade is still what counts. People and places are plain ids.
 */
@Entity
@Table(name = "ai_grade_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGradeProposal {

    static final int ERROR_MAX = 500;
    static final int REASON_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiProposalStatus status;

    private Integer points;

    @Column(name = "parts_json", columnDefinition = "TEXT")
    private String partsJson;

    @Column(length = REASON_MAX)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AiConfidence confidence;

    @Column(length = ERROR_MAX)
    private String error;

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

    public AiGradeProposal(Long attemptId, Long itemId, Instant now) {
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.status = AiProposalStatus.QUEUED;
        this.createdAt = now;
    }

    void start(Instant now) {
        requireStatus(AiProposalStatus.QUEUED);
        status = AiProposalStatus.RUNNING;
        startedAt = now;
        attempts++;
    }

    void succeed(int points, String partsJson, String reason, AiConfidence confidence, String model,
                 Integer inputTokens, Integer outputTokens, Instant now) {
        requireStatus(AiProposalStatus.RUNNING);
        this.points = points;
        this.partsJson = partsJson;
        this.reason = reason == null || reason.length() <= REASON_MAX ? reason : reason.substring(0, REASON_MAX - 1) + "…";
        this.confidence = confidence;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.error = null;
        status = AiProposalStatus.DONE;
        finishedAt = now;
    }

    /** Failed with a reason the teacher reads (in Romanian, no stack traces). */
    void fail(String reason, Instant now) {
        status = AiProposalStatus.FAILED;
        error = reason == null ? null : (reason.length() <= ERROR_MAX ? reason : reason.substring(0, ERROR_MAX - 1) + "…");
        finishedAt = now;
    }

    /** "Cere din nou": a finished or failed proposal goes back in the queue; a waiting or running one stays as is. */
    void requeue() {
        if (status == AiProposalStatus.QUEUED || status == AiProposalStatus.RUNNING) {
            return;
        }
        status = AiProposalStatus.QUEUED;
        points = null;
        partsJson = null;
        reason = null;
        confidence = null;
        error = null;
        startedAt = null;
        finishedAt = null;
    }

    private void requireStatus(AiProposalStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("AI proposal " + id + " is " + status + ", expected " + expected);
        }
    }
}
