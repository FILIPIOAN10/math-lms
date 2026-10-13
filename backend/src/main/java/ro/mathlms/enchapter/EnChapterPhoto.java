package ro.mathlms.enchapter;

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

import java.util.Objects;

/** One photographed page of a chapter's theory. Never edited: replacing a page = delete it and upload again. */
@Entity
@Table(name = "en_chapter_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnChapterPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private EnChapter chapter;

    @Column(nullable = false)
    private int position;

    @Column(name = "image_key", nullable = false, length = 200)
    private String imageKey;

    public EnChapterPhoto(EnChapter chapter, int position, String imageKey) {
        this.chapter = Objects.requireNonNull(chapter, "chapter");
        this.position = position;
        this.imageKey = Objects.requireNonNull(imageKey, "imageKey");
    }
}
