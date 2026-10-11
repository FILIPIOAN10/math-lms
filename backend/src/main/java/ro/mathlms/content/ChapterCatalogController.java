package ro.mathlms.content;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Every chapter in one flat list, ordered class → book → chapter (natural order, "a 9-a" before "a 10-a"): what the
 * teacher picks from when tagging a quiz or a question for the mastery map (E6).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ChapterCatalogController {

    /** One choice: the chapter plus where it lives, so the select can group and label it. */
    public record ChapterOptionDto(Long id, String title, Long bookId, String bookTitle, Long schoolClassId,
                                   String schoolClassName) {
        static ChapterOptionDto from(Chapter chapter) {
            Book book = chapter.getBook();
            SchoolClass cls = book.getSchoolClass();
            return new ChapterOptionDto(chapter.getId(), chapter.getTitle(), book.getId(), book.getTitle(),
                    cls.getId(), cls.getName());
        }
    }

    static final Comparator<ChapterOptionDto> ORDER = Comparator
            .comparing(ChapterOptionDto::schoolClassName, NaturalOrder.BY_NAME)
            .thenComparing(ChapterOptionDto::bookTitle, NaturalOrder.BY_NAME)
            .thenComparing(ChapterOptionDto::title, NaturalOrder.BY_NAME);

    private final ChapterRepository chapterRepository;

    public ChapterCatalogController(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    @GetMapping("/api/admin/chapters")
    @Transactional(readOnly = true)
    public List<ChapterOptionDto> chapters() {
        return chapterRepository.findAllFetched().stream().map(ChapterOptionDto::from).sorted(ORDER).toList();
    }
}
