package ro.mathlms.studyplan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.mastery.EnPrediction;
import ro.mathlms.user.User;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the study plan is worked out from (V84): the school's EN exam date, each student's target grade, the teacher's
 * pins, and the estimate kept day by day for the chart. The exam date and the pins are teachers' changes, so each goes
 * into the journal in the same transaction; a student's own target does not.
 */
@Service
public class EnGoalService {

    /** Days are school days in Romania, whatever the server's zone. */
    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final DateTimeFormatter RO_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BigDecimal MIN_TARGET = BigDecimal.ONE;
    private static final BigDecimal MAX_TARGET = BigDecimal.TEN;

    /** A student's target; {@code baseline} = their estimate on {@code setOn} (null when there was none yet). */
    public record Goal(double target, Double baseline, LocalDate setOn) {
    }

    /** The estimate on one day. */
    public record Snapshot(LocalDate day, double grade, double low, double high) {
    }

    private final JdbcTemplate jdbc;
    private final AuditLog auditLog;
    private final Clock clock;

    public EnGoalService(JdbcTemplate jdbc, AuditLog auditLog, Clock clock) {
        this.jdbc = jdbc;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Today in Romania. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    // --- the exam date ---

    @Transactional(readOnly = true)
    public Optional<LocalDate> examDate() {
        return Optional.ofNullable(jdbc.queryForObject("select exam_date from en_exam_settings where id = 1", LocalDate.class));
    }

    /** {@code null} takes the date away (no plans until a new one is set); a day already gone is refused. */
    @Transactional
    public Optional<LocalDate> setExamDate(LocalDate date) {
        if (date != null && date.isBefore(today())) {
            throw new IllegalArgumentException("Data EN nu poate fi în trecut.");
        }
        LocalDate before = examDate().orElse(null);
        jdbc.update("update en_exam_settings set exam_date = ?, updated_at = ? where id = 1",
                date == null ? null : Date.valueOf(date), Timestamp.from(clock.instant()));
        if (!java.util.Objects.equals(before, date)) {
            auditLog.record(AuditEvent.of(AuditAction.EN_EXAM_DATE_SET, null, "Data EN")
                    .details(format(before) + " → " + format(date)));
        }
        return Optional.ofNullable(date);
    }

    private static String format(LocalDate date) {
        return date == null ? "—" : RO_DATE.format(date);
    }

    // --- the student's target ---

    @Transactional(readOnly = true)
    public Optional<Goal> goal(long studentId) {
        return Optional.ofNullable(goals(List.of(studentId)).get(studentId));
    }

    @Transactional(readOnly = true)
    public Map<Long, Goal> goals(Collection<Long> studentIds) {
        Map<Long, Goal> goals = new HashMap<>();
        if (studentIds.isEmpty()) {
            return goals;
        }
        jdbc.query("select student_id, target_grade, baseline_grade, set_on from student_en_goals where student_id = any(?)",
                rs -> {
                    BigDecimal baseline = rs.getBigDecimal("baseline_grade");
                    goals.put(rs.getLong("student_id"), new Goal(rs.getBigDecimal("target_grade").doubleValue(),
                            baseline == null ? null : baseline.doubleValue(), rs.getObject("set_on", LocalDate.class)));
                }, (Object) studentIds.toArray(Long[]::new));
        return goals;
    }

    /**
     * Sets (or changes) the student's target. The line „în urmă" is measured against starts again today, from
     * {@code now} - and today's estimate is kept for the chart, so it has a first point even before the daily job runs.
     */
    @Transactional
    public void setGoal(long studentId, BigDecimal target, EnPrediction now) {
        if (target == null || target.compareTo(MIN_TARGET) < 0 || target.compareTo(MAX_TARGET) > 0
                || target.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Ținta e o notă de la 1 la 10, cu cel mult două zecimale.");
        }
        Double baseline = now != null && now.available() ? now.grade() : null;
        jdbc.update("""
                insert into student_en_goals (student_id, target_grade, baseline_grade, set_on, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict (student_id) do update set target_grade = excluded.target_grade,
                    baseline_grade = excluded.baseline_grade, set_on = excluded.set_on, updated_at = excluded.updated_at""",
                studentId, target, baseline, Date.valueOf(today()), Timestamp.from(clock.instant()));
        saveSnapshot(studentId, today(), now);
    }

    // --- the estimate, day by day ---

    /**
     * Keeps the estimate for {@code day} (a second one the same day replaces it); false = no estimate, nothing kept.
     * A target set before there was an estimate gets this one as the start of its line.
     */
    @Transactional
    public boolean saveSnapshot(long studentId, LocalDate day, EnPrediction p) {
        if (p == null || !p.available() || p.grade() == null) {
            return false;
        }
        jdbc.update("""
                insert into en_prediction_snapshots (student_id, day, grade, low, high) values (?, ?, ?, ?, ?)
                on conflict (student_id, day) do update set grade = excluded.grade, low = excluded.low, high = excluded.high""",
                studentId, Date.valueOf(day), p.grade(), p.low(), p.high());
        jdbc.update("update student_en_goals set baseline_grade = ?, set_on = ? where student_id = ? and baseline_grade is null",
                p.grade(), Date.valueOf(day), studentId);
        return true;
    }

    /** Oldest first. */
    @Transactional(readOnly = true)
    public List<Snapshot> history(long studentId) {
        return jdbc.query("select day, grade, low, high from en_prediction_snapshots where student_id = ? order by day",
                (rs, n) -> new Snapshot(rs.getObject("day", LocalDate.class), rs.getBigDecimal("grade").doubleValue(),
                        rs.getBigDecimal("low").doubleValue(), rs.getBigDecimal("high").doubleValue()),
                studentId);
    }

    // --- the teacher's pins ---

    /** The chapters pinned into the student's plan, oldest pin first. */
    @Transactional(readOnly = true)
    public List<Long> pins(long studentId) {
        return jdbc.queryForList("select en_chapter_id from study_plan_pins where student_id = ? order by created_at, en_chapter_id",
                Long.class, studentId);
    }

    /** Pinning a chapter that is already pinned changes nothing (and writes no journal row). */
    @Transactional
    public void pin(User student, EnChapter chapter, Long pinnedBy) {
        int added = jdbc.update("""
                insert into study_plan_pins (student_id, en_chapter_id, pinned_by, created_at) values (?, ?, ?, ?)
                on conflict (student_id, en_chapter_id) do nothing""",
                student.getId(), chapter.getId(), pinnedBy, Timestamp.from(clock.instant()));
        if (added > 0) {
            auditLog.record(AuditEvent.of(AuditAction.STUDY_PLAN_PINNED, chapter.getId(), label(chapter)).about(student));
        }
    }

    @Transactional
    public void unpin(User student, EnChapter chapter) {
        int removed = jdbc.update("delete from study_plan_pins where student_id = ? and en_chapter_id = ?",
                student.getId(), chapter.getId());
        if (removed > 0) {
            auditLog.record(AuditEvent.of(AuditAction.STUDY_PLAN_UNPINNED, chapter.getId(), label(chapter)).about(student));
        }
    }

    private static String label(EnChapter chapter) {
        String label = chapter.getCode() + " " + chapter.getTitle();
        return label.length() > 300 ? label.substring(0, 300) : label;
    }

    // --- GDPR ---

    /** Erasure: the target, the history and the pins go (the account row stays as a tombstone, so nothing cascades). */
    @Transactional
    public void eraseStudent(long studentId) {
        jdbc.update("delete from student_en_goals where student_id = ?", studentId);
        jdbc.update("delete from en_prediction_snapshots where student_id = ?", studentId);
        jdbc.update("delete from study_plan_pins where student_id = ?", studentId);
    }
}
