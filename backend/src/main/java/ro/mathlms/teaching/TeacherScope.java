package ro.mathlms.teaching;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which classes and tests the signed-in staff member may see and change. An ADMIN sees everything; a TEACHER only what
 * they own: their classes ({@code school_classes.teacher_id}), everything inside them (books, chapters, exercises,
 * enrolments, homework, class invite links), the students enrolled in them, and their tests ({@code quizzes.teacher_id})
 * with those tests' questions and attempts. Every miss is a {@link ContentNotFoundException} (404, same as a missing id):
 * a guessed id must not confirm that another teacher's class exists.
 *
 * <p>Called by every {@code /api/admin/**} endpoint a TEACHER can reach, before the service runs. Outside a request
 * (scheduled jobs, tests that call services directly) nobody is signed in and nothing is restricted.
 */
@Component
@Transactional(readOnly = true)
public class TeacherScope {

    private static final String ADMIN = "ROLE_ADMIN";
    private static final String TEACHER = "ROLE_TEACHER";

    private final EntityManager em;

    public TeacherScope(EntityManager em) {
        this.em = em;
    }

    /** True for a TEACHER who is not also an ADMIN: their view is limited to what they own. */
    public boolean restricted() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && has(auth, TEACHER) && !has(auth, ADMIN);
    }

    /** The signed-in account's id (the owner given to a new class or test), or null outside a request. */
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return em.createQuery("select u.id from User u where u.email = :email", Long.class)
                .setParameter("email", auth.getName()).getResultStream().findFirst().orElse(null);
    }

    // --- single rows: 404 unless the teacher owns it ---

    public void requireClass(Long classId) {
        requireOwner("SchoolClass", classId, "select c.teacherId from SchoolClass c where c.id = :id");
    }

    public void requireBook(Long bookId) {
        requireOwner("Book", bookId, "select b.schoolClass.teacherId from Book b where b.id = :id");
    }

    public void requireChapter(Long chapterId) {
        requireOwner("Chapter", chapterId, "select c.book.schoolClass.teacherId from Chapter c where c.id = :id");
    }

    public void requireExercise(Long exerciseId) {
        requireOwner("Exercise", exerciseId,
                "select e.chapter.book.schoolClass.teacherId from Exercise e where e.id = :id");
    }

    public void requireEnrollment(Long enrollmentId) {
        requireOwner("Enrollment", enrollmentId, "select e.schoolClass.teacherId from Enrollment e where e.id = :id");
    }

    public void requireAssignment(Long assignmentId) {
        requireOwner("Assignment", assignmentId, "select a.schoolClass.teacherId from Assignment a where a.id = :id");
    }

    public void requireQuiz(Long quizId) {
        requireOwner("Quiz", quizId, "select q.teacherId from Quiz q where q.id = :id");
    }

    public void requireQuizItem(Long itemId) {
        requireOwner("QuizItem", itemId, "select i.quiz.teacherId from QuizItem i where i.id = :id");
    }

    public void requireAttempt(Long attemptId) {
        requireOwner("QuizAttempt", attemptId, "select a.quiz.teacherId from QuizAttempt a where a.id = :id");
    }

    /** A student enrolled in at least one of the teacher's classes. */
    public void requireStudent(Long studentId) {
        if (restricted() && !ownsStudent(studentId)) {
            throw new ContentNotFoundException("User", studentId);
        }
    }

    /** A class link of one of the teacher's classes, or a parent link of one of their students. */
    public void requireInviteLink(Long linkId) {
        if (!restricted()) {
            return;
        }
        Object[] row = em.createQuery("select sc.id, st.id from InviteLink l"
                        + " left join l.schoolClass sc left join l.student st where l.id = :id", Object[].class)
                .setParameter("id", linkId).getResultStream().findFirst()
                .orElseThrow(() -> new ContentNotFoundException("InviteLink", linkId));
        boolean allowed = row[0] != null ? ownedClassIds().contains((Long) row[0])
                : row[1] != null && ownsStudent((Long) row[1]);
        if (!allowed) {
            throw new ContentNotFoundException("InviteLink", linkId);
        }
    }

    // --- lists: keep only what the teacher owns ---

    /** {@code items} unchanged for an ADMIN; for a TEACHER only those whose class they own. */
    public <T> List<T> keepOwnedClasses(List<T> items, Function<T, Long> classId) {
        if (!restricted()) {
            return items;
        }
        Set<Long> owned = ownedClassIds();
        return items.stream().filter(item -> owned.contains(classId.apply(item))).toList();
    }

    /** {@code items} unchanged for an ADMIN; for a TEACHER only those whose test they own. */
    public <T> List<T> keepOwnedQuizzes(List<T> items, Function<T, Long> quizId) {
        if (!restricted()) {
            return items;
        }
        Set<Long> owned = Set.copyOf(em.createQuery("select q.id from Quiz q where q.teacherId = :me", Long.class)
                .setParameter("me", currentUserId()).getResultList());
        return items.stream().filter(item -> owned.contains(quizId.apply(item))).toList();
    }

    /** {@code items} unchanged for an ADMIN; for a TEACHER only the students enrolled in one of their classes. */
    public <T> List<T> keepOwnedStudents(List<T> items, Function<T, Long> studentId) {
        if (!restricted()) {
            return items;
        }
        Set<Long> owned = Set.copyOf(em.createQuery("select e.student.id from Enrollment e"
                        + " where e.schoolClass.teacherId = :me", Long.class)
                .setParameter("me", currentUserId()).getResultList());
        return items.stream().filter(item -> owned.contains(studentId.apply(item))).toList();
    }

    // --- helpers ---

    private void requireOwner(String what, Long id, String ownerQuery) {
        if (!restricted()) {
            return;
        }
        // A missing row and someone else's row look the same (the owner query finds nothing, or another owner).
        List<Long> owner = em.createQuery(ownerQuery, Long.class).setParameter("id", id).getResultList();
        Long me = currentUserId();
        if (owner.isEmpty() || me == null || !Objects.equals(owner.getFirst(), me)) {
            throw new ContentNotFoundException(what, id);
        }
    }

    private Set<Long> ownedClassIds() {
        return em.createQuery("select c.id from SchoolClass c where c.teacherId = :me", Long.class)
                .setParameter("me", currentUserId()).getResultStream().collect(Collectors.toSet());
    }

    private boolean ownsStudent(Long studentId) {
        return em.createQuery("select count(e) from Enrollment e where e.student.id = :student"
                        + " and e.schoolClass.teacherId = :me", Long.class)
                .setParameter("student", studentId).setParameter("me", currentUserId()).getSingleResult() > 0;
    }

    private static boolean has(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
