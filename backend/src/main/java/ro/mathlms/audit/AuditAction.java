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
    /** EN chapter pages: the list, the theory photos and the worked examples on each page. */
    EN_CHAPTER_CREATED(AuditCategory.CONTENT),
    EN_CHAPTER_UPDATED(AuditCategory.CONTENT),
    EN_CHAPTER_DELETED(AuditCategory.CONTENT),
    EN_CHAPTER_PHOTO_ADDED(AuditCategory.CONTENT),
    EN_CHAPTER_PHOTO_REMOVED(AuditCategory.CONTENT),
    /** The written-out book pages of a chapter (V73) shown to students, or hidden again. */
    EN_CHAPTER_LESSON_SHOWN(AuditCategory.CONTENT),
    EN_CHAPTER_LESSON_HIDDEN(AuditCategory.CONTENT),
    /** A written-out page the transcription flagged ([?], a warning), read by the teacher (V77). */
    EN_CHAPTER_LESSON_PAGE_REVIEWED(AuditCategory.CONTENT),
    /** A chapter's new lesson (V78): the teacher corrected its text, or approved it for students. */
    EN_LESSON_EDITED(AuditCategory.CONTENT),
    EN_LESSON_APPROVED(AuditCategory.CONTENT),
    EN_EXAMPLE_ADDED(AuditCategory.CONTENT),
    EN_EXAMPLE_UPDATED(AuditCategory.CONTENT),
    EN_EXAMPLE_DELETED(AuditCategory.CONTENT),
    /** The automatic links: which EN chapter each place on the paper points at by default. */
    EN_CHAPTER_SLOTS_UPDATED(AuditCategory.CONTENT),
    /** The school's EN exam date (V84), which every student's study plan counts down to. */
    EN_EXAM_DATE_SET(AuditCategory.CONTENT),

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
    /** A test made of „Teme recapitulative” exercises (V82); the quiz's own QUIZ_CREATED row comes with it. */
    QUIZ_FROM_RECAP(AuditCategory.QUIZ),
    RECAP_REVIEWED(AuditCategory.CONTENT),
    RECAP_EDITED(AuditCategory.CONTENT),
    /** A question's right answer changed after the fact, every paper marked again. */
    ANSWER_KEY_CORRECTED(AuditCategory.QUIZ),

    ANSWER_GRADED(AuditCategory.GRADING),
    PAPER_COMMENTED(AuditCategory.GRADING),
    PAPER_GRADED(AuditCategory.GRADING),
    /** A final grade taken back to be corrected; finalising again writes a new PAPER_GRADED row. */
    PAPER_REOPENED(AuditCategory.GRADING),
    /** One more graded attempt at a test for one student ("+1 încercare"), the limit unchanged for the rest. */
    ATTEMPT_GRANTED(AuditCategory.GRADING),

    HOMEWORK_ASSIGNED(AuditCategory.HOMEWORK),
    HOMEWORK_RESCHEDULED(AuditCategory.HOMEWORK),
    HOMEWORK_DELETED(AuditCategory.HOMEWORK),
    /** One student given their own deadline or excused from a homework (or the exception changed). */
    HOMEWORK_OVERRIDE_SET(AuditCategory.HOMEWORK),
    /** One student back on the class deadline. */
    HOMEWORK_OVERRIDE_REMOVED(AuditCategory.HOMEWORK),
    /** "Trimite reminder": the students who had not handed in were emailed now (how many, never who). */
    HOMEWORK_REMINDER_SENT(AuditCategory.HOMEWORK),
    /** A chapter put first into one student's study plan (V84), or taken out again. */
    STUDY_PLAN_PINNED(AuditCategory.HOMEWORK),
    STUDY_PLAN_UNPINNED(AuditCategory.HOMEWORK),

    ACCOUNT_APPROVED(AuditCategory.PEOPLE),
    ACCOUNT_REJECTED(AuditCategory.PEOPLE),
    PARENT_LINKED(AuditCategory.PEOPLE),
    /** A student's extra time on timed tests set or taken away (CES). */
    EXTRA_TIME_CHANGED(AuditCategory.PEOPLE),
    STUDENT_ENROLLED(AuditCategory.PEOPLE),
    STUDENT_UNENROLLED(AuditCategory.PEOPLE),
    INVITE_LINK_CREATED(AuditCategory.PEOPLE),
    INVITE_LINK_REVOKED(AuditCategory.PEOPLE),
    /** A newcomer joined a class (or became a student's parent) through an invite link: no teacher step, so it is logged. */
    JOINED_BY_INVITE(AuditCategory.PEOPLE),
    /** An admin removed a student's account: anonymised like a GDPR erasure, the grades kept without a name. */
    STUDENT_REMOVED(AuditCategory.PEOPLE),

    LOGIN_FAILED(AuditCategory.SECURITY),
    LOGIN_CHALLENGE_STARTED(AuditCategory.SECURITY),
    LOGIN_SUCCEEDED(AuditCategory.SECURITY),
    TWO_FACTOR_FAILED(AuditCategory.SECURITY),
    TWO_FACTOR_SUCCEEDED(AuditCategory.SECURITY),
    TWO_FACTOR_ENABLED(AuditCategory.SECURITY),
    TWO_FACTOR_DISABLED(AuditCategory.SECURITY),
    TWO_FACTOR_RECOVERY_CODES_REGENERATED(AuditCategory.SECURITY),
    PASSWORD_RESET(AuditCategory.SECURITY),
    GDPR_EXPORT_REQUESTED(AuditCategory.SECURITY),
    TEACHER_SCOPE_DENIED(AuditCategory.SECURITY);

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
