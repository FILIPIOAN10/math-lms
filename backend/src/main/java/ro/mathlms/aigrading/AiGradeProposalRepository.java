package ro.mathlms.aigrading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiGradeProposalRepository extends JpaRepository<AiGradeProposal, Long> {

    /**
     * Claims a waiting proposal, the same question's answers one after another (oldest first within it): every request
     * then starts with the same instructions, problem, barem and figure, which Gemini's prefix cache bills for less. A
     * paper handed in later may wait behind a busy question; grading is not urgent. {@code SKIP LOCKED} lets two app
     * instances work side by side.
     */
    @Query(value = """
            SELECT * FROM ai_grade_proposals
            WHERE status = 'QUEUED'
            ORDER BY item_id, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<AiGradeProposal> claimNext();

    List<AiGradeProposal> findByAttemptIdOrderByItemId(Long attemptId);

    Optional<AiGradeProposal> findByAttemptIdAndItemId(Long attemptId, Long itemId);

    /** Answers the AI started on since {@code since}: the daily limit, for the whole app. */
    long countByStartedAtGreaterThanEqual(Instant since);

    /** Worked on for too long: the app restarted (or hung) mid-call. */
    List<AiGradeProposal> findByStatusAndStartedAtBefore(AiProposalStatus status, Instant before);
}
