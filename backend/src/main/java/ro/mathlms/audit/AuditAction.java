package ro.mathlms.audit;

import java.util.Arrays;
import java.util.List;

/**
 * What a journal row records. Stored by name ({@code VARCHAR(40)}), so renaming a constant orphans its old rows: add,
 * do not rename. The Romanian wording lives in the frontend.
 */
public enum AuditAction {
    CLASS_CREATED(AuditCategory.CONTENT),
    CLASS_UPDATED(AuditCategory.CONTENT),
    CLASS_DELETED(AuditCategory.CONTENT),
    BOOK_CREATED(AuditCategory.CONTENT),
    BOOK_UPDATED(AuditCategory.CONTENT),
    BOOK_DELETED(AuditCategory.CONTENT),
    CHAPTER_CREATED(AuditCategory.CONTENT),
    CHAPTER_UPDATED(AuditCategory.CONTENT),
    CHAPTER_DELETED(AuditCategory.CONTENT),
    EXERCISE_CREATED(AuditCategory.CONTENT),
    EXERCISE_UPDATED(AuditCategory.CONTENT),
    EXERCISE_DELETED(AuditCategory.CONTENT),

    QUIZ_CREATED(AuditCategory.QUIZ),
    QUIZ_UPDATED(AuditCategory.QUIZ),
    QUIZ_COPIED(AuditCategory.QUIZ),
    QUIZ_PUBLISHED(AuditCategory.QUIZ),
    QUIZ_UNPUBLISHED(AuditCategory.QUIZ),
    QUIZ_DELETED(AuditCategory.QUIZ),
    QUESTION_ADDED(AuditCategory.QUIZ),
    QUESTION_UPDATED(AuditCategory.QUIZ),
    QUESTION_DELETED(AuditCategory.QUIZ),
    QUESTION_IMAGE_SET(AuditCategory.QUIZ),
    QUESTION_IMAGE_REMOVED(AuditCategory.QUIZ),
    /** A draft quiz made from photographed textbook pages (Faza 3); the quiz's own QUIZ_CREATED row comes with it. */
    QUIZ_FROM_PHOTOS(AuditCategory.QUIZ),

    ANSWER_GRADED(AuditCategory.GRADING),
    PAPER_COMMENTED(AuditCategory.GRADING),
    PAPER_GRADED(AuditCategory.GRADING),

    HOMEWORK_ASSIGNED(AuditCategory.HOMEWORK),
    HOMEWORK_RESCHEDULED(AuditCategory.HOMEWORK),
    HOMEWORK_DELETED(AuditCategory.HOMEWORK),

    ACCOUNT_APPROVED(AuditCategory.PEOPLE),
    ACCOUNT_REJECTED(AuditCategory.PEOPLE),
    PARENT_LINKED(AuditCategory.PEOPLE),
    STUDENT_ENROLLED(AuditCategory.PEOPLE),
    STUDENT_UNENROLLED(AuditCategory.PEOPLE),
    INVITE_LINK_CREATED(AuditCategory.PEOPLE),
    INVITE_LINK_REVOKED(AuditCategory.PEOPLE),
    /** A newcomer joined a class (or became a student's parent) through an invite link: no teacher step, so it is logged. */
    JOINED_BY_INVITE(AuditCategory.PEOPLE);

    private final AuditCategory category;

    AuditAction(AuditCategory category) {
        this.category = category;
    }

    public AuditCategory category() {
        return category;
    }

    public static List<AuditAction> in(AuditCategory category) {
        return Arrays.stream(values()).filter(a -> a.category == category).toList();
    }
}
