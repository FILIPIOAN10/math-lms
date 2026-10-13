package ro.mathlms.enchapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Which chapter a place on the EN paper links to by default: "I.1" (SUBIECTUL I, question 1) → A1, "III.6.b" (SUBIECTUL
 * al III-lea, problem 6, requirement b) → G9. Seeded by V34 from docs/EN-capitole.md.
 */
@Entity
@Table(name = "en_chapter_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnChapterSlot implements Persistable<String> {

    @Id
    @Column(length = 12)
    private String slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private EnChapter chapter;

    /**
     * The key is the place itself, not generated: without this Spring Data would take every new link for an existing
     * one and merge it (a SELECT per row) instead of inserting it.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean fresh = true;

    public EnChapterSlot(String slot, EnChapter chapter) {
        this.slot = java.util.Objects.requireNonNull(slot, "slot");
        this.chapter = java.util.Objects.requireNonNull(chapter, "chapter");
    }

    @Override
    public String getId() {
        return slot;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void stored() {
        this.fresh = false;
    }
}
