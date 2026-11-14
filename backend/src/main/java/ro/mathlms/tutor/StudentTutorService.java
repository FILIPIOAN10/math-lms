package ro.mathlms.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.aiusage.AiUsageRecorder;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.enchapter.LessonLinks;
import ro.mathlms.enchapter.LessonLinks.LessonLinkDto;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.user.User;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.user.UserRepository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The student's "Nu înțeleg…" box: the student types what they do not understand, the tutor service answers in two or
 * three sentences taken only from the EN lessons, and this side turns the pages it cites into links - the lesson (its
 * chapter, opened at the section) and "Exersează capitolul". When the lessons have nothing close, the question goes to
 * the teacher's list of the class (what the class does not know), without the student's name.
 *
 * <p>The four rules, and where each one is kept:
 * <ol>
 *   <li>only from the lessons, with the page - the service answers from the passages and must cite one; here an answer
 *       whose cited page no longer exists is not shown either;</li>
 *   <li>never solves the exercise - the service's prompt (tutor/src/tutor/explain.py);</li>
 *   <li>closed while the student has an open graded test (an IN_PROGRESS TEST attempt) - checked here before anything
 *       is counted or sent;</li>
 *   <li>says it is an AI - the box in the frontend, on every answer.</li>
 * </ol>
 * Off unless {@code app.tutor.enabled} and {@code app.tutor.student-enabled} are both true; a daily limit per student
 * ({@code student-daily-limit}) bounds the bill. Nothing the student asked is logged, and an answered question is not
 * stored anywhere.
 */
@Service
public class StudentTutorService {

    private static final Logger log = LoggerFactory.getLogger(StudentTutorService.class);
    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    static final Duration KEEP_UNANSWERED = Duration.ofDays(30);
    static final int KEEP_USAGE_DAYS = 7;
    static final int MAX_LIST = 200;
    static final String NOT_AVAILABLE = "Asistentul nu e disponibil acum. Încearcă mai târziu.";

    public record StatusDto(boolean enabled, int dailyLimit, int remainingToday, String openTest) {
    }

    /** {@code text} is null when not answered; {@code sentToTeacher} = the question is on the teacher's list. */
    public record ExplanationDto(boolean answered, String text, List<LessonLinkDto> lessons, boolean sentToTeacher,
                                 int remainingToday) {
    }

    public record UnansweredDto(Long id, String question, Instant askedAt) {
    }

    /** The box is closed for this student (open test, day's limit): the message is worded for them. */
    public static class ClosedException extends RuntimeException {
        private final HttpStatus status;

        public ClosedException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final TutorProperties properties;
    private final TutorClient client;
    private final UserRepository userRepository;
    private final QuizAttemptRepository attemptRepository;
    private final LessonLinks lessonLinks;
    private final JdbcTemplate jdbc;
    private final AiUsageRecorder usage;
    private final Clock clock;

    public StudentTutorService(TutorProperties properties, TutorClient client, UserRepository userRepository,
                               QuizAttemptRepository attemptRepository, EnChapterRepository chapterRepository,
                               JdbcTemplate jdbc, AiUsageRecorder usage, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.lessonLinks = new LessonLinks(chapterRepository);
        this.jdbc = jdbc;
        this.usage = usage;
        this.clock = clock;
    }

    private boolean enabled() {
        return properties.enabled() && properties.studentEnabled();
    }

    /** Whether the box is there, how many questions are left today, and the open test that closes it (or null). */
    public StatusDto status(String email) {
        if (!enabled()) {
            return new StatusDto(false, 0, 0, null);
        }
        User student = student(email);
        return new StatusDto(true, properties.studentDailyLimit(), remaining(student.getId()),
                openTest(student.getId()).map(a -> a.getQuiz().getTitle()).orElse(null));
    }

    public ExplanationDto explain(String email, String question, List<TutorClient.Turn> history) {
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        User student = student(email);
        Long studentId = student.getId();
        Optional<QuizAttempt> open = openTest(studentId);
        if (open.isPresent()) {
            throw new ClosedException(HttpStatus.CONFLICT, "Asistentul e oprit cât timp ai un test deschis: „"
                    + open.get().getQuiz().getTitle() + "”. Termină-l, apoi întreabă.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (!take(studentId, today)) {
            throw new ClosedException(HttpStatus.TOO_MANY_REQUESTS, "Ai folosit toate cele "
                    + properties.studentDailyLimit() + " întrebări de azi. Revino mâine sau întreabă-ți profesorul.");
        }

        TutorClient.Explanation answer;
        try {
            answer = client.explain(question.strip(), history);
        } catch (TutorException e) {
            giveBack(studentId, today); // the student did not get an answer: the question is not used up
            throw TutorClient.NOT_CONFIGURED.equals(e.getMessage()) ? new TutorException(NOT_AVAILABLE, e) : e;
        }
        if (answer.inputTokens() > 0 || answer.outputTokens() > 0) {
            usage.record("tutor-explain", "tutor", answer.inputTokens(), answer.outputTokens());
        }

        List<LessonLinkDto> lessons = answer.answered() ? lessons(answer.sources()) : List.of();
        boolean answered = answer.answered() && answer.text() != null && !answer.text().isBlank() && !lessons.isEmpty();
        log.info("Student tutor: answered {}, refusal {}, {} lesson link(s), {} input + {} output tokens", answered,
                answer.refusal() == null ? null : answer.refusal().reason(), lessons.size(), answer.inputTokens(),
                answer.outputTokens());
        boolean sent = !answered && sendToTeacher(studentId, standalone(answer, question));
        return new ExplanationDto(answered, answered ? answer.text().strip() : null, answered ? lessons : List.of(), sent,
                remaining(studentId));
    }

    /** What the class did not get an answer to, newest first, last 30 days: the text and when, never who. */
    public List<UnansweredDto> unanswered(Long classId) {
        return jdbc.query("""
                        SELECT q.id, q.question, q.asked_at FROM tutor_unanswered_questions q
                        WHERE q.student_id IN (SELECT e.student_id FROM enrollments e WHERE e.school_class_id = ?)
                          AND q.asked_at >= ?
                        ORDER BY q.asked_at DESC, q.id DESC LIMIT ?""",
                (rs, i) -> new UnansweredDto(rs.getLong("id"), rs.getString("question"),
                        rs.getTimestamp("asked_at").toInstant()),
                classId, Timestamp.from(clock.instant().minus(KEEP_UNANSWERED)), MAX_LIST);
    }

    /** GDPR export: the student's own unanswered questions, newest first. */
    public List<UnansweredDto> questionsOf(Long studentId) {
        return jdbc.query("SELECT id, question, asked_at FROM tutor_unanswered_questions WHERE student_id = ?"
                        + " ORDER BY asked_at DESC, id DESC",
                (rs, i) -> new UnansweredDto(rs.getLong("id"), rs.getString("question"),
                        rs.getTimestamp("asked_at").toInstant()),
                studentId);
    }

    /** GDPR erasure: the student's questions, counters and explanation requests go with the account. */
    public void eraseStudent(Long studentId) {
        jdbc.update("DELETE FROM tutor_unanswered_questions WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM tutor_daily_usage WHERE student_id = ?", studentId);
        // „De ce am greșit?" requests (V70): also only the student's own waiting for an explanation.
        jdbc.update("DELETE FROM mistake_explanation_requests WHERE student_id = ?", studentId);
    }

    // --- helpers ---

    private User student(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("No account for " + email));
    }

    private Optional<QuizAttempt> openTest(Long studentId) {
        return attemptRepository.findFirstByStudentIdAndStatusAndModeOrderByStartedAtDesc(
                studentId, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST);
    }

    /** Counts one question for today, unless the day's limit is reached - checked and counted in one statement. */
    private boolean take(Long studentId, LocalDate today) {
        jdbc.update("DELETE FROM tutor_daily_usage WHERE day < ?", Date.valueOf(today.minusDays(KEEP_USAGE_DAYS)));
        List<Integer> counted = jdbc.queryForList("""
                        INSERT INTO tutor_daily_usage (student_id, day, questions) VALUES (?, ?, 1)
                        ON CONFLICT (student_id, day) DO UPDATE SET questions = tutor_daily_usage.questions + 1
                        WHERE tutor_daily_usage.questions < ?
                        RETURNING questions""",
                Integer.class, studentId, Date.valueOf(today), properties.studentDailyLimit());
        return !counted.isEmpty();
    }

    private void giveBack(Long studentId, LocalDate today) {
        jdbc.update("UPDATE tutor_daily_usage SET questions = questions - 1 WHERE student_id = ? AND day = ? AND questions > 0",
                studentId, Date.valueOf(today));
    }

    private int remaining(Long studentId) {
        List<Integer> used = jdbc.queryForList("SELECT questions FROM tutor_daily_usage WHERE student_id = ? AND day = ?",
                Integer.class, studentId, Date.valueOf(LocalDate.now(clock.withZone(ZONE))));
        return Math.max(0, properties.studentDailyLimit() - (used.isEmpty() ? 0 : used.getFirst()));
    }

    /** The cited pages as links: each to its chapter (the page a student opens), at its section; unknown codes dropped. */
    private List<LessonLinkDto> lessons(List<TutorClient.Source> sources) {
        Map<String, LessonLinkDto> byCode = new LinkedHashMap<>();
        for (TutorClient.Source source : sources) {
            if (source.code() != null && !byCode.containsKey(source.code())) {
                lessonLinks.ofCode(source.code(), source.bookPage()).ifPresent(link -> byCode.put(source.code(), link));
            }
        }
        return new ArrayList<>(byCode.values());
    }

    private static String standalone(TutorClient.Explanation answer, String typed) {
        String question = answer.question() == null || answer.question().isBlank() ? typed : answer.question();
        question = question.strip();
        return question.length() > 500 ? question.substring(0, 500) : question;
    }

    /** On the list of the student's teachers; a student in no class has no teacher to see it, so nothing is kept. */
    private boolean sendToTeacher(Long studentId, String question) {
        Integer classes = jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE student_id = ?", Integer.class,
                studentId);
        if (classes == null || classes == 0) {
            return false;
        }
        Instant now = clock.instant();
        jdbc.update("DELETE FROM tutor_unanswered_questions WHERE asked_at < ?", Timestamp.from(now.minus(KEEP_UNANSWERED)));
        jdbc.update("INSERT INTO tutor_unanswered_questions (student_id, question, asked_at) VALUES (?, ?, ?)",
                studentId, question, Timestamp.from(now));
        return true;
    }
}
