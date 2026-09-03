package ro.mathlms.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * One student's answer to a single {@link QuizItem} within a {@link QuizAttempt}. At most one
 * response per (attempt, item). Shape depends on the item type:
 * <ul>
 *   <li>{@link QuizItemType#SINGLE_CHOICE} — {@link #selectedOption}; auto-graded via {@link #gradeAuto}.</li>
 *   <li>{@link QuizItemType#OPEN} — {@link #imageKey} points to the uploaded rezolvare photo; the
 *       teacher scores it via {@link #gradeManual(int)}.</li>
 * </ul>
 * {@link #awardedPoints} and {@link #correct} stay null until the response is graded.
 */
@Entity
@Table(name = "item_responses", uniqueConstraints =
        @UniqueConstraint(name = "uk_item_responses_attempt_item",
                columnNames = {"attempt_id", "item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private QuizItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuizOption selectedOption;

    /** Storage key of the uploaded rezolvare photo (OPEN items only). */
    @Column(name = "image_key", columnDefinition = "TEXT")
    private String imageKey;

    /** Points awarded for this item; null until graded. */
    @Column(name = "awarded_points")
    private Integer awardedPoints;

    /** Whether the choice was right (SINGLE_CHOICE only); null until graded or for OPEN items. */
    @Column
    private Boolean correct;

    public ItemResponse(QuizAttempt attempt, QuizItem item) {
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.item = Objects.requireNonNull(item, "item");
    }

    /** Records the picked option for a SINGLE_CHOICE item. The option must belong to this item. */
    public void answerSingleChoice(QuizOption option) {
        requireType(QuizItemType.SINGLE_CHOICE);
        Objects.requireNonNull(option, "option");
        if (!Objects.equals(option.getItem().getId(), item.getId())) {
            throw new IllegalArgumentException("option does not belong to this item");
        }
        this.selectedOption = option;
        this.imageKey = null;
    }

    /** Records the uploaded rezolvare photo for an OPEN item. */
    public void answerOpen(String imageKey) {
        requireType(QuizItemType.OPEN);
        this.imageKey = requireNonBlank(imageKey, "imageKey");
        this.selectedOption = null;
    }

    /** Auto-grades a SINGLE_CHOICE response: correct answers earn the item's points, wrong ones zero. */
    public void gradeAuto(boolean correct, int awardedPoints) {
        requireType(QuizItemType.SINGLE_CHOICE);
        this.correct = correct;
        this.awardedPoints = requireNonNegative(awardedPoints);
    }

    /** Records the teacher's score for an OPEN response. */
    public void gradeManual(int awardedPoints) {
        requireType(QuizItemType.OPEN);
        this.awardedPoints = requireNonNegative(awardedPoints);
    }

    private void requireType(QuizItemType expected) {
        if (item.getType() != expected) {
            throw new IllegalStateException(
                    "expected a " + expected + " item, was " + item.getType());
        }
    }

    private static int requireNonNegative(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("awardedPoints must not be negative");
        }
        return points;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
