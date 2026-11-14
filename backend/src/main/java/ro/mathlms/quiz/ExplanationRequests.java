package ro.mathlms.quiz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * „De ce am greșit?" requests ({@code mistake_explanation_requests}, V70): which students are waiting for the teacher to
 * explain a wrong option. One row per (option, student), so asking twice counts once; a saved explanation deletes the
 * option's rows. Plain JDBC: there is nothing to load as an entity, only counts and ids.
 */
@Component
public class ExplanationRequests {

    /** A test with options students asked about: how many options, how many students, since when. */
    public record PendingDto(Long quizId, String quizTitle, int options, int students, Instant oldest) {
    }

    /** One of a student's requests, for their data export. */
    public record OwnRequest(String statement, String chosenOption, Instant requestedAt) {
    }

    private final JdbcTemplate jdbc;

    public ExplanationRequests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Notes the request; false when this student had already asked about this option. */
    boolean add(Long optionId, Long studentId, Instant at) {
        return jdbc.update("INSERT INTO mistake_explanation_requests (option_id, student_id, requested_at) VALUES (?, ?, ?)"
                + " ON CONFLICT (option_id, student_id) DO NOTHING", optionId, studentId, Timestamp.from(at)) > 0;
    }

    /** Of these options, the ones the student is waiting about. */
    Set<Long> askedBy(Long studentId, Collection<Long> optionIds) {
        if (optionIds.isEmpty()) {
            return Set.of();
        }
        String marks = String.join(",", optionIds.stream().map(id -> "?").toList());
        Object[] args = new Object[optionIds.size() + 1];
        args[0] = studentId;
        int i = 1;
        for (Long id : optionIds) {
            args[i++] = id;
        }
        return new HashSet<>(jdbc.queryForList("SELECT option_id FROM mistake_explanation_requests WHERE student_id = ?"
                + " AND option_id IN (" + marks + ")", Long.class, args));
    }

    /** How many students are waiting, per option of one quiz. */
    Map<Long, Integer> countsForQuiz(Long quizId) {
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query("""
                        SELECT r.option_id, count(*) AS n FROM mistake_explanation_requests r
                        JOIN quiz_options o ON o.id = r.option_id JOIN quiz_items i ON i.id = o.item_id
                        WHERE i.quiz_id = ? GROUP BY r.option_id""",
                rs -> {
                    counts.put(rs.getLong("option_id"), rs.getInt("n"));
                }, quizId);
        return counts;
    }

    /** The tests with requests, oldest first; {@code teacherId} null = every test (an ADMIN). */
    public List<PendingDto> pending(Long teacherId) {
        return jdbc.query("""
                        SELECT q.id, q.title, count(DISTINCT r.option_id) AS options,
                               count(DISTINCT r.student_id) AS students, min(r.requested_at) AS oldest
                        FROM mistake_explanation_requests r
                        JOIN quiz_options o ON o.id = r.option_id JOIN quiz_items i ON i.id = o.item_id
                        JOIN quizzes q ON q.id = i.quiz_id
                        WHERE (?::bigint IS NULL OR q.teacher_id = ?)
                        GROUP BY q.id, q.title ORDER BY min(r.requested_at)""",
                (rs, n) -> new PendingDto(rs.getLong("id"), rs.getString("title"), rs.getInt("options"),
                        rs.getInt("students"), rs.getTimestamp("oldest").toInstant()),
                teacherId, teacherId);
    }

    /** The answered option's requests are done. */
    void deleteForOption(Long optionId) {
        jdbc.update("DELETE FROM mistake_explanation_requests WHERE option_id = ?", optionId);
    }

    /** GDPR erasure. */
    public void deleteForStudent(Long studentId) {
        jdbc.update("DELETE FROM mistake_explanation_requests WHERE student_id = ?", studentId);
    }

    /** GDPR export: what the student asked about, newest first. */
    public List<OwnRequest> ofStudent(Long studentId) {
        return jdbc.query("""
                        SELECT i.statement, o.text, r.requested_at FROM mistake_explanation_requests r
                        JOIN quiz_options o ON o.id = r.option_id JOIN quiz_items i ON i.id = o.item_id
                        WHERE r.student_id = ? ORDER BY r.requested_at DESC""",
                (rs, n) -> new OwnRequest(rs.getString("statement"), rs.getString("text"),
                        rs.getTimestamp("requested_at").toInstant()),
                studentId);
    }
}
