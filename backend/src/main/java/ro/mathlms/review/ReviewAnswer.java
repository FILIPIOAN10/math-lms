package ro.mathlms.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.Objects;

/** One answer in the daily review (E4). Append-only: the review queue is replayed from these and the quiz answers. */
@Entity
@Table(name = "review_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private QuizItem item;

    /** The option picked; null once that option is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private QuizOption option;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    public ReviewAnswer(User student, QuizItem item, QuizOption option, boolean correct, Instant answeredAt) {
        this.student = Objects.requireNonNull(student, "student");
        this.item = Objects.requireNonNull(item, "item");
        this.option = option;
        this.correct = correct;
        this.answeredAt = Objects.requireNonNull(answeredAt, "answeredAt");
    }
}
