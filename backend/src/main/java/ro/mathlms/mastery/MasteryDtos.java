package ro.mathlms.mastery;

import java.util.List;

/** What the mastery map (E6) sends to the screens. A {@code null} percent = no scored answer in that chapter yet. */
public final class MasteryDtos {

    private MasteryDtos() {
    }

    /** One chapter for one student (their own page, or their parent's). */
    public record ChapterMasteryDto(Long chapterId, String chapterTitle, String bookTitle, String schoolClassName,
                                    int earned, int possible, Integer percent, int questions) {
    }

    /** A column of the teacher's grid. */
    public record ChapterRefDto(Long id, String title, String bookTitle) {
    }

    /** One cell: a student in a chapter. */
    public record CellDto(Long chapterId, Integer percent, int questions) {
    }

    /** One row: a student, with one cell per chapter in the order of {@link ClassMasteryDto#chapters()}. */
    public record StudentRowDto(Long studentId, String fullName, List<CellDto> cells) {
    }

    /** The teacher's grid for one class: students × the class's chapters. */
    public record ClassMasteryDto(List<ChapterRefDto> chapters, List<StudentRowDto> students) {
    }
}
