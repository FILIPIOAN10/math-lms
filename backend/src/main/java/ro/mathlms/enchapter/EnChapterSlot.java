package ro.mathlms.enchapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Which chapter a place on the EN paper links to by default: "I.1" (SUBIECTUL I, question 1) → A1, "III.6.b" (SUBIECTUL
 * al III-lea, problem 6, requirement b) → G9. Seeded by V34 from docs/EN-capitole.md.
 */
@Entity
@Table(name = "en_chapter_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnChapterSlot {

    @Id
    @Column(length = 12)
    private String slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private EnChapter chapter;
}
