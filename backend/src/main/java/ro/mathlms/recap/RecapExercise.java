package ro.mathlms.recap;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import ro.mathlms.enchapter.EnChapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One exercise of the „Teme recapitulative” collection (V82), imported from the zip made outside the app: the book's
 * statement, the AI's barem and answers, the verdict of the two AI runs, the drawing cut from the page. A teacher approves
 * it before it can go in a test; until then no student sees it.
 */
@Entity
@Table(name = "recap_exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecapExercise {

    /** The most requirements one exercise may have (as for a test's problem). */
    public static final int MAX_PARTS = 6;

    /** Both AI runs agreed on every result. */
    public static final String AI_AGREED = "AGREE";
    /** A result the AI runs agreed on was changed by a teacher (not checked by AI any more). */
    public static final String TEACHER_EDITED = "EDITED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, length = 40, updatable = false)
    private String sourceKey;

    @Column(nullable = false)
    private int tema;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(length = 40)
    private String result;

    @Column(name = "ai_status", nullable = false, length = 20)
    private String aiStatus;

    @Column(name = "image_key", length = 200)
    private String imageKey;

    @Column(name = "figure_missing", nullable = false)
    private boolean figureMissing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "en_chapter_id")
    private EnChapter enChapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecapReview review = RecapReview.DRAFT;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection
    @CollectionTable(name = "recap_exercise_parts", joinColumns = @JoinColumn(name = "exercise_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 100)
    private List<RecapPart> parts = new ArrayList<>();

    /** The tests it was put in. */
    @ElementCollection
    @CollectionTable(name = "recap_exercise_quizzes", joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "quiz_id")
    @BatchSize(size = 100)
    private Set<Long> quizIds = new HashSet<>();

    public RecapExercise(String sourceKey, int tema, int number, String aiStatus) {
        this.sourceKey = requireText(sourceKey, "sourceKey");
        this.tema = tema;
        this.number = number;
        this.aiStatus = requireText(aiStatus, "aiStatus");
    }

    /** The text, barem, answer and requirements; a result that is not a plain number is not kept (see the service). */
    public void write(String statement, String solution, String answer, String result, List<RecapPart> parts) {
        this.statement = requireText(statement, "statement").strip();
        this.solution = blankToNull(solution);
        this.answer = blankToNull(answer);
        this.result = blankToNull(result);
        List<RecapPart> given = parts == null ? List.of() : parts;
        if (given.size() > MAX_PARTS) {
            throw new IllegalArgumentException("at most " + MAX_PARTS + " requirements");
        }
        this.parts.clear();
        this.parts.addAll(given);
    }

    /**
     * The teacher changed a result the two AI runs had agreed on: the exercise is no longer "Verificat de AI" (the
     * list's AI filter and the "checked" marks follow {@code aiStatus}). Other statuses already say "read it".
     */
    public void resultEditedByTeacher() {
        if (AI_AGREED.equals(aiStatus)) {
            aiStatus = TEACHER_EDITED;
        }
    }

    public void pointToEnChapter(EnChapter chapter) {
        this.enChapter = chapter;
    }

    /** The drawing cut from the book (or added by the teacher): the exercise no longer waits for one. */
    public String showImage(String key) {
        String old = imageKey;
        this.imageKey = requireText(key, "key");
        this.figureMissing = false;
        return old;
    }

    /** The book had a drawing that was not found: approval waits for one, or for {@link #waiveFigure}. */
    public void markFigureMissing(boolean missing) {
        this.figureMissing = missing && imageKey == null;
    }

    /** The teacher says the exercise is fine without its drawing. */
    public void waiveFigure() {
        this.figureMissing = false;
    }

    public void review(RecapReview review, Long by, Instant at) {
        this.review = Objects.requireNonNull(review, "review");
        this.reviewedBy = review == RecapReview.DRAFT ? null : by;
        this.reviewedAt = review == RecapReview.DRAFT ? null : at;
    }

    public void usedIn(Long quizId) {
        quizIds.add(quizId);
    }

    /** The statement with its requirements, for recognising the EN chapter. */
    public String fullText() {
        StringBuilder text = new StringBuilder(statement);
        parts.forEach(p -> text.append('\n').append(p.getStatement()));
        return text.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
