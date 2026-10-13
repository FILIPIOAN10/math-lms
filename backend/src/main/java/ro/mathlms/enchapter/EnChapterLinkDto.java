package ro.mathlms.enchapter;

/**
 * The chapter a question points at, as the result page shows it: "Vezi modele: A1 Ordinea efectuării operațiilor".
 * {@code ready} = its page has photos or worked examples (an empty page is not worth a button yet).
 */
public record EnChapterLinkDto(Long id, String code, String title, boolean ready) {

    public static EnChapterLinkDto of(EnChapter chapter, boolean ready) {
        return chapter == null ? null : new EnChapterLinkDto(chapter.getId(), chapter.getCode(), chapter.getTitle(), ready);
    }
}
