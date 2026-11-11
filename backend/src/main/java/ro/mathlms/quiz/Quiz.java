package ro.mathlms.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.Chapter;

/**
 * An assessment (e.g. "Simulare EN – Varianta 3") authored directly by an admin: it holds
 * an ordered list of {@link QuizItem}s. Built in {@link QuizStatus#DRAFT} and only visible
 * to students once {@link QuizStatus#PUBLISHED}.
 */
@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizStatus status = QuizStatus.DRAFT;

    /** The one class this quiz is for; {@code null} = every student may take it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    /**
     * Minutes a student has once an attempt starts; {@code null} = untimed. Each attempt snapshots its own
     * deadline (see {@link QuizAttempt#getDeadlineAt()}), so changing this affects only attempts started later.
     */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    public static final int MAX_TIME_LIMIT_MINUTES = 600;

    /**
     * Whether students may also PRACTISE this quiz (immediate feedback, no grade). Off by default: practice shows
     * the right answers, so the teacher decides which quizzes can double as practice material without spoiling a test.
     */
    @Column(name = "practice_allowed", nullable = false)
    private boolean practiceAllowed;

    /**
     * How many times a student may hand it in; {@code null} = no limit. See {@link AttemptAllowance}. The result shows
     * the answers and the barem, so anything above one is the teacher's choice, and the highest grade is the one that counts.
     */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    /**
     * Whether every hand-in asks the AI for grade proposals by itself. Off by default: each proposal is a call to the
     * strong model, also for papers graded by hand and for every retake; "Cere propunere AI" still works on one answer.
     */
    @Column(name = "ai_auto_proposals", nullable = false)
    private boolean aiAutoProposals;

    /** Each attempt gets the questions in an order of its own, within each section (V65). */
    @Column(name = "shuffle_items", nullable = false)
    private boolean shuffleItems;

    /** Each attempt gets the options of each grilă in an order of its own (V65). */
    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    public static final int MAX_ATTEMPTS = 20;

    /** What a test made without the dialog starts with (from photos, from a chapter's exercises). */
    public static final int DEFAULT_ATTEMPTS = 1;

    /** The teacher who made the test (sees and changes it); null = only ADMINs see it. */
    @Column(name = "teacher_id")
    private Long teacherId;

    /** The chapter its questions practise unless one says otherwise (E6 mastery map); {@code null} = untagged. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    public Quiz(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    /** Records who owns the test: its creator (or whoever copied it). */
    public void ownedBy(Long teacherId) {
        this.teacherId = teacherId;
    }

    public void update(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    /** Sets the time limit in minutes (1–{@value #MAX_TIME_LIMIT_MINUTES}), or {@code null} for no limit. */
    public void changeTimeLimit(Integer minutes) {
        if (minutes != null && (minutes < 1 || minutes > MAX_TIME_LIMIT_MINUTES)) {
            throw new IllegalArgumentException(
                    "timeLimitMinutes must be between 1 and " + MAX_TIME_LIMIT_MINUTES + ", was " + minutes);
        }
        this.timeLimitMinutes = minutes;
    }

    /** Sets how many times a student may hand it in (1–{@value #MAX_ATTEMPTS}), or {@code null} for no limit. */
    public void limitAttempts(Integer attempts) {
        if (attempts != null && (attempts < 1 || attempts > MAX_ATTEMPTS)) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ATTEMPTS + ", was " + attempts);
        }
        this.maxAttempts = attempts;
    }

    /** Decides only attempts started from now on: an open one keeps the order it began with. */
    public void shuffle(boolean items, boolean options) {
        this.shuffleItems = items;
        this.shuffleOptions = options;
    }

    public void autoProposeGrades(boolean on) {
        this.aiAutoProposals = on;
    }

    public void allowPractice(boolean allowed) {
        this.practiceAllowed = allowed;
    }

    /** Restricts the quiz to one class, or ({@code null}) opens it to every student again. */
    public void assignToClass(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
    }

    /** Tags the quiz with the chapter its questions practise, or ({@code null}) removes the tag. */
    public void tagChapter(Chapter chapter) {
        this.chapter = chapter;
    }

    /** Makes the quiz visible to students. Idempotent-safe: publishing a published quiz is a no-op. */
    public void publish() {
        this.status = QuizStatus.PUBLISHED;
    }

    /** Pulls the quiz back to draft (hidden from students). */
    public void unpublish() {
        this.status = QuizStatus.DRAFT;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
