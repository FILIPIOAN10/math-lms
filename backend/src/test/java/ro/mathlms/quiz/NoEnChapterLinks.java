package ro.mathlms.quiz;

import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.enchapter.EnChapterService;
import ro.mathlms.enchapter.EnChapterSlotRepository;

import static org.mockito.Mockito.mock;

/** EN chapter links for unit tests that do not care about them: no automatic links, no page with content. */
final class NoEnChapterLinks {

    private NoEnChapterLinks() {
    }

    static EnChapterLinks create() {
        return new EnChapterLinks(mock(EnChapterSlotRepository.class), mock(EnChapterRepository.class),
                mock(EnChapterService.class));
    }
}
