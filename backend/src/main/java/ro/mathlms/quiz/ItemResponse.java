package ro.mathlms.quiz;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One student's answer to a single {@link QuizItem} within a {@link QuizAttempt}. At most one
 * response per (attempt, item). Shape depends on the item type:
 * <ul>
 *   <li>{@link QuizItemType#SINGLE_CHOICE} — {@link #selectedOption}; auto-graded via {@link #gradeAuto}.</li>
 *   <li>{@link QuizItemType#OPEN} — {@link #photos}: the photographed rezolvare, up to {@link #MAX_PHOTOS} pages in the
 *       order taken (S2); the teacher scores it via {@link #gradeManual(int)}.</li>
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

    /** The most pages one open answer may have (S2). */
    public static final int MAX_PHOTOS = 5;

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

    /** The photographed rezolvare (OPEN items only), oldest first. The legacy image_key column is frozen since V28. */
    @OneToMany(mappedBy = "response", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ResponsePhoto> photos = new ArrayList<>();

    /** Points awarded for this item; null until graded. */
    @Column(name = "awarded_points")
    private Integer awardedPoints;

    /** How many of the item's hints the student revealed (practice mode only; always 0 in a graded test). */
    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    /** Whether the choice was right (SINGLE_CHOICE only); null until graded or for OPEN items. */
    @Column
    private Boolean correct;

    /** The teacher's written comment on a graded OPEN answer; null = none. */
    @Column(name = "teacher_comment", length = TeacherComment.MAX_LENGTH)
    private String teacherComment;

    /** The browser that sent the stored choice, and its counter then (V31); null for answers from older clients. */
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "client_seq")
    private Long clientSeq;

    /**
     * The teacher's points per requirement (EN "(2p) a)", "(3p) b)") of a problem that has them, by part id (V32).
     * {@link #awardedPoints} is their sum once every requirement has points, null before.
     */
    @ElementCollection
    @CollectionTable(name = "response_part_points", joinColumns = @JoinColumn(name = "response_id"))
    @MapKeyColumn(name = "part_id")
    @Column(name = "points")
    private Map<Long, Integer> partPoints = new HashMap<>();

    public ItemResponse(QuizAttempt attempt, QuizItem item) {
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.item = Objects.requireNonNull(item, "item");
    }

    /** Records the picked option for a SINGLE_CHOICE item. The option must belong to this item. */
    public void answerSingleChoice(QuizOption option) {
        answerSingleChoice(option, null, null);
    }

    /**
     * Records the picked option unless it is an older retry from the same browser: a browser retries unsent answers, so
     * "B" sent at 10:01 can arrive after "C" sent at 10:02. Applied when it carries no browser id or counter (older
     * clients), comes from another browser (another device: the last to arrive wins), or has a higher counter than the
     * stored answer. Returns whether it was applied.
     */
    public boolean answerSingleChoice(QuizOption option, UUID fromClient, Long seq) {
        requireType(QuizItemType.SINGLE_CHOICE);
        Objects.requireNonNull(option, "option");
        if (!Objects.equals(option.getItem().getId(), item.getId())) {
            throw new IllegalArgumentException("option does not belong to this item");
        }
        boolean stale = fromClient != null && seq != null && fromClient.equals(clientId) && clientSeq != null
                && seq <= clientSeq;
        if (stale) {
            return false;
        }
        this.selectedOption = option;
        this.clientId = fromClient;
        this.clientSeq = fromClient == null ? null : seq;
        return true;
    }

    /** Adds one page of the rezolvare to an OPEN item's answer; refused once it has {@link #MAX_PHOTOS}. */
    public ResponsePhoto addPhoto(String imageKey, Instant at) {
        requireType(QuizItemType.OPEN);
        if (photos.size() >= MAX_PHOTOS) {
            throw new IllegalStateException("An answer has at most " + MAX_PHOTOS + " photos");
        }
        ResponsePhoto photo = new ResponsePhoto(this, requireNonBlank(imageKey, "imageKey"), at);
        photos.add(photo);
        return photo;
    }

    /** Takes one page off the answer; returns its stored file name, or empty when it is not this answer's photo. */
    public Optional<String> removePhoto(Long photoId) {
        Optional<ResponsePhoto> photo = photos.stream().filter(p -> Objects.equals(p.getId(), photoId)).findFirst();
        photo.ifPresent(photos::remove);
        return photo.map(ResponsePhoto::getImageKey);
    }

    /** The stored file of one of this answer's photos, by id. */
    public Optional<String> photoKey(Long photoId) {
        return photos.stream().filter(p -> Objects.equals(p.getId(), photoId)).map(ResponsePhoto::getImageKey).findFirst();
    }

    public boolean hasPhotos() {
        return !photos.isEmpty();
    }

    /** The stored files of the photos, in the order taken (what the AI grading reads). */
    public List<String> photoKeys() {
        return photos.stream().map(ResponsePhoto::getImageKey).toList();
    }

    /** The photos' ids in the order taken - what the pages use to show and delete them. */
    public List<Long> photoIds() {
        return photos.stream().map(ResponsePhoto::getId).toList();
    }

    /** Records that hint number {@code number} (1-based) was revealed; asking for an earlier one never lowers the count. */
    public void revealHint(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("hint numbers start at 1, was " + number);
        }
        this.hintsUsed = Math.max(this.hintsUsed, number);
    }

    /** Auto-grades a SINGLE_CHOICE response: correct answers earn the item's points, wrong ones zero. */
    public void gradeAuto(boolean correct, int awardedPoints) {
        requireType(QuizItemType.SINGLE_CHOICE);
        this.correct = correct;
        this.awardedPoints = requireNonNegative(awardedPoints);
    }

    /** Records the teacher's score for an OPEN response, without a comment. */
    public void gradeManual(int awardedPoints) {
        gradeManual(awardedPoints, null);
    }

    /** Records the teacher's score and comment for an OPEN response; a blank comment clears it. */
    public void gradeManual(int awardedPoints, String comment) {
        requireType(QuizItemType.OPEN);
        this.awardedPoints = requireNonNegative(awardedPoints);
        this.teacherComment = TeacherComment.normalize(comment);
    }

    /**
     * Records the teacher's points for some of an OPEN problem's requirements (null takes them away) and the comment on
     * the whole answer. The answer's points become the sum once every requirement of {@code parts} has some - until
     * then they stay null, so the paper cannot be finalised half graded.
     */
    public void gradeParts(List<QuizItemPart> parts, Map<Long, Integer> grades, String comment) {
        requireType(QuizItemType.OPEN);
        for (Map.Entry<Long, Integer> grade : grades.entrySet()) {
            QuizItemPart part = parts.stream().filter(p -> Objects.equals(p.getId(), grade.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("part " + grade.getKey() + " is not one of this item's"));
            Integer points = grade.getValue();
            if (points == null) {
                partPoints.remove(part.getId());
            } else if (points < 0 || points > part.getPoints()) {
                throw new IllegalArgumentException("part " + part.label() + " is worth 0.." + part.getPoints());
            } else {
                partPoints.put(part.getId(), points);
            }
        }
        boolean complete = parts.stream().allMatch(p -> partPoints.containsKey(p.getId()));
        this.awardedPoints = complete ? parts.stream().mapToInt(p -> partPoints.get(p.getId())).sum() : null;
        this.teacherComment = TeacherComment.normalize(comment);
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
