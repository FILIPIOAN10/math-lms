package ro.mathlms.enchapter;

/**
 * The chapter a question points at, as a wrong answer shows it: "Dacă vrei să recapitulezi noțiunile despre
 * {@code revisionTopic}", then "Vezi lecția: A1 Ordinea efectuării operațiilor".
 * {@code ready} = its page has photos or worked examples (an empty page is not worth a link yet).
 */
public record EnChapterLinkDto(Long id, String code, String title, boolean ready, String revisionTopic) {

    public static EnChapterLinkDto of(EnChapter chapter, boolean ready) {
        return chapter == null ? null
                : new EnChapterLinkDto(chapter.getId(), chapter.getCode(), chapter.getTitle(), ready, chapter.getRevisionTopic());
    }
}
