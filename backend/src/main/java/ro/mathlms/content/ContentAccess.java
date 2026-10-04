package ro.mathlms.content;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Who may READ which part of the content tree (class → book → chapter → exercise).
 *
 * <p>A STUDENT only reads content of the classes they are enrolled in; ADMIN and PARENT are not
 * restricted here (a parent will be narrowed to their children's classes in Phase 5). Every miss is
 * a {@link ContentNotFoundException} (404, same as a missing id): a guessed id must not confirm that
 * another class's book or exercise exists. Writes are admin-only and never go through this check.
 */
@Component
public class ContentAccess {

    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final ExerciseRepository exerciseRepository;

    public ContentAccess(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                         BookRepository bookRepository, ChapterRepository chapterRepository,
                         ExerciseRepository exerciseRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public boolean isStudent(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> STUDENT_AUTHORITY.equals(authority.getAuthority()));
    }

    public void checkClass(Long classId, Authentication authentication) {
        if (isStudent(authentication)) {
            requireEnrolled(classId, authentication, "SchoolClass", classId);
        }
    }

    public void checkBook(Long bookId, Authentication authentication) {
        if (isStudent(authentication)) {
            Long classId = bookRepository.findClassIdById(bookId)
                    .orElseThrow(() -> new ContentNotFoundException("Book", bookId));
            requireEnrolled(classId, authentication, "Book", bookId);
        }
    }

    public void checkChapter(Long chapterId, Authentication authentication) {
        if (isStudent(authentication)) {
            Long classId = chapterRepository.findClassIdById(chapterId)
                    .orElseThrow(() -> new ContentNotFoundException("Chapter", chapterId));
            requireEnrolled(classId, authentication, "Chapter", chapterId);
        }
    }

    public void checkExercise(Long exerciseId, Authentication authentication) {
        if (isStudent(authentication)) {
            Long classId = exerciseRepository.findClassIdById(exerciseId)
                    .orElseThrow(() -> new ContentNotFoundException("Exercise", exerciseId));
            requireEnrolled(classId, authentication, "Exercise", exerciseId);
        }
    }

    /** Throws "No {what} with id {id}" — identical to a genuinely missing row — unless enrolled. */
    private void requireEnrolled(Long classId, Authentication authentication, String what, Long id) {
        User student = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ContentNotFoundException(what, id));
        if (!enrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(), classId)) {
            throw new ContentNotFoundException(what, id);
        }
    }
}
