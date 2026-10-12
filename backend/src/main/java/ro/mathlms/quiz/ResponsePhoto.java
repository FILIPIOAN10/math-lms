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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** S2: one page of a student's photographed solution to an OPEN item; an answer has up to {@link ItemResponse#MAX_PHOTOS}. */
@Entity
@Table(name = "response_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponsePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private ItemResponse response;

    /** The stored file's name in the quiz-photos folder (a UUID - never the uploader's file name). */
    @Column(name = "image_key", nullable = false, columnDefinition = "TEXT")
    private String imageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    ResponsePhoto(ItemResponse response, String imageKey, Instant createdAt) {
        this.response = Objects.requireNonNull(response, "response");
        this.imageKey = Objects.requireNonNull(imageKey, "imageKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
