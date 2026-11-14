package ro.mathlms.enchapter;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Where to read something in the EN lessons, as a link a student follows: the chapter page to open (a section or a point
 * has no page of its own, it opens its chapter, scrolled to it) and the page printed in the book. Used for the tutor's
 * citations ("Nu înțeleg…") and for the lesson under a teacher's explanation of a wrong option („De ce am greșit?").
 */
@Component
public class LessonLinks {

    /**
     * {@code chapterId} is the page to open (and to practise), {@code sectionId} the section to scroll to (null when it
     * is the chapter itself), {@code title} what the cited part is called.
     */
    public record LessonLinkDto(Long chapterId, String chapterCode, String chapterTitle, Long sectionId, String title,
                                int bookPage) {
    }

    private final EnChapterRepository chapterRepository;

    public LessonLinks(EnChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    /** By the code the tutor cites ("AIS5P5"); empty when no such page exists any more. */
    public Optional<LessonLinkDto> ofCode(String code, int bookPage) {
        return code == null ? Optional.empty() : chapterRepository.findByCode(code).flatMap(page -> of(page, bookPage));
    }

    /** By the id stored with an explanation; empty for a missing id. */
    public Optional<LessonLinkDto> ofId(Long chapterId, Integer bookPage) {
        if (chapterId == null) {
            return Optional.empty();
        }
        return chapterRepository.findById(chapterId)
                .flatMap(page -> of(page, bookPage != null ? bookPage : page.getBookPage() != null ? page.getBookPage() : 0));
    }

    public boolean exists(Long chapterId) {
        return chapterId != null && chapterRepository.existsById(chapterId);
    }

    private Optional<LessonLinkDto> of(EnChapter page, int bookPage) {
        EnChapter top = page;
        // A point sits under a section, a section under its chapter (EnChapterTree.MAX_DEPTH = 2).
        for (int depth = 0; top.getParentId() != null && depth < 3; depth++) {
            EnChapter parent = chapterRepository.findById(top.getParentId()).orElse(null);
            if (parent == null) {
                return Optional.empty();
            }
            top = parent;
        }
        if (top.getParentId() != null) {
            return Optional.empty();
        }
        return Optional.of(new LessonLinkDto(top.getId(), top.getCode(), top.getTitle(),
                page == top ? null : page.getId(), page.getTitle(), bookPage));
    }
}
