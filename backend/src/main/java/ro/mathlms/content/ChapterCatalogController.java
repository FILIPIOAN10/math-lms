package ro.mathlms.content;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import ro.mathlms.teaching.TeacherScope;

/**
 * Every chapter in one flat list, ordered class → book → chapter (natural order, "a 9-a" before "a 10-a"): what the
 * teacher picks from when tagging a quiz or a question for the mastery map (E6).
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
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
    private final TeacherScope scope;

    public ChapterCatalogController(ChapterRepository chapterRepository, TeacherScope scope) {
        this.chapterRepository = chapterRepository;
        this.scope = scope;
    }

    @GetMapping("/api/admin/chapters")
    @Transactional(readOnly = true)
    public List<ChapterOptionDto> chapters() {
        List<ChapterOptionDto> all = chapterRepository.findAllFetched().stream().map(ChapterOptionDto::from).toList();
        return scope.keepOwnedClasses(all, ChapterOptionDto::schoolClassId).stream().sorted(ORDER).toList();
    }
}
